package fr.michael.logistics.shipment.application;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionOperations;

/**
 * Outil ADMIN : lecture et rejeu de {@code scan-events.DLT}. Un message rejoué qui échoue encore revient dans
 * le DLT sous un nouvel offset, non rejoué ; l'idempotence par {@code eventId} évite un double traitement.
 */
@Service
public class DeadLetterService {

    public static final int MAX_LIMIT = 500;

    private final ScanEventDeadLetters deadLetters;
    private final DeadLetterReplays replays;
    private final TransactionOperations transactions;
    private final Clock clock;

    public DeadLetterService(ScanEventDeadLetters deadLetters, DeadLetterReplays replays,
            TransactionOperations transactions, Clock clock) {
        this.deadLetters = deadLetters;
        this.replays = replays;
        this.transactions = transactions;
        this.clock = clock;
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

    /** Rejoue un message ; 404 s'il n'existe pas, 409 s'il est déjà rejoué. */
    public DeadLetterEntry replay(DeadLetterPosition position, String replayedBy) {
        DeadLetter letter = deadLetters.find(position).orElseThrow(() -> new DeadLetterNotFoundException(position));
        Instant now = clock.instant();
        if (!replay(letter, now, replayedBy)) {
            throw new DeadLetterAlreadyReplayedException(position);
        }
        return new DeadLetterEntry(letter, now);
    }

    /** Rejoue chaque message pas encore rejoué ; rend leur nombre. */
    public int replayAll(String replayedBy) {
        AtomicInteger count = new AtomicInteger();
        deadLetters.forEach(letter -> {
            if (replay(letter, clock.instant(), replayedBy)) {
                count.incrementAndGet();
            }
        });
        return count.get();
    }

    /**
     * Le rejeu est enregistré avant d'être publié, dans la même transaction : un rejeu concurrent du même message
     * attend puis s'arrête, et un échec de publication annule l'enregistrement. {@code false} : déjà rejoué.
     */
    private boolean replay(DeadLetter letter, Instant now, String replayedBy) {
        return Boolean.TRUE.equals(transactions.execute(status -> {
            if (!replays.record(letter.position(), now, replayedBy)) {
                return false;
            }
            deadLetters.republish(letter);
            return true;
        }));
    }
}
