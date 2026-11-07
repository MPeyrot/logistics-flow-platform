package fr.michael.logistics.ingestion.infrastructure.batch;

import fr.michael.logistics.ingestion.application.CsvStructureException;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import org.springframework.batch.infrastructure.item.file.transform.DelimitedLineTokenizer;

/**
 * Fichier décodé (UTF-8 strict, BOM retiré) dont l'en-tête est vérifié. Tout est contrôlé ici, avant le job :
 * une erreur de structure ne doit laisser aucune publication derrière elle.
 */
record CsvFile(String text, List<String> columns) {

    static final List<String> KNOWN = List.of("eventId", "shipmentReference", "type", "site", "occurredAt", "reason");
    static final List<String> REQUIRED = List.of("shipmentReference", "type", "site", "occurredAt");

    static CsvFile parse(byte[] content) {
        String text = decode(content);
        if (text.startsWith("﻿")) {
            text = text.substring(1);
        }
        if (text.isBlank()) {
            throw new CsvStructureException("The file is empty");
        }
        String header = text.lines().findFirst().orElse("");
        if (header.isBlank()) {
            throw new CsvStructureException("The header line is missing");
        }
        List<String> columns = columns(header);
        Set<String> seen = new LinkedHashSet<>();
        for (String column : columns) {
            if (!KNOWN.contains(column)) {
                throw new CsvStructureException("Unknown column: " + column);
            }
            if (!seen.add(column)) {
                throw new CsvStructureException("Duplicate column: " + column);
            }
        }
        for (String required : REQUIRED) {
            if (!seen.contains(required)) {
                throw new CsvStructureException("Missing column: " + required);
            }
        }
        return new CsvFile(text, columns);
    }

    /** Lignes de données non vides (hors en-tête). */
    long dataLines() {
        return text.lines().skip(1).filter(line -> !line.isBlank()).count();
    }

    private static List<String> columns(String header) {
        try {
            DelimitedLineTokenizer tokenizer = new DelimitedLineTokenizer();
            return Arrays.stream(tokenizer.tokenize(header).getValues()).map(String::trim).toList();
        } catch (RuntimeException e) {
            throw new CsvStructureException("The header line is not valid CSV");
        }
    }

    private static String decode(byte[] content) {
        try {
            return StandardCharsets.UTF_8.newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                    .decode(ByteBuffer.wrap(content))
                    .toString();
        } catch (CharacterCodingException e) {
            throw new CsvStructureException("The file is not valid UTF-8");
        }
    }
}
