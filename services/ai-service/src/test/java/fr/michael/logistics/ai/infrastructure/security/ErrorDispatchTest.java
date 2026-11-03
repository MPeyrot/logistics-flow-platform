package fr.michael.logistics.ai.infrastructure.security;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/**
 * Vrai serveur : MockMvc ne fait pas le dispatch ERROR vers {@code /error}, où une erreur 400 ou 413 devenait 403.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class ErrorDispatchTest {

    private static final String BOUNDARY = "error-dispatch-boundary";

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        TestJwt.register(registry);
        registry.add("ai.claude.api-key", () -> "");
        registry.add("spring.servlet.multipart.max-file-size", () -> "1KB");
    }

    @LocalServerPort
    int port;

    private final HttpClient http = HttpClient.newHttpClient();

    @Test
    void missingFilePartIsBadRequest() throws Exception {
        HttpResponse<String> response = extract(part("other", "x"));

        assertThat(response.statusCode()).isEqualTo(400);
    }

    @Test
    void fileOverTheSizeLimitIsPayloadTooLarge() throws Exception {
        HttpResponse<String> response = extract(part("file", "x".repeat(4096)));

        assertThat(response.statusCode()).isEqualTo(413);
    }

    private static String part(String name, String content) {
        return "--" + BOUNDARY + "\r\n"
                + "Content-Disposition: form-data; name=\"" + name + "\"; filename=\"note.pdf\"\r\n"
                + "Content-Type: application/octet-stream\r\n\r\n"
                + content + "\r\n"
                + "--" + BOUNDARY + "--\r\n";
    }

    private HttpResponse<String> extract(String body) throws Exception {
        HttpRequest request = HttpRequest.newBuilder(
                        URI.create("http://localhost:" + port + "/api/delivery-notes/extract"))
                .header("Content-Type", "multipart/form-data; boundary=" + BOUNDARY)
                .header("Authorization", "Bearer " + TestJwt.service().build())
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build();
        return http.send(request, HttpResponse.BodyHandlers.ofString());
    }
}
