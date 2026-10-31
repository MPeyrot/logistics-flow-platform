package fr.michael.logistics.ai.infrastructure.claude;

import com.anthropic.client.AnthropicClient;
import com.anthropic.errors.AnthropicException;
import com.anthropic.models.messages.Base64ImageSource;
import com.anthropic.models.messages.Base64PdfSource;
import com.anthropic.models.messages.CacheControlEphemeral;
import com.anthropic.models.messages.ContentBlockParam;
import com.anthropic.models.messages.DocumentBlockParam;
import com.anthropic.models.messages.ImageBlockParam;
import com.anthropic.models.messages.Message;
import com.anthropic.models.messages.MessageCreateParams;
import com.anthropic.models.messages.OutputConfig;
import com.anthropic.models.messages.StopReason;
import com.anthropic.models.messages.TextBlockParam;
import fr.michael.logistics.ai.application.DeliveryNoteExtractor;
import fr.michael.logistics.ai.application.ExtractionFailedException;
import fr.michael.logistics.ai.application.ExtractionUnavailableException;
import fr.michael.logistics.ai.application.ModelExtraction;
import fr.michael.logistics.ai.application.UnreadableDocumentException;
import fr.michael.logistics.ai.domain.DocumentFormat;
import fr.michael.logistics.ai.domain.SourceDocument;
import fr.michael.logistics.ai.domain.TokenUsage;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validator;
import java.util.Base64;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.json.JsonMapper;

@Component
class ClaudeDeliveryNoteExtractor implements DeliveryNoteExtractor {

    private final AnthropicClient client;
    private final ClaudeProperties props;
    private final Validator validator;
    private final JsonMapper jsonMapper;

    ClaudeDeliveryNoteExtractor(AnthropicClient client, ClaudeProperties props, Validator validator,
            JsonMapper jsonMapper) {
        this.client = client;
        this.props = props;
        this.validator = validator;
        this.jsonMapper = jsonMapper;
    }

    @Override
    public ModelExtraction extract(SourceDocument document) {
        if (!props.hasApiKey()) {
            throw new ExtractionUnavailableException("ANTHROPIC_API_KEY is not set");
        }
        Message response = call(document);
        TokenUsage usage = usageOf(response);

        if (response.stopReason().filter(r -> r.equals(StopReason.REFUSAL) || r.equals(StopReason.MAX_TOKENS)).isPresent()) {
            throw new UnreadableDocumentException("Model stopped early: " + response.stopReason().orElseThrow(), usage);
        }
        String text = response.content().stream()
                .flatMap(block -> block.text().stream())
                .map(t -> t.text())
                .collect(Collectors.joining());

        DeliveryNoteDraft draft = parse(text, usage);
        Set<ConstraintViolation<DeliveryNoteDraft>> violations = validator.validate(draft);
        if (!violations.isEmpty()) {
            String detail = violations.stream()
                    .map(v -> v.getPropertyPath() + " " + v.getMessage())
                    .sorted()
                    .collect(Collectors.joining(", "));
            throw new UnreadableDocumentException("Invalid extraction: " + detail, usage);
        }
        return new ModelExtraction(draft.toDomain(), usage);
    }

    private Message call(SourceDocument document) {
        // Pas de `thinking` ni de `tool_choice` : le modèle choisit seul, et le JSON est contrôlé après coup.
        MessageCreateParams params = MessageCreateParams.builder()
                .model(props.model())
                .maxTokens(props.maxTokens())
                .outputConfig(OutputConfig.builder()
                        .effort(OutputConfig.Effort.of(props.effort().toLowerCase(Locale.ROOT)))
                        .build())
                .systemOfTextBlockParams(List.of(TextBlockParam.builder()
                        .text(ExtractionPrompt.SYSTEM)
                        .cacheControl(CacheControlEphemeral.builder().build())
                        .build()))
                .addUserMessageOfContentBlockParams(List.of(
                        documentBlock(document),
                        ContentBlockParam.ofText(TextBlockParam.builder().text(ExtractionPrompt.USER).build())))
                .build();
        try {
            return client.messages().create(params);
        } catch (AnthropicException e) {
            throw new ExtractionFailedException("Claude API call failed: " + e.getMessage(), e);
        }
    }

    private static ContentBlockParam documentBlock(SourceDocument document) {
        String data = Base64.getEncoder().encodeToString(document.content());
        if (document.format() == DocumentFormat.PDF) {
            return ContentBlockParam.ofDocument(DocumentBlockParam.builder()
                    .source(Base64PdfSource.builder().data(data).build())
                    .build());
        }
        return ContentBlockParam.ofImage(ImageBlockParam.builder()
                .source(Base64ImageSource.builder()
                        .data(data)
                        .mediaType(Base64ImageSource.MediaType.of(document.format().mediaType()))
                        .build())
                .build());
    }

    private DeliveryNoteDraft parse(String text, TokenUsage usage) {
        try {
            return jsonMapper.readValue(stripCodeFence(text), DeliveryNoteDraft.class);
        } catch (JacksonException e) {
            throw new UnreadableDocumentException("Model did not return valid JSON", usage, e);
        }
    }

    /** Le modèle entoure parfois le JSON d'un bloc ``` malgré la consigne. */
    private static String stripCodeFence(String text) {
        String t = text.strip();
        if (t.startsWith("```")) {
            int firstLineEnd = t.indexOf('\n');
            int end = t.lastIndexOf("```");
            if (firstLineEnd > 0 && end > firstLineEnd) {
                return t.substring(firstLineEnd + 1, end).strip();
            }
        }
        return t;
    }

    private static TokenUsage usageOf(Message response) {
        var usage = response.usage();
        return new TokenUsage(
                usage.inputTokens(),
                usage.outputTokens(),
                usage.cacheCreationInputTokens().orElse(0L),
                usage.cacheReadInputTokens().orElse(0L));
    }
}
