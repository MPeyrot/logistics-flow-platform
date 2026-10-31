package fr.michael.logistics.shipment.infrastructure.ai;

import fr.michael.logistics.shipment.application.DeliveryNoteData;
import fr.michael.logistics.shipment.application.DeliveryNoteReader;
import fr.michael.logistics.shipment.application.DeliveryNoteReading;
import fr.michael.logistics.shipment.application.ExtractionServiceUnavailableException;
import fr.michael.logistics.shipment.application.UnreadableDeliveryNoteException;
import fr.michael.logistics.shipment.application.UnsupportedDocumentException;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validator;
import java.net.http.HttpClient;
import java.util.Set;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

@Component
class AiServiceDeliveryNoteReader implements DeliveryNoteReader {

    private final RestClient client;
    private final Validator validator;

    AiServiceDeliveryNoteReader(AiServiceProperties props, Validator validator) {
        var factory = new JdkClientHttpRequestFactory(
                HttpClient.newBuilder().version(HttpClient.Version.HTTP_1_1).connectTimeout(props.connectTimeout()).build());
        factory.setReadTimeout(props.readTimeout());
        this.client = RestClient.builder().baseUrl(props.baseUrl()).requestFactory(factory).build();
        this.validator = validator;
    }

    @Override
    public DeliveryNoteReading read(byte[] content, String filename) {
        MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
        body.add("file", new ByteArrayResource(content) {
            @Override
            public String getFilename() {
                return filename == null || filename.isBlank() ? "document" : filename;
            }
        });
        AiExtractionResponse response;
        try {
            response = client.post()
                    .uri("/api/delivery-notes/extract")
                    .contentType(MediaType.MULTIPART_FORM_DATA)
                    .body(body)
                    .retrieve()
                    .body(AiExtractionResponse.class);
        } catch (RestClientResponseException e) {
            throw translate(e);
        } catch (RestClientException e) {
            throw new ExtractionServiceUnavailableException("ai-service unreachable: " + e.getMessage(), e);
        }
        return toReading(response);
    }

    private static RuntimeException translate(RestClientResponseException e) {
        HttpStatusCode status = e.getStatusCode();
        return switch (status.value()) {
            case 415 -> new UnsupportedDocumentException("Expected a PDF, PNG, JPEG, GIF or WebP file");
            case 422 -> new UnreadableDeliveryNoteException("No usable delivery note found in the document");
            case 400 -> new IllegalArgumentException("The document was rejected by the extraction service");
            default -> new ExtractionServiceUnavailableException("ai-service answered " + status.value(), e);
        };
    }

    private DeliveryNoteReading toReading(AiExtractionResponse response) {
        if (response == null) {
            throw new ExtractionServiceUnavailableException("ai-service returned an empty response");
        }
        Set<ConstraintViolation<AiExtractionResponse>> violations = validator.validate(response);
        if (!violations.isEmpty()) {
            throw new ExtractionServiceUnavailableException("ai-service returned an invalid response: " + violations);
        }
        var n = response.deliveryNote();
        return new DeliveryNoteReading(
                new DeliveryNoteData(n.reference(), n.deliveryDate(), n.senderName(), n.recipientName(),
                        n.recipientAddress(), n.packageCount(), n.totalWeightKg()),
                response.costUsd());
    }
}
