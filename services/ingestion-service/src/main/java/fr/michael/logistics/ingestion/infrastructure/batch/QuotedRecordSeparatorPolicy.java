package fr.michael.logistics.ingestion.infrastructure.batch;

import org.springframework.batch.infrastructure.item.file.separator.RecordSeparatorPolicy;

/**
 * Un enregistrement finit quand ses guillemets sont fermés (RFC 4180, {@code ""} = un guillemet littéral). Pas de
 * continuation par antislash, contrairement à {@code DefaultRecordSeparatorPolicy}.
 */
final class QuotedRecordSeparatorPolicy implements RecordSeparatorPolicy {

    /** Le lecteur rappelle avec le même début plus une ligne : seule la partie nouvelle est analysée. */
    private final CsvQuoteScanner scanner = new CsvQuoteScanner();
    private int scanned;

    @Override
    public boolean isEndOfRecord(String record) {
        if (record.length() < scanned) { // nouvel enregistrement
            scanner.reset();
            scanned = 0;
        }
        scanner.feed(record, scanned);
        scanned = record.length();
        if (!scanner.isOpen()) {
            scanner.reset();
            scanned = 0;
            return true;
        }
        return false;
    }

    @Override
    public String postProcess(String record) {
        return record;
    }

    /** Le lecteur recolle la ligne suivante sans séparateur : le saut de ligne du champ est rétabli ici. */
    @Override
    public String preProcess(String record) {
        return scanner.isOpen() ? record + "\n" : record;
    }
}
