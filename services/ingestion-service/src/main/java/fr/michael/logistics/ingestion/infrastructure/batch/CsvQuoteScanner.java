package fr.michael.logistics.ingestion.infrastructure.batch;

/**
 * Suit, caractère par caractère, si un guillemet reste ouvert. Même règle que {@link CsvCells} (RFC 4180) : seul un
 * guillemet qui ouvre une cellule (après les blancs que {@code strip()} rogne) commence un champ entre guillemets ;
 * {@code ""} y est un guillemet littéral ; un guillemet ailleurs (au milieu d'une cellule non guillemetée, ou après
 * le guillemet fermant) est un caractère ordinaire. L'état se garde d'une ligne à l'autre : rien n'est recompté.
 */
final class CsvQuoteScanner {

    private enum State { CELL_START, UNQUOTED, QUOTED, QUOTE_IN_QUOTED, AFTER_QUOTED }

    private State state = State.CELL_START;

    void feed(CharSequence text, int from) {
        for (int i = from; i < text.length(); i++) {
            feed(text.charAt(i));
        }
    }

    void feed(char c) {
        switch (state) {
            case CELL_START -> {
                if (c == '"') {
                    state = State.QUOTED;
                } else if (c != ',' && !Character.isWhitespace(c)) {
                    state = State.UNQUOTED;
                }
            }
            case UNQUOTED, AFTER_QUOTED -> {
                if (c == ',') {
                    state = State.CELL_START;
                }
            }
            case QUOTED -> {
                if (c == '"') {
                    state = State.QUOTE_IN_QUOTED;
                }
            }
            case QUOTE_IN_QUOTED -> {
                if (c == '"') {
                    state = State.QUOTED;
                } else {
                    state = c == ',' ? State.CELL_START : State.AFTER_QUOTED;
                }
            }
        }
    }

    boolean isOpen() {
        return state == State.QUOTED;
    }

    void reset() {
        state = State.CELL_START;
    }
}
