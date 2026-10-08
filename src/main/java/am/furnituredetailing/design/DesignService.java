package am.furnituredetailing.design;

import am.furnituredetailing.claude.ClaudeException;
import am.furnituredetailing.claude.ClaudeGateway;
import am.furnituredetailing.claude.ClaudeGateway.ImageInput;
import am.furnituredetailing.claude.ClaudeProperties;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;

/**
 * Photo + sizes → Claude → validated {@link DesignProposal}.
 */
public class DesignService {

    private static final System.Logger log = System.getLogger(DesignService.class.getName());
    static final Set<String> IMAGE_TYPES = Set.of("image/jpeg", "image/png", "image/webp", "image/gif");
    static final int MAX_ROOM_IMAGES = 2;
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
        return analyze(request, images, List.of());
    }

    /**
     * @param pieceImages photos/sketches of the furniture itself
     * @param roomImages  photos of the space it will stand in; sent after the piece images
     */
    public DesignProposal analyze(DesignRequest request, List<ImageInput> pieceImages, List<ImageInput> roomImages) {
        validate(request, pieceImages, roomImages);
        List<ImageInput> all = new java.util.ArrayList<>(pieceImages);
        all.addAll(roomImages);
        String prompt = FurniturePrompt.build(request, pieceImages.size(), roomImages.size());
        String answer = claude.ask(prompt, all);
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

    private void validate(DesignRequest request, List<ImageInput> images, List<ImageInput> roomImages) {
        boolean hasNotes = request.notes() != null && !request.notes().isBlank();
        if (images.isEmpty() && !hasNotes) {
            throw new InvalidDesignInputException("Добавьте фото или опишите мебель.");
        }
        if (images.size() > props.maxImages()) {
            throw new InvalidDesignInputException("Не больше " + props.maxImages() + " фото за раз.");
        }
        if (roomImages.size() > MAX_ROOM_IMAGES) {
            throw new InvalidDesignInputException("Не больше " + MAX_ROOM_IMAGES + " фото помещения.");
        }
        for (ImageInput img : Stream.concat(images.stream(), roomImages.stream()).toList()) {
            if (!IMAGE_TYPES.contains(img.mediaType())) {
                throw new InvalidDesignInputException("Формат " + img.mediaType() + " не поддерживается: нужен JPG, PNG, WebP или GIF.");
            }
            if (img.data().length > MAX_IMAGE_BYTES) {
                throw new InvalidDesignInputException("Фото больше 5 МБ. Уменьшите его и попробуйте снова.");
            }
        }
    }
}
