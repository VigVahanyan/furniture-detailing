package am.furnituredetailing.design;

/**
 * The user's request cannot be sent (no photo or description, wrong file type, too many photos...).
 */
public class InvalidDesignInputException extends RuntimeException {

    private static final long serialVersionUID = 1L;
    public InvalidDesignInputException(String message) {
        super(message);
    }
}
