package fr.michael.logistics.shipment.infrastructure.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import fr.michael.logistics.shipment.TestcontainersConfiguration;
import fr.michael.logistics.shipment.application.DeadLetterOutbox;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.transaction.support.TransactionOperations;

/** Un seul publieur de l'outbox à la fois, quelle que soit l'instance : le verrou suit la transaction. */
@SpringBootTest(properties = "shipment.kafka.dlt.outbox.interval=1s")
@Import(TestcontainersConfiguration.class)
class DeadLetterOutboxLockTest {

    @Autowired
    DeadLetterOutbox outbox;

    @Autowired
    TransactionOperations transactions;

    /** Réessayé : la publication planifiée de ce contexte prend le même verrou une fois par seconde. */
    @Test
    void thePublisherLockIsHeldUntilTheEndOfItsTransaction() {
        await().atMost(Duration.ofSeconds(10)).untilAsserted(() -> {
            Boolean otherWhileHeld = transactions.execute(status -> {
                assertThat(outbox.lockPublisher()).isTrue();
                return CompletableFuture.supplyAsync(this::lockInOtherTransaction).join();
            });

            assertThat(otherWhileHeld).isFalse();
            assertThat(lockInOtherTransaction()).isTrue();
        });
    }

    private Boolean lockInOtherTransaction() {
        return transactions.execute(status -> outbox.lockPublisher());
    }
}
