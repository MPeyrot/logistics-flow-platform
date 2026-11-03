package fr.michael.logistics.shipment.application;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;

/**
 * Jeton de service de shipment-service vers ai-service ({@code sub=shipment-service}, {@code aud=ai-service}, 5 min),
 * gardé en cache et renouvelé un peu avant son expiration.
 */
public class ServiceTokenProvider {

    public static final String SUBJECT = "shipment-service";
    public static final Duration LIFETIME = Duration.ofMinutes(5);
    /** Un jeton qui expire dans moins que cela est renouvelé : il ne doit pas expirer pendant l'appel. */
    static final Duration RENEW_MARGIN = Duration.ofSeconds(30);

    private final String audience;
    private final TokenIssuer issuer;
    private final Clock clock;
    private String token;
    private Instant expiresAt;

    public ServiceTokenProvider(String audience, TokenIssuer issuer, Clock clock) {
        this.audience = audience;
        this.issuer = issuer;
        this.clock = clock;
    }

    public synchronized String token() {
        Instant now = clock.instant();
        if (token == null || !now.isBefore(expiresAt.minus(RENEW_MARGIN))) {
            expiresAt = now.plus(LIFETIME);
            token = issuer.sign(new TokenClaims(AuthenticationService.ISSUER, SUBJECT, audience, List.of(), null, now, expiresAt));
        }
        return token;
    }
}
