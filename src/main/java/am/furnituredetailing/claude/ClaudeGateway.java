package am.furnituredetailing.claude;

import java.util.List;

/**
 * Sends a prompt (optionally with images) to Claude and returns the text answer.
 */
public interface ClaudeGateway {

    String ask(String prompt, List<ImageInput> images);

    /** Image bytes plus MIME type: image/jpeg, image/png, image/webp or image/gif. */
    record ImageInput(String mediaType, byte[] data) {}
}
