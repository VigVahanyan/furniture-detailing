package am.furnituredetailing.design;

/**
 * What the user knows about the piece: overall size (mm, any may be null), wishes, and sheet thickness.
 */
public record DesignRequest(Integer width, Integer height, Integer depth, String notes, int thickness) {
}
