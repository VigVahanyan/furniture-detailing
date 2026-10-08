package am.furnituredetailing.web;

import am.furnituredetailing.design.ModuleSpec;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;

/**
 * Body of POST /api/drawing: the modules to draw, panel thickness and an optional title.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record DrawingRequest(List<ModuleSpec> modules, Integer thickness, String title) {
}
