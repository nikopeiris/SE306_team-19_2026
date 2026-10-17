package business.system.dto;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class CustomerSummaryTest {

    @Test
    void constructorTrimsEveryField() {
        CustomerSummary summary = new CustomerSummary(" C123 ", " Jane ", " Smith ");

        assertEquals("C123", summary.getNumber());
        assertEquals("Jane", summary.getFirstName());
        assertEquals("Smith", summary.getFamilyName());
        assertEquals("Jane Smith", summary.getFullName());
    }

    @Test
    void constructorTreatsMissingFieldsAsEmpty() {
        CustomerSummary summary = new CustomerSummary(null, null, null);

        assertEquals("", summary.getNumber());
        assertEquals("", summary.getFirstName());
        assertEquals("", summary.getFamilyName());
        assertEquals("", summary.getFullName());
    }

    @Test
    void fullNameHasNoStraySpaceWhenOneNameIsMissing() {
        assertEquals("Jane", new CustomerSummary("C1", "Jane", null).getFullName());
        assertEquals("Smith", new CustomerSummary("C1", null, "Smith").getFullName());
    }
}
