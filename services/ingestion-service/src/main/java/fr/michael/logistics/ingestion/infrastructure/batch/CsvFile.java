package fr.michael.logistics.ingestion.infrastructure.batch;

import fr.michael.logistics.ingestion.application.CsvStructureException;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Set;

/**
 * Fichier décodé (UTF-8 strict, BOM retiré) dont l'en-tête est vérifié. Tout est contrôlé ici, avant le job :
 * une erreur de structure ne doit laisser aucune publication derrière elle. Le fichier est découpé en
 * enregistrements une fois pour les contrôles ({@code dataLines}), une fois par le lecteur du job, sans copie.
 *
 * @param dataLines enregistrements de données non vides (hors en-tête) : un champ entre guillemets sur plusieurs
 *     lignes compte un
 */
record CsvFile(String text, List<String> columns, long dataLines) {

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
        Records records = new Records(text);
        String header = records.next().text();
        if (header.isBlank()) {
            throw new CsvStructureException("The header line is missing");
        }
        List<String> columns = CsvCells.split(header);
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
        long dataLines = 0;
        while (records.hasNext()) {
            records.next(); // vérifie aussi qu'aucun guillemet ne reste ouvert
            dataLines++;
        }
        return new CsvFile(text, columns, dataLines);
    }

    /** Enregistrements de données non vides, dans l'ordre du fichier. */
    Iterator<Record> dataRecords() {
        Records records = new Records(text);
        records.next(); // l'en-tête
        return records;
    }

    /** Un enregistrement et le numéro de sa première ligne physique (en-tête = 1). */
    record Record(int line, String text) {
    }

    /**
     * Enregistrements RFC 4180 d'un texte, lus un à un : lignes physiques recollées (par {@code \n}) tant qu'un
     * guillemet reste ouvert. Les enregistrements vides sont sautés, sauf l'en-tête (le premier). Un guillemet
     * jamais fermé lève {@link CsvStructureException}.
     */
    private static final class Records implements Iterator<Record> {

        private final String text;
        private final CsvQuoteScanner scanner = new CsvQuoteScanner();
        private int position;
        private int line = 1;
        private boolean first = true;
        private Record next;

        Records(String text) {
            this.text = text;
        }

        @Override
        public boolean hasNext() {
            while (next == null && position < text.length()) {
                Record record = read();
                if (first || !record.text().isBlank()) {
                    first = false;
                    next = record;
                }
            }
            return next != null;
        }

        @Override
        public Record next() {
            if (!hasNext()) {
                throw new NoSuchElementException();
            }
            Record record = next;
            next = null;
            return record;
        }

        private Record read() {
            int firstLine = line;
            StringBuilder record = new StringBuilder();
            scanner.reset();
            while (true) {
                int end = position;
                while (end < text.length() && text.charAt(end) != '\n' && text.charAt(end) != '\r') {
                    end++;
                }
                for (int i = position; i < end; i++) {
                    scanner.feed(text.charAt(i));
                }
                scanner.feed('\n');
                record.append(text, position, end);
                position = end;
                if (position < text.length()) {
                    position += text.startsWith("\r\n", position) ? 2 : 1;
                }
                line++;
                if (!scanner.isOpen()) {
                    return new Record(firstLine, record.toString());
                }
                if (position >= text.length()) {
                    throw new CsvStructureException("A quoted field is not closed");
                }
                record.append('\n');
            }
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
