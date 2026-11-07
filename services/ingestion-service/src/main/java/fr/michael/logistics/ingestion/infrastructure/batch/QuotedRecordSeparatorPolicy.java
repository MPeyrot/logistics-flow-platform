package fr.michael.logistics.ingestion.infrastructure.batch;

import org.springframework.batch.infrastructure.item.file.separator.RecordSeparatorPolicy;

/**
 * Un enregistrement finit quand ses guillemets sont fermés (RFC 4180, {@code ""} = un guillemet littéral). Pas de
 * continuation par antislash, contrairement à {@code DefaultRecordSeparatorPolicy}.
 */
final class QuotedRecordSeparatorPolicy implements RecordSeparatorPolicy {

    static boolean isComplete(String record) {
        return record.chars().filter(c -> c == '"').count() % 2 == 0;
    }

    @Override
    public boolean isEndOfRecord(String record) {
        return isComplete(record);
    }

    @Override
    public String postProcess(String record) {
        return record;
    }

    /** Le lecteur recolle la ligne suivante sans séparateur : le saut de ligne du champ est rétabli ici. */
    @Override
    public String preProcess(String record) {
        return isComplete(record) ? record : record + "\n";
    }
}
