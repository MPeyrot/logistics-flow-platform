package fr.michael.logistics.ai.infrastructure.metrics;

import static org.assertj.core.api.Assertions.assertThat;

import fr.michael.logistics.ai.domain.TokenUsage;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

class MicrometerExtractionMetricsTest {

    private final SimpleMeterRegistry registry = new SimpleMeterRegistry();
    private final MicrometerExtractionMetrics metrics = new MicrometerExtractionMetrics(registry, "claude-haiku-5-5");

    @Test
    void accumulatesCostPerOutcomeAndTokensPerType() {
        metrics.recordBilledCall(new TokenUsage(1000, 100, 0, 0), new BigDecimal("0.000150"), true);
        metrics.recordBilledCall(new TokenUsage(2000, 200, 30, 40), new BigDecimal("0.000300"), true);
        metrics.recordBilledCall(new TokenUsage(500, 50, 0, 0), new BigDecimal("0.000075"), false);

        var extracted = registry.get("ai.claude.cost")
                .tags("model", "claude-haiku-5-5", "outcome", "extracted").summary();
        assertThat(extracted.count()).isEqualTo(2);
        assertThat(extracted.totalAmount()).isEqualTo(0.00045, org.assertj.core.data.Offset.offset(1e-12));
        assertThat(registry.get("ai.claude.cost").tags("outcome", "unreadable").summary().count()).isEqualTo(1);

        assertThat(tokens("input")).isEqualTo(3500);
        assertThat(tokens("output")).isEqualTo(350);
        assertThat(tokens("cache_write")).isEqualTo(30);
        assertThat(tokens("cache_read")).isEqualTo(40);
    }

    private double tokens(String type) {
        return registry.get("ai.claude.tokens").tags("model", "claude-haiku-5-5", "type", type).counter().count();
    }
}
