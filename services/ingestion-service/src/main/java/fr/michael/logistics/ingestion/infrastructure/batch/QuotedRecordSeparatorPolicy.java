package fr.michael.logistics.ingestion.infrastructure.batch;

import org.springframework.batch.infrastructure.item.file.separator.RecordSeparatorPolicy;

/**
 * Un enregistrement finit quand ses guillemets sont fermés (RFC 4180, {@code ""} = un guillemet littéral). Pas de
 * continuation par antislash, contrairement à {@code DefaultRecordSeparatorPolicy}.
 */
final class QuotedRecordSeparatorPolicy implements RecordSeparatorPolicy {

    /** Le lecteur rappelle avec le même début plus une ligne : seule la partie nouvelle est analysée. */
    private boolean open;
    private int scanned;

    static boolean oddQuotes(CharSequence text) {
        boolean odd = false;
        for (int i = 0; i < text.length(); i++) {
            if (text.charAt(i) == '"') {
                odd = !odd;
            }
        }
        return odd;
    }

    @Override
    public boolean isEndOfRecord(String record) {
        if (record.length() < scanned) { // nouvel enregistrement
            open = false;
            scanned = 0;
        }
        open ^= oddQuotes(record.substring(scanned));
        scanned = open ? record.length() : 0;
        return !open;
    }

    @Override
    public String postProcess(String record) {
        return record;
    }

    /** Le lecteur recolle la ligne suivante sans séparateur : le saut de ligne du champ est rétabli ici. */
    @Override
    public String preProcess(String record) {
        return open ? record + "\n" : record;
    }
}
