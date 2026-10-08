package am.furnituredetailing;

import am.furnituredetailing.design.DesignProposal;
import am.furnituredetailing.design.DesignRequest;
import am.furnituredetailing.design.FurniturePrompt;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FurniturePromptTest {

    private final DesignRequest request = new DesignRequest(1900, 2700, 580, "прихожая", 18);

    @Test
    void roomPhotosAddObstacleInstructions() {
        String prompt = FurniturePrompt.build(request, 1, 2);
        assertTrue(prompt.contains("фото помещения (2 шт.)"));
        assertTrue(prompt.contains("коллекторы"));
        assertTrue(prompt.contains("siteNotes"));
    }

    @Test
    void withoutRoomPhotosThereIsNoRoomSection() {
        assertFalse(FurniturePrompt.build(request, 1).contains("Далее приложено фото помещения"));
    }

    @Test
    void siteNotesAreCleanedAndCapped() {
        List<String> raw = new java.util.ArrayList<>(List.of(" ", "коллектор справа"));
        for (int i = 0; i < 20; i++) raw.add("п" + i);
        DesignProposal p = new DesignProposal("s", null, null, raw).normalized();
        assertTrue(p.siteNotes().size() <= 10);
        assertTrue(p.siteNotes().contains("коллектор справа"));
        assertFalse(p.siteNotes().contains(" "));
    }
}
