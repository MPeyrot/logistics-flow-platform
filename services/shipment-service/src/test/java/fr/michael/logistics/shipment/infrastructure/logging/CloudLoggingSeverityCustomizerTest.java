package fr.michael.logistics.shipment.infrastructure.logging;

import static org.assertj.core.api.Assertions.assertThat;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.classic.spi.LoggingEvent;
import org.junit.jupiter.api.Test;
import org.springframework.boot.json.JsonWriter;

/** Les niveaux de Logback prennent les noms de Cloud Logging (profil {@code gcp}). */
class CloudLoggingSeverityCustomizerTest {

    private final JsonWriter<ILoggingEvent> writer = JsonWriter.<ILoggingEvent>of(members -> {
        members.add("level", event -> event.getLevel().toString());
        members.add("message", ILoggingEvent::getMessage);
        new CloudLoggingSeverityCustomizer().customize(members);
    });

    private String json(Level level, String message) {
        LoggingEvent event = new LoggingEvent();
        event.setLevel(level);
        event.setMessage(message);
        return writer.writeToString(event);
    }

    @Test
    void warnDevientWarning() {
        assertThat(json(Level.WARN, "x")).contains("\"level\":\"WARNING\"");
    }

    @Test
    void traceDevientDebugEtInfoEtErrorRestent() {
        assertThat(json(Level.TRACE, "x")).contains("\"level\":\"DEBUG\"");
        assertThat(json(Level.INFO, "x")).contains("\"level\":\"INFO\"");
        assertThat(json(Level.ERROR, "x")).contains("\"level\":\"ERROR\"");
    }

    @Test
    void unMessageQuiSeLitWarnResteIntact() {
        assertThat(json(Level.INFO, "WARN")).contains("\"message\":\"WARN\"");
    }
}
