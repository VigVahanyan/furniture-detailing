package am.furnituredetailing.design;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;

/**
 * Claude's breakdown of a piece of furniture into constructor modules.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record DesignProposal(String summary, List<ModuleSpec> modules, List<String> notes, List<String> siteNotes) {

    public static final int MAX_MODULES = 12;

    /** Clamps every module and gives each one a position in the assembly. */
    public DesignProposal normalized() {
        List<ModuleSpec> mods = modules == null ? List.of()
                : Layout.place(modules.stream().limit(MAX_MODULES).map(ModuleSpec::normalized).toList());
        List<String> ns = notes == null ? List.of()
                : notes.stream().filter(s -> s != null && !s.isBlank()).limit(8).toList();
        List<String> site = siteNotes == null ? List.of()
                : siteNotes.stream().filter(s -> s != null && !s.isBlank()).limit(10).toList();
        return new DesignProposal(summary == null ? "" : summary.strip(), mods, ns, site);
    }
}
