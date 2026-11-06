package fr.michael.logistics.shipment.infrastructure.persistence;

import fr.michael.logistics.shipment.application.ReplayAllJob;
import fr.michael.logistics.shipment.application.ReplayAllJobs;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
class JpaReplayAllJobs implements ReplayAllJobs {

    private static final int MAX_ERROR_LENGTH = 500;

    private final SpringDataDltReplayJobRepository jobs;
    private final SpringDataDltReplayOutboxRepository outbox;

    JpaReplayAllJobs(SpringDataDltReplayJobRepository jobs, SpringDataDltReplayOutboxRepository outbox) {
        this.jobs = jobs;
        this.outbox = outbox;
    }

    @Override
    @Transactional
    public void create(UUID id, String requestedBy, Instant requestedAt) {
        jobs.insert(id, requestedBy, requestedAt);
    }

    @Override
    @Transactional
    public void recordFailure(UUID id) {
        jobs.incrementFailed(id);
    }

    @Override
    @Transactional
    public void finish(UUID id, Instant finishedAt, String error) {
        jobs.finish(id, finishedAt,
                error == null || error.length() <= MAX_ERROR_LENGTH ? error : error.substring(0, MAX_ERROR_LENGTH));
    }

    /** Une transaction : le job et ses comptes sont lus dans le même état. */
    @Override
    @Transactional(readOnly = true)
    public Optional<ReplayAllJob> find(UUID id) {
        return jobs.findById(id).map(job -> {
            SpringDataDltReplayOutboxRepository.Counts counts = outbox.countByJob(id);
            return new ReplayAllJob(job.id(), job.requestedBy(), job.requestedAt(), job.finishedAt(),
                    counts.getReplayed(), counts.getPublished(), job.failed(), job.error());
        });
    }
}
