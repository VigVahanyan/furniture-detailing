package am.furnituredetailing.design;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.Map;
import java.util.Set;

/**
 * One cabinet carcass as the constructor understands it. Field names match the front-end engine.
 * {@code x}/{@code y} place the module in the assembly: mm from the left edge and from the floor.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record ModuleSpec(
        String type, String name,
        Integer W, Integer H, Integer D,
        Integer doors, Integer drawers, Integer dh,
        Integer partitions, Integer shelves,
        Boolean removable, String top, String base,
        Integer legH, Integer plinthH,
        Boolean rod, Boolean back, Boolean hang,
        Integer x, Integer y
) {
    public static final Set<String> TYPES = Set.of("wardrobe", "dresser", "kitchenLow", "kitchenUp", "rack", "tumba");

    private static final Map<String, ModuleSpec> DEFAULTS = Map.of(
            "wardrobe",   preset("wardrobe", "Шкаф распашной", 1200, 2200, 600, 2, 0, 180, 0, 3, true, "solid", "plinth", 100, 80, true, true, false),
            "dresser",    preset("dresser", "Комод", 800, 850, 450, 0, 4, 180, 0, 0, true, "solid", "legs", 100, 80, false, true, false),
            "kitchenLow", preset("kitchenLow", "Кухня: нижний", 600, 820, 560, 1, 0, 150, 0, 1, true, "rails", "legs", 100, 100, false, true, false),
            "kitchenUp",  preset("kitchenUp", "Кухня: верхний", 600, 720, 300, 1, 0, 150, 0, 2, true, "solid", "none", 100, 80, false, true, true),
            "rack",       preset("rack", "Стеллаж", 800, 1800, 350, 0, 0, 150, 1, 4, false, "solid", "plinth", 100, 60, false, true, false),
            "tumba",      preset("tumba", "Тумба", 500, 600, 400, 1, 1, 150, 0, 1, true, "solid", "legs", 100, 80, false, true, false)
    );

    private static ModuleSpec preset(String type, String name, int W, int H, int D, int doors, int drawers, int dh,
                                     int partitions, int shelves, boolean removable, String top, String base,
                                     int legH, int plinthH, boolean rod, boolean back, boolean hang) {
        return new ModuleSpec(type, name, W, H, D, doors, drawers, dh, partitions, shelves, removable, top, base,
                legH, plinthH, rod, back, hang, null, null);
    }

    /**
     * Fills missing values from the type preset and clamps everything into ranges the engine can build.
     * Position stays null when unknown; {@link Layout} fills it.
     */
    public ModuleSpec normalized() {
        String t = type != null && TYPES.contains(type) ? type : "wardrobe";
        ModuleSpec d = DEFAULTS.get(t);
        String n = name == null || name.isBlank() ? d.name : name.strip();
        if (n.length() > 40) n = n.substring(0, 40);
        return new ModuleSpec(
                t, n,
                clamp(W, 200, 3600, d.W), clamp(H, 200, 2800, d.H), clamp(D, 150, 900, d.D),
                clamp(doors, 0, 6, d.doors), clamp(drawers, 0, 8, d.drawers), clamp(dh, 100, 400, d.dh),
                clamp(partitions, 0, 5, d.partitions), clamp(shelves, 0, 12, d.shelves),
                removable == null || removable,
                "rails".equals(top) ? "rails" : "solid",
                base != null && Set.of("none", "legs", "plinth").contains(base) ? base : d.base,
                clamp(legH, 20, 200, d.legH), clamp(plinthH, 40, 200, d.plinthH),
                rod != null && rod,
                back == null || back,
                hang != null && hang,
                x == null ? null : clamp(x, 0, 20000, 0),
                y == null ? null : clamp(y, 0, 5000, 0)
        );
    }

    public ModuleSpec at(int nx, int ny) {
        return new ModuleSpec(type, name, W, H, D, doors, drawers, dh, partitions, shelves, removable, top, base,
                legH, plinthH, rod, back, hang, nx, ny);
    }

    private static int clamp(Integer v, int min, int max, int def) {
        if (v == null) return def;
        return Math.max(min, Math.min(max, v));
    }
}
