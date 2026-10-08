package am.furnituredetailing.web;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;

/**
 * Body of POST /api/analyze. Images arrive base64-encoded (the UI downsizes them first).
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record AnalyzeRequest(Integer width, Integer height, Integer depth, String notes, Integer thickness,
                             List<Image> images) {

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Image(String mediaType, String data) {}
}
