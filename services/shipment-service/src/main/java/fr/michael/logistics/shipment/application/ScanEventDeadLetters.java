package fr.michael.logistics.shipment.application;

import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;

/** Port de sortie : le topic {@code scan-events.DLT}, lu sans groupe de consommateurs (aucun offset commité). */
public interface ScanEventDeadLetters {

    /** Les {@code limit} messages les plus récents, du plus récent au plus ancien. DLT absent : liste vide. */
    List<DeadLetter> latest(int limit);

    /** Vide si la partition ou l'offset n'existe pas (ou plus : rétention du topic). */
    Optional<DeadLetter> find(DeadLetterPosition position);

    /**
     * Chaque message présent au moment de l'appel, partition par partition, dans l'ordre des offsets (l'ordre
     * par expédition est gardé : une même clé va dans une même partition). Les messages écrits pendant le
     * parcours, dont ceux qui échouent de nouveau après un rejeu, sont laissés de côté.
     */
    void forEach(Consumer<DeadLetter> action);

    /** Republie clé et valeur vers {@code scan-events}, sans les en-têtes du DLT, et attend l'accusé du broker. */
    void republish(String key, String value);
}
