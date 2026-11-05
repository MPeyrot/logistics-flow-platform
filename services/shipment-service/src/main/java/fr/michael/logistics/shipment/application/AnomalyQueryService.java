package fr.michael.logistics.shipment.application;

import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AnomalyQueryService {

    private final AnomalyRepository anomalies;

    public AnomalyQueryService(AnomalyRepository anomalies) {
        this.anomalies = anomalies;
    }

    /** @param open true : ouvertes ; false : closes ; null : toutes. Les plus récemment détectées d'abord. */
    @Transactional(readOnly = true)
    public List<AnomalyView> list(Boolean open) {
        return anomalies.findViews(open);
    }
}
