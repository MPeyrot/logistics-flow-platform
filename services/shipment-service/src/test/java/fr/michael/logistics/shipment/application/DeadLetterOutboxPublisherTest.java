package fr.michael.logistics.shipment.application;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
import java.util.UUID;
import java.util.function.Consumer;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionOperations;

/**
 * Publication de l'outbox des rejeux : chaque message est marqué publié après l'accusé du broker, dans l'ordre
 * d'écriture ; un échec de publication arrête le lot, la suite repart du message en échec.
 */
class DeadLetterOutboxPublisherTest {

    private static final Instant NOW = Instant.parse("2026-11-06T10:00:00Z");

    private final InMemoryOutbox outbox = new InMemoryOutbox();
    private final RecordingBroker broker = new RecordingBroker();
    private final TransactionOperations transactions = new TransactionOperations() {
        @Override
        public <T> T execute(TransactionCallback<T> action) {
            return action.doInTransaction(null);
        }
    };
    private final DeadLetterOutboxPublisher publisher = new DeadLetterOutboxPublisher(
            outbox, broker, transactions, Clock.fixed(NOW, ZoneOffset.UTC), 2);

    @Test
    void publishesEveryPendingMessageInWritingOrderThenHasNothingLeft() {
        outbox.add("REF-1", "a");
        outbox.add("REF-2", "b");
        outbox.add("REF-1", "c");

        assertThat(publisher.publishPending()).isEqualTo(3);

        assertThat(broker.published).containsExactly("a", "b", "c");
        assertThat(outbox.publishedAt.values()).containsOnly(NOW);
        assertThat(publisher.publishPending()).isZero();
        assertThat(broker.published).hasSize(3);
    }

    @Test
    void aBrokerFailureKeepsTheFailedMessageAndTheNextOnesForTheNextRun() {
        outbox.add("REF-1", "a");
        outbox.add("REF-1", "b");
        outbox.add("REF-1", "c");
        broker.failOnce("b");

        assertThat(publisher.publishPending()).isEqualTo(1);

        assertThat(broker.published).containsExactly("a");
        assertThat(publisher.publishPending()).isEqualTo(2);
        assertThat(broker.published).containsExactly("a", "b", "c");
    }

    @Test
    void publishesNothingWhileAnotherInstanceHoldsThePublisherLock() {
        outbox.add("REF-1", "a");
        outbox.lockedElsewhere = true;

        assertThat(publisher.publishPending()).isZero();

        assertThat(broker.published).isEmpty();
    }

    private static final class InMemoryOutbox implements DeadLetterOutbox {

        final Map<Long, OutboxMessage> messages = new TreeMap<>();
        final Map<Long, Instant> publishedAt = new TreeMap<>();
        boolean lockedElsewhere;

        void add(String key, String value) {
            long id = messages.size() + 1;
            messages.put(id, new OutboxMessage(id, key, value));
        }

        @Override
        public void add(DeadLetter letter, Instant createdAt, UUID jobId) {
            add(letter.key(), letter.value());
        }

        @Override
        public boolean lockPublisher() {
            return !lockedElsewhere;
        }

        @Override
        public List<OutboxMessage> pending(int limit) {
            return messages.values().stream()
                    .filter(message -> !publishedAt.containsKey(message.id()))
                    .limit(limit)
                    .toList();
        }

        @Override
        public void markPublished(long id, Instant at) {
            publishedAt.put(id, at);
        }
    }

    private static final class RecordingBroker implements ScanEventDeadLetters {

        final List<String> published = new ArrayList<>();
        private String failOnce;

        void failOnce(String value) {
            failOnce = value;
        }

        @Override
        public void republish(String key, String value) {
            if (value.equals(failOnce)) {
                failOnce = null;
                throw new IllegalStateException("Broker did not acknowledge");
            }
            published.add(value);
        }

        @Override
        public List<DeadLetter> latest(int limit) {
            throw new UnsupportedOperationException();
        }

        @Override
        public Optional<DeadLetter> find(DeadLetterPosition position) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void forEach(Consumer<DeadLetter> action) {
            throw new UnsupportedOperationException();
        }
    }
}
