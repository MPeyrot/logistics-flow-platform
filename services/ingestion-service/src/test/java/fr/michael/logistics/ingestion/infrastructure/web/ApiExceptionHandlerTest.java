package fr.michael.logistics.ingestion.infrastructure.web;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.ErrorResponseException;

class ApiExceptionHandlerTest {

    @Test
    void errorResponseKeepsItsStatusDetailAndHeaders() {
        ErrorResponseException e = new ErrorResponseException(HttpStatus.SERVICE_UNAVAILABLE,
                ProblemDetail.forStatusAndDetail(HttpStatus.SERVICE_UNAVAILABLE, "busy"), null);
        e.getHeaders().set(HttpHeaders.RETRY_AFTER, "30");

        ResponseEntity<ProblemDetail> response = new ApiExceptionHandler().errorResponse(e);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
        assertThat(response.getHeaders().getFirst(HttpHeaders.RETRY_AFTER)).isEqualTo("30");
        assertThat(response.getBody().getDetail()).isEqualTo("busy");
    }
}
