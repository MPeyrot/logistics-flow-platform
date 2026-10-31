package fr.michael.logistics.ai.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Optional;
import org.junit.jupiter.api.Test;

class DocumentFormatTest {

    @Test
    void detectsFormatsFromMagicBytes() {
        assertThat(DocumentFormat.detect("%PDF-1.7 ...".getBytes())).contains(DocumentFormat.PDF);
        assertThat(DocumentFormat.detect(bytes(0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A, 0))).contains(DocumentFormat.PNG);
        assertThat(DocumentFormat.detect(bytes(0xFF, 0xD8, 0xFF, 0xE0))).contains(DocumentFormat.JPEG);
        assertThat(DocumentFormat.detect("GIF89a".getBytes())).contains(DocumentFormat.GIF);
        assertThat(DocumentFormat.detect(bytes('R', 'I', 'F', 'F', 0, 0, 0, 0, 'W', 'E', 'B', 'P'))).contains(DocumentFormat.WEBP);
    }

    @Test
    void rejectsOtherContent() {
        assertThat(DocumentFormat.detect("just some text".getBytes())).isEqualTo(Optional.empty());
        assertThat(DocumentFormat.detect(new byte[0])).isEmpty();
        assertThat(DocumentFormat.detect(bytes('R', 'I', 'F', 'F', 0, 0, 0, 0, 'W', 'A', 'V', 'E'))).isEmpty();
    }

    @Test
    void sourceDocumentRejectsEmptyAndOversizedImages() {
        assertThatThrownBy(() -> new SourceDocument(new byte[0], DocumentFormat.PDF))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new SourceDocument(new byte[SourceDocument.MAX_IMAGE_BYTES + 1], DocumentFormat.PNG))
                .isInstanceOf(IllegalArgumentException.class);
        new SourceDocument(new byte[SourceDocument.MAX_IMAGE_BYTES + 1], DocumentFormat.PDF);
    }

    private static byte[] bytes(int... values) {
        byte[] out = new byte[values.length];
        for (int i = 0; i < values.length; i++) {
            out[i] = (byte) values[i];
        }
        return out;
    }
}
