package business.system.dto;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;

import org.junit.jupiter.api.Test;

class CustomerDetailsDtoTest {
    @Test
    void emptyCustomerHasNewPersonalDetailsAndEmptyCollections() {
        CustomerDetails details = CustomerDetails.empty();

        assertTrue(details.personal().isNew());
        assertTrue(details.addresses().isEmpty());
        assertTrue(details.phones().isEmpty());
        assertTrue(details.emails().isEmpty());
        assertTrue(details.employers().isEmpty());
    }

    @Test
    void customerDetailsRejectsMissingPersonalAndCopiesLists() {
        assertThrows(IllegalArgumentException.class,
                () -> new CustomerDetails(null, null, null, null, null));

        List<CustomerDetails.Address> addresses = new java.util.ArrayList<>();
        CustomerDetails details = new CustomerDetails(
                new CustomerDetails.Personal(" C1 ", " Ms ", " Jane ", " Doe ",
                        " 2000-01-01 ", " NZ ", 4L, " Active "),
                addresses, null, null, null);
        addresses.add(null);

        assertEquals(List.of(), details.addresses());
        assertEquals("C1", details.personal().number());
        assertEquals("Jane", details.personal().firstName());
        assertFalse(details.personal().isNew());
    }

    @Test
    void nestedCustomerDetailsValuesTrimText() {
        CustomerDetails.Address address = new CustomerDetails.Address(1L, " Home ",
                null, " Line 1 ", null, " Suburb ", " City ", " NZ ", " 1010 ", true, false);
        CustomerDetails.Phone phone = new CustomerDetails.Phone(2L, " Mobile ",
                null, " +64 ", " 123 ", true, true);
        CustomerDetails.Email email = new CustomerDetails.Email(3L, " Work ",
                null, " jane@example.com ", true);
        CustomerDetails.Employer employer = new CustomerDetails.Employer(4L, " Primary ",
                " Acme ", " Engineer ", 1.0);

        assertEquals("Home", address.type());
        assertEquals("", address.otherType());
        assertEquals("Line 1", address.line1());
        assertEquals("Mobile", phone.type());
        assertEquals("+64", phone.countryCode());
        assertEquals("jane@example.com", email.address());
        assertEquals("Acme", employer.name());
        assertEquals("Engineer", employer.position());
    }

    @Test
    void saveResultCopiesProblemsAndReportsWhetherProblemsExist() {
        List<String> problems = new java.util.ArrayList<>(List.of("email"));
        CustomerDetails.SaveResult result = new CustomerDetails.SaveResult("C1", problems);
        problems.add("phone");

        assertTrue(result.hasProblems());
        assertEquals(List.of("email"), result.problems());
        assertFalse(new CustomerDetails.SaveResult("C1", null).hasProblems());
    }

    @Test
    void otherDtosExposeValuesAndCleanDocuments() {
        CustomerDocument document = new CustomerDocument(1L, " Passport ", " file.pdf ");
        LoanApplication application = new LoanApplication("Fixed", "100", "2", "5",
                "Monthly", "Monthly", false);
        LoanDetail detail = new LoanDetail("L1", "Jane", "C1", "Fixed", "2",
                "Monthly", 100.0, "Monthly", false, 5.0, 50.0, "2030-01-01",
                10.0, 40.0, "New", true, application);
        LoanRow loan = new LoanRow("L1", "Fixed", 100.0, "2026-01-01", "New");
        RepaymentRow repayment = new RepaymentRow(1L, "2026-02-01", 50.0, 40.0, 10.0, 60.0);
        PaymentRow payment = new PaymentRow(2L, 25.0, "2026-03-01");

        assertEquals("Passport", document.type());
        assertEquals("file.pdf", document.fileName());
        assertEquals("Fixed", application.loanType());
        assertEquals("L1", detail.loanNumber());
        assertEquals("New", loan.status());
        assertEquals(1L, repayment.paymentNumber());
        assertEquals(25.0, payment.amountPaid());
        assertEquals("", new CustomerDocument(null, null, null).type());
    }
}
