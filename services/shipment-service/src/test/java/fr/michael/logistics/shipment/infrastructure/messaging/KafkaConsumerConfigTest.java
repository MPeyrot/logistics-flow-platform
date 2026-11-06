package fr.michael.logistics.shipment.infrastructure.messaging;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import org.junit.jupiter.api.Test;
import org.springframework.boot.convert.ApplicationConversionService;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.kafka.core.KafkaOperations;
import org.springframework.kafka.listener.DefaultErrorHandler;

/**
 * Les nouvelles tentatives dorment dans le thread du consommateur, sans appeler poll() : une attente qui, ajoutée au
 * traitement des messages du lot qui précèdent l'échec, dépasse max.poll.interval.ms ferait sortir le consommateur
 * du groupe (rééquilibrage). Démarrage refusé.
 */
class KafkaConsumerConfigTest {

    @SuppressWarnings("unchecked")
    private final ApplicationContextRunner context = new ApplicationContextRunner()
            // comme SpringApplication : conversion de "1s" en Duration
            .withInitializer(ctx -> ctx.getBeanFactory()
                    .setConversionService(ApplicationConversionService.getSharedInstance()))
            .withUserConfiguration(KafkaConsumerConfig.class)
            .withBean(KafkaOperations.class, () -> mock(KafkaOperations.class))
            .withPropertyValues(
                    "shipment.kafka.unknown-shipment.initial-interval=1s",
                    "shipment.kafka.unknown-shipment.multiplier=2",
                    "shipment.kafka.unknown-shipment.max-elapsed-time=5m");

    @Test
    void startsWhenEachWaitStaysUnderMaxPollInterval() {
        context.withPropertyValues(
                        "shipment.kafka.unknown-shipment.max-interval=60s",
                        "spring.kafka.consumer.properties.max.poll.interval.ms=300000")
                .run(started -> assertThat(started).hasSingleBean(DefaultErrorHandler.class));
    }

    @Test
    void startupFailsWhenAWaitReachesMaxPollInterval() {
        context.withPropertyValues(
                        "shipment.kafka.unknown-shipment.max-interval=5m",
                        "spring.kafka.consumer.properties.max.poll.interval.ms=300000")
                .run(started -> {
                    assertThat(started).hasFailed();
                    assertThat(started.getStartupFailure()).rootCause()
                            .hasMessageContaining("shipment.kafka.unknown-shipment.max-interval")
                            .hasMessageContaining("max.poll.interval.ms");
                });
    }

    @Test
    void startupFailsWhenAWaitLeavesNoMarginToProcessTheRestOfTheBatch() {
        context.withPropertyValues(
                        "shipment.kafka.unknown-shipment.max-interval=280s",
                        "spring.kafka.consumer.properties.max.poll.interval.ms=300000")
                .run(started -> {
                    assertThat(started).hasFailed();
                    assertThat(started.getStartupFailure()).rootCause()
                            .hasMessageContaining("shipment.kafka.batch-processing-margin");
                });
    }

    @Test
    void theMarginCanBeLoweredToAllowALongerWait() {
        context.withPropertyValues(
                        "shipment.kafka.unknown-shipment.max-interval=280s",
                        "shipment.kafka.batch-processing-margin=10s",
                        "spring.kafka.consumer.properties.max.poll.interval.ms=300000")
                .run(started -> assertThat(started).hasSingleBean(DefaultErrorHandler.class));
    }

    @Test
    void startupFailsWhenTheRetryIntervalOfOtherErrorsLeavesNoMargin() {
        context.withPropertyValues(
                        "shipment.kafka.unknown-shipment.max-interval=60s",
                        "shipment.kafka.retry-interval-ms=280000",
                        "spring.kafka.consumer.properties.max.poll.interval.ms=300000")
                .run(started -> {
                    assertThat(started).hasFailed();
                    assertThat(started.getStartupFailure()).rootCause()
                            .hasMessageContaining("shipment.kafka.retry-interval-ms")
                            .hasMessageContaining("max.poll.interval.ms");
                });
    }

    @Test
    void kafkaDefaultMaxPollIntervalAppliesWhenNotSet() {
        context.withPropertyValues("shipment.kafka.unknown-shipment.max-interval=6m")
                .run(started -> assertThat(started).hasFailed());
    }
}
