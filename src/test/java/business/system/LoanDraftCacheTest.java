package business.system;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

import business.system.dto.LoanApplication;

class LoanDraftCacheTest {
    private final LoanDraftCache cache = new LoanDraftCache();

    @Test
    void savesAndRetrievesDraftByTrimmedCustomerNumber() {
        LoanApplication draft = new LoanApplication(
                "Fixed", "$100", "2", "5%", "Monthly", "Monthly", false);

        cache.save(" C1 ", draft);

        assertEquals(draft, cache.get("C1"));
        assertEquals(draft, cache.get(" C1 "));
    }

    @Test
    void clearRemovesOnlyTheSelectedCustomerDraft() {
        LoanApplication first = new LoanApplication("Fixed", "100", "1", "5",
                "Monthly", "Monthly", false);
        LoanApplication second = new LoanApplication("Variable", "200", "2", "6",
                "Yearly", "Weekly", true);
        cache.save("C1", first);
        cache.save("C2", second);

        cache.clear("C1");

        assertNull(cache.get("C1"));
        assertEquals(second, cache.get("C2"));
    }

    @Test
    void rejectsInvalidSavedDraftInputs() {
        assertThrows(IllegalArgumentException.class, () -> cache.save(" ", null));
        assertNull(cache.get(null));
        cache.clear(null);
    }
}
