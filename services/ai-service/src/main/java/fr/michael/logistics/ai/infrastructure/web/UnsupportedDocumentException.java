package fr.michael.logistics.ai.infrastructure.web;

class UnsupportedDocumentException extends RuntimeException {

    UnsupportedDocumentException() {
        super("Unsupported document: expected a PDF, PNG, JPEG, GIF or WebP file");
    }
}
