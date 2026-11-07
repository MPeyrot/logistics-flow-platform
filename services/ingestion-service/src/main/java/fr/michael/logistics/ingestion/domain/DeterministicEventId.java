package fr.michael.logistics.ingestion.domain;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.UUID;

/**
 * Identifiant d'un scan d'import qui n'en fournit pas : UUID par nom (type 3) sur
 * {@code shipmentReference|type|site|occurredAt}. Réimporter le même fichier redonne les mêmes
 * identifiants, que le consommateur dédoublonne.
 */
public final class DeterministicEventId {

    private DeterministicEventId() {
    }

    public static UUID of(String shipmentReference, ScanType type, String site, Instant occurredAt) {
        String name = shipmentReference + "|" + type.name() + "|" + site + "|" + occurredAt;
        return UUID.nameUUIDFromBytes(name.getBytes(StandardCharsets.UTF_8));
    }
}
