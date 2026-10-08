package am.furnituredetailing.claude;

/**
 * Failure talking to the Anthropic API. {@code status} is the upstream HTTP status, or 0 for network errors.
 */
public class ClaudeException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    private final int status;

    public ClaudeException(int status, String message) {
        super(message);
        this.status = status;
    }

    public ClaudeException(String message, Throwable cause) {
        super(message, cause);
        this.status = 0;
    }

    public int status() {
        return status;
    }
}
