package fr.michael.logistics.ingestion.infrastructure.messaging;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import fr.michael.logistics.ingestion.application.ScanPublicationException;
import fr.michael.logistics.ingestion.domain.ScanEvent;
import fr.michael.logistics.ingestion.domain.ScanType;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.apache.kafka.common.KafkaException;
import org.junit.jupiter.api.Test;
import org.springframework.kafka.core.KafkaTemplate;
import tools.jackson.databind.json.JsonMapper;

/** Le publieur sans broker : seules les erreurs de transport deviennent 503. */
class KafkaScanPublisherTest {

    private static final ScanEvent EVENT =
            new ScanEvent(UUID.randomUUID(), "REF-1", ScanType.PICKED_UP, "Nantes", Instant.parse("2026-11-07T08:00:00Z"));

    @Test
    @SuppressWarnings("unchecked")
    void aKafkaClientErrorIsAPublicationFailure() {
        KafkaTemplate<String, String> kafka = mock(KafkaTemplate.class);
        when(kafka.send(anyString(), anyString(), anyString())).thenThrow(new KafkaException("boom"));

        assertThatThrownBy(() -> new KafkaScanPublisher(kafka, JsonMapper.builder().build()).publishAll(List.of(EVENT)))
                .isInstanceOf(ScanPublicationException.class);
    }

    @Test
    @SuppressWarnings("unchecked")
    void aSerializationBugIsNotPresentedAsKafkaBeingUnavailable() {
        KafkaTemplate<String, String> kafka = mock(KafkaTemplate.class);
        JsonMapper broken = mock(JsonMapper.class);
        when(broken.writeValueAsString(any())).thenThrow(new IllegalStateException("bug"));

        assertThatThrownBy(() -> new KafkaScanPublisher(kafka, broken).publishAll(List.of(EVENT)))
                .isInstanceOf(IllegalStateException.class)
                .isNotInstanceOf(ScanPublicationException.class);
    }
}
