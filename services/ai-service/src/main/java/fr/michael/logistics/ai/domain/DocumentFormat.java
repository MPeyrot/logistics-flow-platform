package fr.michael.logistics.ai.domain;

import java.util.Optional;

/** Formats de document acceptés par l'API Claude, reconnus d'après leurs premiers octets. */
public enum DocumentFormat {
    PDF("application/pdf", false),
    PNG("image/png", true),
    JPEG("image/jpeg", true),
    GIF("image/gif", true),
    WEBP("image/webp", true);

    private final String mediaType;
    private final boolean image;

    DocumentFormat(String mediaType, boolean image) {
        this.mediaType = mediaType;
        this.image = image;
    }

    public String mediaType() {
        return mediaType;
    }

    public boolean isImage() {
        return image;
    }

    /** On ne fait pas confiance au Content-Type déclaré par le client : seul le contenu compte. */
    public static Optional<DocumentFormat> detect(byte[] content) {
        if (startsWith(content, 0, '%', 'P', 'D', 'F', '-')) {
            return Optional.of(PDF);
        }
        if (startsWith(content, 0, 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A)) {
            return Optional.of(PNG);
        }
        if (startsWith(content, 0, 0xFF, 0xD8, 0xFF)) {
            return Optional.of(JPEG);
        }
        if (startsWith(content, 0, 'G', 'I', 'F', '8')) {
            return Optional.of(GIF);
        }
        if (startsWith(content, 0, 'R', 'I', 'F', 'F') && startsWith(content, 8, 'W', 'E', 'B', 'P')) {
            return Optional.of(WEBP);
        }
        return Optional.empty();
    }

    private static boolean startsWith(byte[] content, int offset, int... expected) {
        if (content.length < offset + expected.length) {
            return false;
        }
        for (int i = 0; i < expected.length; i++) {
            if ((content[offset + i] & 0xFF) != expected[i]) {
                return false;
            }
        }
        return true;
    }
}
