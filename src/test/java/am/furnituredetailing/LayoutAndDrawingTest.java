package am.furnituredetailing;

import am.furnituredetailing.design.Layout;
import am.furnituredetailing.design.ModuleSpec;
import am.furnituredetailing.drawing.AssemblyDrawing;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class LayoutAndDrawingTest {

    static ModuleSpec mod(String name, int w, int h, Integer x, Integer y) {
        return new ModuleSpec("wardrobe", name, w, h, 580, 1, 0, 180, 0, 2, true, "solid", "none", 100, 80,
                false, true, false, x, y).normalized();
    }

    @Test
    void zonesBecomeColumnsAndRowsStackUp() {
        List<ModuleSpec> out = Layout.place(List.of(
                mod("Л: низ", 500, 450, null, null),
                mod("Л: шкаф", 500, 1650, null, null),
                mod("Ц: тумба", 450, 450, null, null),
                mod("Ц: ниша", 550, 450, null, null),
                mod("Ц: верх", 1000, 1650, null, null),
                mod("П: пенал", 400, 2100, null, null)));
        assertPos(out.get(0), 0, 0);
        assertPos(out.get(1), 0, 450);
        assertPos(out.get(2), 500, 0);
        assertPos(out.get(3), 950, 0);     // side by side with the tumba
        assertPos(out.get(4), 500, 450);   // next row
        assertPos(out.get(5), 1500, 0);
    }

    @Test
    void explicitPositionsAreKeptAndOthersGoToTheRight() {
        List<ModuleSpec> out = Layout.place(List.of(
                mod("A", 600, 800, 0, 0),
                mod("B", 600, 800, 600, 0),
                mod("C", 400, 800, null, null)));
        assertPos(out.get(0), 0, 0);
        assertPos(out.get(1), 600, 0);
        assertPos(out.get(2), 1200, 0);
    }

    @Test
    void unnamedModulesStandSideBySide() {
        List<ModuleSpec> out = Layout.place(List.of(mod("Мойка", 800, 820, null, null), mod("Ящики", 600, 820, null, null)));
        assertPos(out.get(0), 0, 0);
        assertPos(out.get(1), 800, 0);
    }

    @Test
    void drawingHasBothViewsDimensionsAndTable() {
        String svg = AssemblyDrawing.render(List.of(
                mod("Л: шкаф <тест>", 500, 2100, null, null),
                mod("П: пенал", 400, 2100, null, null)), 18, null);
        assertTrue(svg.startsWith("<svg"));
        assertTrue(svg.contains("ВИД СПЕРЕДИ — КОРПУС"));
        assertTrue(svg.contains("ВИД СПЕРЕДИ — С ФАСАДАМИ"));
        assertTrue(svg.contains("Сборка 900 × 2100 × 580 мм"));
        assertTrue(svg.contains(">500</text>") && svg.contains(">400</text>") && svg.contains(">900</text>"));
        assertTrue(svg.contains("Л: шкаф &lt;тест&gt;"));       // escaped
        assertFalse(svg.contains("<тест>"));
        assertTrue(svg.trim().endsWith("</svg>"));
    }

    private static void assertPos(ModuleSpec m, int x, int y) {
        assertEquals(x, m.x(), m.name() + " x");
        assertEquals(y, m.y(), m.name() + " y");
    }
}
