package fr.michael.logistics.shipment.infrastructure.messaging;

import fr.michael.logistics.shipment.application.RecordScanService;
import fr.michael.logistics.shipment.domain.ScanEvent;
import jakarta.validation.Validator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

@Component
class ScanEventListener {

    static final String TOPIC = "scan-events";

    private static final Logger log = LoggerFactory.getLogger(ScanEventListener.class);

    private final RecordScanService service;
    private final JsonMapper json;
    private final Validator validator;

    ScanEventListener(RecordScanService service, JsonMapper json, Validator validator) {
        this.service = service;
        this.json = json;
        this.validator = validator;
    }

    @KafkaListener(topics = TOPIC)
    void onMessage(String payload) {
        ScanEvent event = parse(payload);
        service.record(event);
        log.info("Scan {} applied to shipment {}", event.eventId(), event.shipmentReference());
    }

    private ScanEvent parse(String payload) {
        try {
            ScanEventMessage message = json.readValue(payload, ScanEventMessage.class);
            if (!validator.validate(message).isEmpty()) {
                throw new IllegalArgumentException("Message breaks the scan-event.v2 constraints");
            }
            return message.toDomain();
        } catch (RuntimeException e) {
            throw new InvalidMessageException("Message does not match scan-event.v2", e);
        }
    }
}
