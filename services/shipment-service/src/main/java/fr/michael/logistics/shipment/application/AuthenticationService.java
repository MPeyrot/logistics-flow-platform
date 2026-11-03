package fr.michael.logistics.shipment.application;

import fr.michael.logistics.shipment.domain.Role;
import fr.michael.logistics.shipment.domain.UserAccount;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Émet un jeton d'accès contre un identifiant et un mot de passe. */
@Service
public class AuthenticationService {

    public static final String ISSUER = "shipment-service";
    public static final String SHIPMENT_AUDIENCE = "shipment-service";
    public static final String INGESTION_AUDIENCE = "ingestion-service";

    private static final Duration USER_TOKEN_LIFETIME = Duration.ofHours(1);
    private static final Duration SCANNER_TOKEN_LIFETIME = Duration.ofHours(12);

    private final UserAccountRepository repository;
    private final PasswordHasher passwordHasher;
    private final TokenIssuer tokenIssuer;
    private final Clock clock;
    /** Comparé quand l'identifiant est inconnu, pour que la réponse prenne le même temps. */
    private final String unknownUserHash;

    public AuthenticationService(UserAccountRepository repository, PasswordHasher passwordHasher,
            TokenIssuer tokenIssuer, Clock clock) {
        this.repository = repository;
        this.passwordHasher = passwordHasher;
        this.tokenIssuer = tokenIssuer;
        this.clock = clock;
        this.unknownUserHash = passwordHasher.hash("unknown-user-placeholder");
    }

    @Transactional(readOnly = true)
    public AccessToken issueToken(String username, String rawPassword) {
        Optional<UserAccount> account = repository.findByUsername(username);
        boolean passwordMatches = passwordHasher.matches(rawPassword,
                account.map(UserAccount::passwordHash).orElse(unknownUserHash));
        if (account.isEmpty() || !passwordMatches || !account.get().active()) {
            throw new InvalidCredentialsException();
        }
        TokenClaims claims = claimsFor(account.get(), clock.instant());
        return new AccessToken(tokenIssuer.sign(claims), claims.expiresAt());
    }

    /** Un SCANNER ne parle qu'à ingestion-service ; ADMIN et OPERATOR à shipment-service. */
    private static TokenClaims claimsFor(UserAccount account, Instant now) {
        boolean scanner = account.role() == Role.SCANNER;
        String audience = scanner ? INGESTION_AUDIENCE : SHIPMENT_AUDIENCE;
        Duration lifetime = scanner ? SCANNER_TOKEN_LIFETIME : USER_TOKEN_LIFETIME;
        return new TokenClaims(ISSUER, account.username(), audience, List.of(account.role().name()),
                account.site(), now, now.plus(lifetime));
    }
}
