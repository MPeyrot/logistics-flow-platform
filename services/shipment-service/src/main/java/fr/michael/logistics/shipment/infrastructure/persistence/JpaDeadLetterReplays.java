package fr.michael.logistics.shipment.infrastructure.persistence;

import fr.michael.logistics.shipment.application.DeadLetterPosition;
import fr.michael.logistics.shipment.application.DeadLetterReplays;
import java.time.Instant;
import java.util.Collection;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.stereotype.Repository;

@Repository
class JpaDeadLetterReplays implements DeadLetterReplays {

    private final SpringDataDltReplayRepository jpa;

    JpaDeadLetterReplays(SpringDataDltReplayRepository jpa) {
        this.jpa = jpa;
    }

    @Override
    public Map<DeadLetterPosition, Instant> replayedAt(Collection<DeadLetterPosition> positions) {
        return jpa.findAllById(positions.stream()
                        .map(position -> new DltReplayJpaEntity.Key(position.partition(), position.offset()))
                        .toList())
                .stream()
                .collect(Collectors.toMap(
                        replay -> new DeadLetterPosition(replay.dltPartition(), replay.dltOffset()),
                        DltReplayJpaEntity::replayedAt));
    }

    @Override
    public boolean record(DeadLetterPosition position, Instant replayedAt, String replayedBy) {
        return jpa.insertIfAbsent(position.partition(), position.offset(), replayedAt, replayedBy) == 1;
    }
}
