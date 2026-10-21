package lavs.ui;

import java.util.LinkedHashMap;
import java.util.Map;

import business.system.Customer;

/** Hands out colours to loan statuses; a status keeps its colour for the whole session. */
final class StatusColors {

    /** Colours handed out to statuses (the awaiting-verification status is always orange). */
    private static final String[] COLORS = {
        "navy", "lavender", "crimson", "navy-soft", "lavender-soft", "crimson-soft"
    };
    /** Each status keeps the same colour in the table and in the bubbles. */
    private static final Map<String, Integer> COLOR_INDEX = new LinkedHashMap<>();

    private StatusColors() { }

    /** Colour name for a status; the same status always gets the same colour. */
    static String of(String status, Customer customerService) {
        if (customerService != null && customerService.isAwaitingVerification(status)) {
            return "orange";
        }
        String key = status.trim().toLowerCase();
        Integer index = COLOR_INDEX.get(key);
        if (index == null) {
            index = COLOR_INDEX.size();
            COLOR_INDEX.put(key, index);
        }
        return COLORS[index % COLORS.length];
    }
}
