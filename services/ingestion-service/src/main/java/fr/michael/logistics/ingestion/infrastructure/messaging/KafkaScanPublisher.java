package fr.michael.logistics.ingestion.infrastructure.messaging;

import fr.michael.logistics.ingestion.application.ScanPublicationException;
import fr.michael.logistics.ingestion.application.ScanPublisher;
import fr.michael.logistics.ingestion.domain.ScanEvent;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.apache.kafka.common.KafkaException;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

@Component
class KafkaScanPublisher implements ScanPublisher {

    private static final long ACK_TIMEOUT_SECONDS = 5;

    private final KafkaTemplate<String, String> kafka;
    private final JsonMapper json;

    KafkaScanPublisher(KafkaTemplate<String, String> kafka, JsonMapper json) {
        this.kafka = kafka;
        this.json = json;
    }

    @Override
    public void publish(ScanEvent event) {
        String payload = json.writeValueAsString(ScanEventMessage.from(event));
        try {
            kafka.send(KafkaTopicsConfig.SCAN_EVENTS, event.shipmentReference(), payload)
                    .get(ACK_TIMEOUT_SECONDS, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new ScanPublicationException("Interrupted while publishing scan " + event.eventId(), e);
        } catch (ExecutionException | TimeoutException | KafkaException | org.springframework.kafka.KafkaException e) {
            throw new ScanPublicationException("Could not publish scan " + event.eventId(), e);
        }
    }

    @Override
    public void publishAll(List<ScanEvent> events) {
        List<CompletableFuture<SendResult<String, String>>> acks = new ArrayList<>(events.size());
        try {
            for (ScanEvent event : events) {
                String payload = json.writeValueAsString(ScanEventMessage.from(event));
                acks.add(kafka.send(KafkaTopicsConfig.SCAN_EVENTS, event.shipmentReference(), payload));
            }
            kafka.flush();
            for (CompletableFuture<SendResult<String, String>> ack : acks) {
                ack.get(ACK_TIMEOUT_SECONDS, TimeUnit.SECONDS);
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new ScanPublicationException("Interrupted while publishing " + events.size() + " scans", e);
        } catch (ExecutionException | TimeoutException | KafkaException | org.springframework.kafka.KafkaException e) {
            throw new ScanPublicationException("Could not publish " + events.size() + " scans", e);
        }
    }
}
