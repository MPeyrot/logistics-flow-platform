package fr.michael.logistics.shipment.application;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Executor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.transaction.support.TransactionOperations;

/**
 * Outil ADMIN : lecture et rejeu de {@code scan-events.DLT}. Un rejeu est enregistré ({@code dlt_replay}) et mis
 * dans l'outbox dans la même transaction ; {@link DeadLetterOutboxPublisher} le publie ensuite. Un message rejoué
 * qui échoue encore revient dans le DLT sous un nouvel offset, non rejoué ; l'idempotence par {@code eventId} évite
 * un double traitement.
 */
public class DeadLetterService {

    public static final int MAX_LIMIT = 500;

    private static final Logger log = LoggerFactory.getLogger(DeadLetterService.class);

    private final ScanEventDeadLetters deadLetters;
    private final DeadLetterReplays replays;
    private final DeadLetterOutbox outbox;
    private final ReplayAllJobs jobs;
    private final TransactionOperations transactions;
    private final Clock clock;
    private final Executor background;

    /** {@code background} : exécute les « tout rejouer », hors de la requête HTTP. */
    public DeadLetterService(ScanEventDeadLetters deadLetters, DeadLetterReplays replays, DeadLetterOutbox outbox,
            ReplayAllJobs jobs, TransactionOperations transactions, Clock clock, Executor background) {
        this.deadLetters = deadLetters;
        this.replays = replays;
        this.outbox = outbox;
        this.jobs = jobs;
        this.transactions = transactions;
        this.clock = clock;
        this.background = background;
    }

    /** Les {@code limit} messages les plus récents (1 à 500), avec leur rejeu éventuel. */
    public List<DeadLetterEntry> list(int limit) {
        if (limit < 1 || limit > MAX_LIMIT) {
            throw new IllegalArgumentException("limit must be between 1 and " + MAX_LIMIT);
        }
        List<DeadLetter> letters = deadLetters.latest(limit);
        Map<DeadLetterPosition, Instant> replayed = replays.replayedAt(
                letters.stream().map(DeadLetter::position).toList());
        return letters.stream()
                .map(letter -> new DeadLetterEntry(letter, replayed.get(letter.position())))
                .toList();
    }

    /** Rejoue un message (publié peu après, par l'outbox) ; 404 s'il n'existe pas, 409 s'il est déjà rejoué. */
    public DeadLetterEntry replay(DeadLetterPosition position, String replayedBy) {
        DeadLetter letter = deadLetters.find(position).orElseThrow(() -> new DeadLetterNotFoundException(position));
        Instant now = clock.instant();
        if (!replay(letter, now, replayedBy, null)) {
            throw new DeadLetterAlreadyReplayedException(position);
        }
        return new DeadLetterEntry(letter, now);
    }

    /**
     * Lance le rejeu de chaque message pas encore rejoué, en tâche de fond, et rend aussitôt le rejeu lancé
     * (avancement : {@link #replayAllJob}).
     */
    public ReplayAllJob replayAll(String requestedBy) {
        ReplayAllJob job = ReplayAllJob.started(UUID.randomUUID(), requestedBy, clock.instant());
        jobs.create(job.id(), job.requestedBy(), job.requestedAt());
        try {
            background.execute(() -> run(job.id(), requestedBy));
        } catch (RuntimeException e) {
            jobs.finish(job.id(), clock.instant(), "Not started: " + e.getMessage());
            throw e;
        }
        return job;
    }

    /** 404 si aucun rejeu n'a cet identifiant. */
    public ReplayAllJob replayAllJob(UUID id) {
        return jobs.find(id).orElseThrow(() -> new ReplayAllJobNotFoundException(id));
    }

    /**
     * Parcourt le DLT. Un message dont l'enregistrement échoue est compté en échec, le parcours continue ; un échec
     * de lecture du DLT l'arrête. Un arrêt de l'instance en cours de route laisse le rejeu non terminé : relancer
     * « tout rejouer » reprend sans doublon.
     */
    private void run(UUID jobId, String requestedBy) {
        String error = null;
        try {
            deadLetters.forEach(letter -> {
                try {
                    replay(letter, clock.instant(), requestedBy, jobId);
                } catch (RuntimeException e) {
                    log.warn("Rejeu {} : message {} non enregistré : {}", jobId, letter.position(), e.getMessage());
                    jobs.recordFailure(jobId);
                }
            });
        } catch (RuntimeException e) {
            log.error("Rejeu {} interrompu", jobId, e);
            error = e.getMessage() == null ? e.getClass().getName() : e.getMessage();
        }
        jobs.finish(jobId, clock.instant(), error);
    }

    /**
     * Le rejeu est enregistré et mis dans l'outbox dans la même transaction : un rejeu concurrent du même message
     * attend puis s'arrête. {@code false} : déjà rejoué.
     */
    private boolean replay(DeadLetter letter, Instant now, String replayedBy, UUID jobId) {
        return Boolean.TRUE.equals(transactions.execute(status -> {
            if (!replays.record(letter.position(), now, replayedBy)) {
                return false;
            }
            outbox.add(letter, now, jobId);
            return true;
        }));
    }
}
