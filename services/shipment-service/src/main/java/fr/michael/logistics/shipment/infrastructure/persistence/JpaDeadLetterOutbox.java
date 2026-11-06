package fr.michael.logistics.shipment.infrastructure.persistence;

import fr.michael.logistics.shipment.application.DeadLetter;
import fr.michael.logistics.shipment.application.DeadLetterOutbox;
import fr.michael.logistics.shipment.application.OutboxMessage;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** Toujours dans la transaction de l'appelant : le verrou du publieur et l'écriture avec dlt_replay en dépendent. */
@Repository
@Transactional(propagation = Propagation.MANDATORY)
class JpaDeadLetterOutbox implements DeadLetterOutbox {

    private final SpringDataDltReplayOutboxRepository jpa;

    JpaDeadLetterOutbox(SpringDataDltReplayOutboxRepository jpa) {
        this.jpa = jpa;
    }

    @Override
    public void add(DeadLetter letter, Instant createdAt, UUID jobId) {
        jpa.insert(letter.partition(), letter.offset(), letter.key(), letter.value(), createdAt, jobId);
    }

    @Override
    public boolean lockPublisher() {
        return jpa.tryLock(SpringDataDltReplayOutboxRepository.PUBLISHER_LOCK);
    }

    @Override
    public List<OutboxMessage> pending(int limit) {
        return jpa.findPending(limit).stream()
                .map(row -> new OutboxMessage(row.id(), row.messageKey(), row.messageValue()))
                .toList();
    }

    @Override
    public void markPublished(long id, Instant publishedAt) {
        jpa.markPublished(id, publishedAt);
    }
}
