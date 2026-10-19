package lavs.ui;

import java.text.DecimalFormat;
import java.text.NumberFormat;
import java.util.Locale;

/** Display-only helpers: turns values from the business layer into text for labels and cells. */
final class LoanFormat {
    private static final NumberFormat MONEY =
            NumberFormat.getCurrencyInstance(Locale.forLanguageTag("en-NZ"));
    private static final DecimalFormat PERCENT = new DecimalFormat("0.##");

    private LoanFormat() {}

    static String money(Double value) {
        return value == null ? "-" : MONEY.format(value);
    }

    static String percent(Double value) {
        return value == null ? "-" : PERCENT.format(value) + "%";
    }

    static String text(Object value) {
        if (value == null) {
            return "-";
        }
        String s = value.toString();
        return s.isBlank() ? "-" : s;
    }
}
