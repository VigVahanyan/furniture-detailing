package am.furnituredetailing.design;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Places modules in the assembly. Modules that already have x/y keep them; the rest are packed to the right:
 * modules named «Л: …», «Ц: …», «П: …» (or L/C/R) form one column per zone, filled bottom-up in rows;
 * unnamed modules each get their own column.
 */
public final class Layout {

    private static final Pattern ZONE = Pattern.compile("^\\s*([ЛЦПлцпLCRlcr])\\s*[:.]");

    private Layout() {
    }

    /** Expects normalized modules (W/H set). Returns the same modules, in the same order, all positioned. */
    public static List<ModuleSpec> place(List<ModuleSpec> modules) {
        ModuleSpec[] out = modules.toArray(ModuleSpec[]::new);
        int startX = 0;
        for (ModuleSpec m : modules) {
            if (m.x() != null && m.y() != null) startX = Math.max(startX, m.x() + m.W());
        }
        // column key → indexes, in first-seen order of zones L, C, R then unnamed
        Map<String, List<Integer>> columns = new TreeMap<>(Comparator.comparingInt(Layout::rank).thenComparing(k -> k));
        for (int i = 0; i < out.length; i++) {
            if (out[i].x() != null && out[i].y() != null) continue;
            String zone = zoneOf(out[i].name());
            String key = zone != null ? zone : "~" + String.format("%03d", i);
            columns.computeIfAbsent(key, k -> new ArrayList<>()).add(i);
        }
        int x0 = startX;
        for (List<Integer> col : columns.values()) {
            int colW = col.stream().mapToInt(i -> out[i].W()).max().orElse(0);
            int rowX = 0, rowY = 0, rowH = 0;
            for (int i : col) {
                ModuleSpec m = out[i];
                if (rowX > 0 && rowX + m.W() > colW) {   // next row up
                    rowY += rowH;
                    rowX = 0;
                    rowH = 0;
                }
                out[i] = m.at(x0 + rowX, rowY);
                rowX += m.W();
                rowH = Math.max(rowH, m.H());
            }
            x0 += colW;
        }
        return List.of(out);
    }

    static String zoneOf(String name) {
        if (name == null) return null;
        Matcher m = ZONE.matcher(name);
        if (!m.find()) return null;
        return switch (Character.toUpperCase(m.group(1).charAt(0))) {
            case 'Л', 'L' -> "L";
            case 'Ц', 'C' -> "C";
            default -> "R";
        };
    }

    private static int rank(String key) {
        return switch (key) {
            case "L" -> 0;
            case "C" -> 1;
            case "R" -> 2;
            default -> 3;
        };
    }
}
