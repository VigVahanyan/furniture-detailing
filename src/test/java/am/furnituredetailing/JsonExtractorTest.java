package am.furnituredetailing;

import am.furnituredetailing.design.JsonExtractor;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;

class JsonExtractorTest {

    @Test
    void plainObject() {
        assertEquals(Optional.of("{\"a\":1}"), JsonExtractor.extract(" {\"a\":1} "));
    }

    @Test
    void fencedObjectWithProse() {
        assertEquals(Optional.of("{\"a\":{\"b\":2}}"),
                JsonExtractor.extract("Here:\n```json\n{\"a\":{\"b\":2}}\n```\nDone"));
    }

    @Test
    void objectInsideSentence() {
        assertEquals(Optional.of("{\"a\":1}"), JsonExtractor.extract("Result {\"a\":1} ok"));
    }

    @Test
    void noJson() {
        assertEquals(Optional.empty(), JsonExtractor.extract("nothing here"));
    }
}
