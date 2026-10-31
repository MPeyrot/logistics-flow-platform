package fr.michael.logistics.ai.domain;

import java.util.Objects;

/** Document à analyser : PDF ou image. L'API Claude refuse les images de plus de 5 Mo. */
public record SourceDocument(byte[] content, DocumentFormat format) {

    public static final int MAX_IMAGE_BYTES = 5 * 1024 * 1024;

    public SourceDocument {
        Objects.requireNonNull(content, "content");
        Objects.requireNonNull(format, "format");
        if (content.length == 0) {
            throw new IllegalArgumentException("document is empty");
        }
        if (format.isImage() && content.length > MAX_IMAGE_BYTES) {
            throw new IllegalArgumentException("image must not exceed 5 MB");
        }
    }
}
