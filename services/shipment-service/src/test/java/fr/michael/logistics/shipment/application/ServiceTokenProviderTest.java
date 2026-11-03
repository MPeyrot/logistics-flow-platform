package fr.michael.logistics.shipment.application;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class ServiceTokenProviderTest {

    private final ExtractionRateLimiterTest.MutableClock clock = new ExtractionRateLimiterTest.MutableClock();
    private final List<TokenClaims> signed = new ArrayList<>();
    private final ServiceTokenProvider provider = new ServiceTokenProvider("ai-service", claims -> {
        signed.add(claims);
        return "token-" + signed.size();
    }, clock);

    @Test
    void signsServiceClaimsForFiveMinutes() {
        provider.token();

        TokenClaims claims = signed.getFirst();
        assertThat(claims.issuer()).isEqualTo("shipment-service");
        assertThat(claims.subject()).isEqualTo("shipment-service");
        assertThat(claims.audience()).isEqualTo("ai-service");
        assertThat(Duration.between(claims.issuedAt(), claims.expiresAt())).isEqualTo(Duration.ofMinutes(5));
    }

    @Test
    void reusesTheTokenUntilJustBeforeItExpires() {
        assertThat(provider.token()).isEqualTo("token-1");
        clock.advance(Duration.ofMinutes(4));
        assertThat(provider.token()).isEqualTo("token-1");
        assertThat(signed).hasSize(1);
    }

    @Test
    void renewsTheTokenBeforeItExpires() {
        provider.token();
        clock.advance(Duration.ofMinutes(4).plusSeconds(31));

        assertThat(provider.token()).isEqualTo("token-2");
    }
}
