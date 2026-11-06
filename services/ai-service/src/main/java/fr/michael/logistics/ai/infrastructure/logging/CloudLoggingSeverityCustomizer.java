package fr.michael.logistics.ai.infrastructure.logging;

import ch.qos.logback.classic.spi.ILoggingEvent;
import java.util.Map;
import org.springframework.boot.json.JsonWriter;
import org.springframework.boot.json.JsonWriter.ValueProcessor;
import org.springframework.boot.logging.structured.StructuredLoggingJsonMembersCustomizer;

/**
 * Profil {@code gcp} : Cloud Logging attend ses propres noms de niveau dans {@code severity} ({@code WARNING},
 * {@code DEBUG}, {@code CRITICAL}) ; Logback écrit {@code WARN}, {@code TRACE}, {@code FATAL}. Les autres
 * ({@code INFO}, {@code ERROR}) sont identiques. Branché par {@code logging.structured.json.customizer}.
 */
public class CloudLoggingSeverityCustomizer implements StructuredLoggingJsonMembersCustomizer<ILoggingEvent> {

    private static final Map<String, String> SEVERITIES = Map.of("WARN", "WARNING", "TRACE", "DEBUG", "FATAL", "CRITICAL");

    @Override
    public void customize(JsonWriter.Members<ILoggingEvent> members) {
        members.applyingValueProcessor(ValueProcessor.<Object>of(value -> SEVERITIES.getOrDefault(String.valueOf(value), String.valueOf(value)))
                .whenHasPath("level"));
    }
}
