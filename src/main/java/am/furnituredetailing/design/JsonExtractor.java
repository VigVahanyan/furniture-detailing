package am.furnituredetailing.design;

import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Pulls one JSON object out of a model reply: whole reply, a ```json fence, or first '{' to last '}'.
 */
public final class JsonExtractor {

    private static final Pattern FENCE = Pattern.compile("```(?:json)?\\s*(\\{[\\s\\S]*?})\\s*```");

    private JsonExtractor() {
    }

    public static Optional<String> extract(String text) {
        if (text == null) return Optional.empty();
        String t = text.strip();
        if (t.startsWith("{") && t.endsWith("}")) return Optional.of(t);
        Matcher m = FENCE.matcher(t);
        if (m.find()) return Optional.of(m.group(1));
        int a = t.indexOf('{'), b = t.lastIndexOf('}');
        return a >= 0 && b > a ? Optional.of(t.substring(a, b + 1)) : Optional.empty();
    }
}
