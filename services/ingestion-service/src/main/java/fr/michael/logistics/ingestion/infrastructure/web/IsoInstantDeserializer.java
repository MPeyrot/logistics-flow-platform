package fr.michael.logistics.ingestion.infrastructure.web;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.format.DateTimeParseException;
import tools.jackson.core.JsonParser;
import tools.jackson.core.JsonToken;
import tools.jackson.databind.DeserializationContext;
import tools.jackson.databind.deser.std.StdDeserializer;

/** Une date est un texte ISO-8601 ; un nombre (secondes depuis 1970) est refusé. */
final class IsoInstantDeserializer extends StdDeserializer<Instant> {

    IsoInstantDeserializer() {
        super(Instant.class);
    }

    @Override
    public Instant deserialize(JsonParser parser, DeserializationContext context) {
        if (parser.currentToken() != JsonToken.VALUE_STRING) {
            return (Instant) context.handleUnexpectedToken(Instant.class, parser);
        }
        String text = parser.getString();
        try {
            return OffsetDateTime.parse(text).toInstant();
        } catch (DateTimeParseException e) {
            return (Instant) context.handleWeirdStringValue(Instant.class, text, "not an ISO-8601 date-time");
        }
    }
}
