package fr.michael.logistics.shipment.infrastructure.scheduling;

import static org.assertj.core.api.Assertions.assertThat;

import fr.michael.logistics.shipment.TestcontainersConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Import;

/** Les tests coupent la tâche de l'outbox : sans cela, chaque contexte en cache publierait sur la base commune. */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
class DeadLetterOutboxTaskSwitchTest {

    @Autowired
    ApplicationContext context;

    @Test
    void theOutboxTaskIsOffByDefaultInTests() {
        assertThat(context.containsBean("deadLetterOutboxTask")).isFalse();
    }
}
