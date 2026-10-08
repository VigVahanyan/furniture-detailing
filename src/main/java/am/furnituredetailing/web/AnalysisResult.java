package am.furnituredetailing.web;

import am.furnituredetailing.design.DesignProposal;
import am.furnituredetailing.design.ModuleSpec;

import java.util.List;

/**
 * Response of POST /api/analyze: Claude's proposal plus the assembly drawing as SVG.
 */
public record AnalysisResult(String summary, List<ModuleSpec> modules, List<String> notes, List<String> siteNotes, String drawingSvg) {

    static AnalysisResult of(DesignProposal p, String svg) {
        return new AnalysisResult(p.summary(), p.modules(), p.notes(), p.siteNotes(), svg);
    }
}
