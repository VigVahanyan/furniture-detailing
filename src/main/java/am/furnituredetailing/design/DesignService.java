package am.furnituredetailing.design;

import am.furnituredetailing.claude.ClaudeException;
import am.furnituredetailing.claude.ClaudeGateway;
import am.furnituredetailing.claude.ClaudeGateway.ImageInput;
import am.furnituredetailing.claude.ClaudeProperties;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.util.List;
import java.util.Set;

/**
 * Photo + sizes → Claude → validated {@link DesignProposal}.
 */
public class DesignService {

    private static final System.Logger log = System.getLogger(DesignService.class.getName());
    static final Set<String> IMAGE_TYPES = Set.of("image/jpeg", "image/png", "image/webp", "image/gif");
    static final int MAX_IMAGE_BYTES = 5 * 1024 * 1024;

    private final ClaudeGateway claude;
    private final ClaudeProperties props;
    private final ObjectMapper mapper;

    public DesignService(ClaudeGateway claude, ClaudeProperties props, ObjectMapper mapper) {
        this.claude = claude;
        this.props = props;
        this.mapper = mapper;
    }

    public DesignProposal analyze(DesignRequest request, List<ImageInput> images) {
        validate(request, images);
        String prompt = FurniturePrompt.build(request, images.size());
        String answer = claude.ask(prompt, images);
        return parse(answer);
    }

    /** Also used for JSON the user pastes from a chat. */
    public DesignProposal parse(String answer) {
        String json = JsonExtractor.extract(answer)
                .orElseThrow(() -> new ClaudeException(502, "Claude answered without JSON"));
        DesignProposal proposal;
        try {
            proposal = mapper.readValue(json, DesignProposal.class).normalized();
        } catch (IOException e) {
            log.log(System.Logger.Level.WARNING, "Unparseable proposal: " + e.getMessage());
            throw new ClaudeException(502, "Claude's JSON could not be read");
        }
        if (proposal.modules().isEmpty()) {
            throw new ClaudeException(502, "Claude proposed no modules");
        }
        return proposal;
    }

    private void validate(DesignRequest request, List<ImageInput> images) {
        boolean hasNotes = request.notes() != null && !request.notes().isBlank();
        if (images.isEmpty() && !hasNotes) {
            throw new InvalidDesignInputException("Добавьте фото или опишите мебель.");
        }
        if (images.size() > props.maxImages()) {
            throw new InvalidDesignInputException("Не больше " + props.maxImages() + " фото за раз.");
        }
        for (ImageInput img : images) {
            if (!IMAGE_TYPES.contains(img.mediaType())) {
                throw new InvalidDesignInputException("Формат " + img.mediaType() + " не поддерживается: нужен JPG, PNG, WebP или GIF.");
            }
            if (img.data().length > MAX_IMAGE_BYTES) {
                throw new InvalidDesignInputException("Фото больше 5 МБ. Уменьшите его и попробуйте снова.");
            }
        }
    }
}
