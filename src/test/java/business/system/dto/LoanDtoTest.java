package business.system.dto;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

class LoanDtoTest {
    @Test
    void recordsExposeAllFields() {
        LoanApplication application = new LoanApplication("Fixed", "$10", "2", "5%", "Monthly",
                "Weekly", true);
        assertEquals("Fixed", application.loanType());
        assertTrue(application.interestOnly());

        LoanDetail detail = new LoanDetail("L1", "Jane", "C1", "Fixed", "2 years", "Monthly",
                10.0, "Weekly", false, 5.0, 1.0, "2028-01-01", 2.0, 8.0,
                "New", true, application);
        assertEquals("L1", detail.loanNumber());
        assertEquals(application, detail.editForm());

        assertEquals(new LoanRow("L1", "Fixed", 10.0, "2026-01-01", "New"),
                new LoanRow("L1", "Fixed", 10.0, "2026-01-01", "New"));
        assertEquals(new PaymentRow(3L, 20.0, "2026-02-01"),
                new PaymentRow(3L, 20.0, "2026-02-01"));
        assertEquals(new RepaymentRow(1L, "2026-01-01", 20.0, 15.0, 5.0, 85.0),
                new RepaymentRow(1L, "2026-01-01", 20.0, 15.0, 5.0, 85.0));
    }

    @Test
    void loanSummaryCanChangeStatusWithoutMutatingOriginal() {
        LoanSummary original = new LoanSummary("L1", "C1", "Jane", 100.0, "2026-01-01", "New");
        LoanSummary changed = original.withStatus("Paid");
        assertEquals("New", original.getStatus());
        assertEquals("Paid", changed.getStatus());
        assertEquals("L1", changed.getLoanNumber());
        assertEquals(100.0, changed.getPrincipal());
    }

    @Test
    void loanListResultRetainsResultValues() {
        LoanSummary loan = new LoanSummary("L1", "C1", "Jane", 1.0, null, "New");
        LoanListResult result = new LoanListResult(java.util.List.of(loan), 2, "failure");
        assertEquals(java.util.List.of(loan), result.getLoans());
        assertEquals(2, result.getFailedCustomers());
        assertEquals("failure", result.getFirstError());
    }
}
