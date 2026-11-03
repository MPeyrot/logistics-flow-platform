package fr.michael.logistics.ai.infrastructure.web;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import fr.michael.logistics.ai.infrastructure.security.TestJwt;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockMultipartHttpServletRequestBuilder;

@SpringBootTest
@AutoConfigureMockMvc
class ExtractionQuotaApiTest {

    private static final WireMockServer WIRE_MOCK = new WireMockServer(WireMockConfiguration.options().dynamicPort());

    static {
        WIRE_MOCK.start();
        WIRE_MOCK.stubFor(post("/v1/messages").willReturn(aResponse().withStatus(200)
                .withHeader("content-type", "application/json")
                .withBody("""
                        {"id":"msg_test","type":"message","role":"assistant","model":"claude-opus-5-5",
                         "content":[{"type":"text","text":"{\\"reference\\":\\"BL-1\\"}"}],
                         "stop_reason":"end_turn","stop_sequence":null,
                         "usage":{"input_tokens":10,"output_tokens":5}}""")));
    }

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("ai.claude.base-url", WIRE_MOCK::baseUrl);
        registry.add("ai.claude.api-key", () -> "test-key");
        registry.add("ai.claude.max-retries", () -> "0");
        registry.add("ai.extraction.max-per-hour", () -> "2");
        TestJwt.register(registry);
    }

    @AfterAll
    static void stop() {
        WIRE_MOCK.stop();
    }

    @Autowired
    MockMvc mvc;

    @Test
    void answers429WithRetryAfterAndSkipsClaudeOnceTheCapIsReached() throws Exception {
        for (int i = 0; i < 2; i++) {
            mvc.perform(extract()).andExpect(status().isOk());
        }
        assertThat(WIRE_MOCK.getAllServeEvents()).hasSize(2);

        mvc.perform(extract())
                .andExpect(status().isTooManyRequests())
                .andExpect(header().exists("Retry-After"))
                .andExpect(jsonPath("$.status").value(429));

        assertThat(WIRE_MOCK.getAllServeEvents()).hasSize(2);
    }

    private static MockMultipartHttpServletRequestBuilder extract() {
        return multipart("/api/delivery-notes/extract")
                .header("Authorization", "Bearer " + TestJwt.service().build())
                .file(new MockMultipartFile("file", "bl.pdf", "application/pdf", "%PDF-1.4 fake".getBytes()));
    }
}
