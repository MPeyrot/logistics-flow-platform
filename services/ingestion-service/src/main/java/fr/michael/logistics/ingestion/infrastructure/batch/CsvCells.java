package fr.michael.logistics.ingestion.infrastructure.batch;

import java.util.ArrayList;
import java.util.List;

/**
 * Découpe un enregistrement CSV (virgule, guillemets RFC 4180). Les blancs autour d'une cellule hors guillemets sont
 * rognés ; ceux d'une cellule entre guillemets sont gardés. Un guillemet au milieu d'une cellule non guillemetée reste
 * un caractère.
 */
final class CsvCells {

    private CsvCells() {
    }

    static List<String> split(String record) {
        List<String> cells = new ArrayList<>();
        int i = 0;
        while (true) {
            int start = i;
            while (start < record.length() && record.charAt(start) == ' ') {
                start++;
            }
            if (start < record.length() && record.charAt(start) == '"') {
                StringBuilder cell = new StringBuilder();
                i = start + 1;
                while (i < record.length()) {
                    char c = record.charAt(i);
                    if (c == '"' && i + 1 < record.length() && record.charAt(i + 1) == '"') {
                        cell.append('"');
                        i += 2;
                    } else if (c == '"') {
                        i++;
                        break;
                    } else {
                        cell.append(c);
                        i++;
                    }
                }
                // ce qui suit le guillemet fermant, jusqu'à la virgule, est ajouté tel quel (rogné)
                int end = record.indexOf(',', i);
                String rest = record.substring(i, end < 0 ? record.length() : end).strip();
                cells.add(cell + rest);
                i = end;
            } else {
                int end = record.indexOf(',', start);
                cells.add(record.substring(start, end < 0 ? record.length() : end).strip());
                i = end;
            }
            if (i < 0) {
                return cells;
            }
            i++;
        }
    }
}
