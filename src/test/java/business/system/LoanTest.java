package business.system;

import java.io.IOException;
import java.lang.reflect.Field;
import java.time.LocalDate;
import java.util.ArrayList;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import static org.mockito.ArgumentMatchers.any;
import org.mockito.Mock;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import org.mockito.junit.jupiter.MockitoExtension;

import business.lavsexceptions.LavsException;
import business.validation.LoanValidation;
import business.validation.ValidationException;
import dataaccess.systemadapters.MainframeAdapter;
import mainframe.client.Status;
import mainframe.client.messages.customerAddress.List;
import mainframe.client.messages.loan.Create;

@ExtendWith(MockitoExtension.class)
class LoanTest {
    @Mock
    private MainframeAdapter adapter;

    private Loan loan;

    @BeforeEach
    void setUp() {
        loan = new Loan(adapter);
    }

    /**
     * Stubs the address service to return one mailing address item.
     */
    private void stubMailingAddress() throws Exception {
        lenient().when(adapter.send(any(List.class))).thenAnswer(invocation -> {
            List listMessage = invocation.getArgument(0);
            Field dataField = List.class.getDeclaredField("_data");
            dataField.setAccessible(true);
            @SuppressWarnings("unchecked")
            ArrayList<List.Item> items = (ArrayList<List.Item>) dataField.get(listMessage);
            items.add(listMessage.new Item("Auckland", "NZ", 5L, "y", "n", "Home"));
            return new Status(0, null, 1L);
        });
    }

    /**
     * Stubs the email service to return a primary email so preReqCheck passes the email check.
     */
    private void stubPrimaryEmail() throws Exception {
        lenient().when(adapter.send(any(mainframe.client.messages.customerEmail.List.class))).thenAnswer(invocation -> {
            mainframe.client.messages.customerEmail.List listMessage = invocation.getArgument(0);
            Field dataField = mainframe.client.messages.customerEmail.List.class.getDeclaredField("_data");
            dataField.setAccessible(true);
            @SuppressWarnings("unchecked")
            ArrayList<mainframe.client.messages.customerEmail.List.Item> items =
                    (ArrayList<mainframe.client.messages.customerEmail.List.Item>) dataField.get(listMessage);
            items.add(listMessage.new Item("test@example.com", 1L, "y", "Work"));
            return new Status(0, null, 1L);
        });
    }

    /**
     * Stubs the employer service to return one employer so validateCustomerJobAndGuarantor passes.
     */
    private void stubEmployers() throws Exception {
        lenient().when(adapter.send(any(mainframe.client.messages.customerEmployer.List.class))).thenAnswer(invocation -> {
            mainframe.client.messages.customerEmployer.List listMessage = invocation.getArgument(0);
            Field dataField = mainframe.client.messages.customerEmployer.List.class.getDeclaredField("_data");
            dataField.setAccessible(true);
            @SuppressWarnings("unchecked")
            ArrayList<mainframe.client.messages.customerEmployer.List.Item> items =
                    (ArrayList<mainframe.client.messages.customerEmployer.List.Item>) dataField.get(listMessage);
            items.add(listMessage.new Item(1L, "Acme", "Primary"));
            return new Status(0, null, 1L);
        });
    }

    /**
     * Stubs all preReqCheck dependencies (address, email) so the flow succeeds.
     */
    private void stubPreReqCheck() throws Exception {
        stubMailingAddress();
        stubPrimaryEmail();
    }

    @Test
    void constructorRejectsNullAdapter() {
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                () -> new Loan(null));

        assertEquals("Loan adapter cannot be null", exception.getMessage());
    }

    @Test
    void addLoanSendsTrimmedMessageAndReturnsIt() throws Exception {
        stubPreReqCheck();
        stubEmployers();
        when(adapter.send(any(Create.class))).thenReturn(new Status(1L));

        String futureRenewalDate = LocalDate.now().plusDays(30).toString();
        String futureStartDate = LocalDate.now().plusDays(5).toString();

        Create result = loan.addLoan(
                "  C123  ",
                1200.0,
                5.5,
                12,
                " Fixed ",
                " Monthly ",
                " Monthly ",
                futureRenewalDate,
                futureStartDate,
                null);

        ArgumentCaptor<Create> captor = ArgumentCaptor.forClass(Create.class);
        verify(adapter).send(captor.capture());

        Create sentMessage = captor.getValue();
        assertEquals("C123", result.getCustomerNumber());
        assertEquals("C123", sentMessage.getCustomerNumber());
        assertEquals("Fixed", result.getRateType());
        assertEquals("Monthly", result.getPaymentFrequency());
        assertEquals("Monthly", result.getRateFrequency());
        assertEquals(futureRenewalDate, result.getRenewalDate());
        assertEquals(futureStartDate, result.getStartDate());
        assertEquals(100.0, result.getPaymentAmount());
    }

    @Test
    void addLoanRejectsMissingRequiredValuesBeforeCallingAdapter() {
        assertThrows(ValidationException.class, () ->
                loan.addLoan("", 1000.0, 5.0, 12,
                        "Fixed", "Monthly", "Monthly",
                        LocalDate.now().plusDays(30).toString(),
                        LocalDate.now().plusDays(5).toString(),
                        null));

        assertThrows(ValidationException.class, () ->
                loan.addLoan("C123", 1000.0, 5.0, 12,
                        "", "Monthly", "Monthly",
                        LocalDate.now().plusDays(30).toString(),
                        LocalDate.now().plusDays(5).toString(),
                        null));

        verifyNoInteractions(adapter);
    }

    @Test
    void addLoanRejectsCustomerWithoutMailingAddress() throws Exception {
        // Return an empty list (no items) so findMailingAddress returns null
        lenient().when(adapter.send(any(List.class))).thenReturn(new Status(0, null, 1L));
        stubPrimaryEmail();

        ValidationException exception = assertThrows(ValidationException.class, () ->
                loan.addLoan("C123", 1000.0, 5.0, 12,
                        "Fixed", "Monthly", "Monthly",
                        LocalDate.now().plusDays(30).toString(),
                        LocalDate.now().plusDays(5).toString(),
                        null));

        assertEquals("Does not have a mailing address.", exception.getResult().getError("mailingAddress"));
    }

    @Test
    void addLoanRejectsInvalidPrincipalRateTermAndCodes() throws Exception {
        // These validation checks happen before preReqCheck, so no adapter calls expected
        ValidationException principalException = assertThrows(ValidationException.class, () ->
                loan.addLoan("C123", 0, 5.0, 12,
                        "Fixed", "Monthly", "Monthly",
                        LocalDate.now().plusDays(30).toString(),
                        LocalDate.now().plusDays(5).toString(),
                        null));
        assertEquals("Loan principal must be greater than zero",
                principalException.getResult().getError("principal"));

        ValidationException annualRateException = assertThrows(ValidationException.class, () ->
                loan.addLoan("C123", 1000.0, -1.0, 12,
                        "Fixed", "Monthly", "Monthly",
                        LocalDate.now().plusDays(30).toString(),
                        LocalDate.now().plusDays(5).toString(),
                        null));
        assertEquals("Annual rate cannot be negative",
                annualRateException.getResult().getError("annualRate"));

        ValidationException termException = assertThrows(ValidationException.class, () ->
                loan.addLoan("C123", 1000.0, 5.0, 0,
                        "Fixed", "Monthly", "Monthly",
                        LocalDate.now().plusDays(30).toString(),
                        LocalDate.now().plusDays(5).toString(),
                        null));
        assertEquals("Term must be greater than zero",
                termException.getResult().getError("term"));

        ValidationException loanTypeException = assertThrows(ValidationException.class, () ->
                loan.addLoan("C123", 1000.0, 5.0, 12,
                        "BadType", "Monthly", "Monthly",
                        LocalDate.now().plusDays(30).toString(),
                        LocalDate.now().plusDays(5).toString(),
                        null));
        assertEquals("Loan type must be a valid category code",
                loanTypeException.getResult().getError("loanType"));

        ValidationException paymentException = assertThrows(ValidationException.class, () ->
                loan.addLoan("C123", 1000.0, 5.0, 12,
                        "Fixed", "BadPeriod", "Monthly",
                        LocalDate.now().plusDays(30).toString(),
                        LocalDate.now().plusDays(5).toString(),
                        null));
        assertEquals("Payment frequency must be a valid category code",
                paymentException.getResult().getError("paymentFrequency"));

        ValidationException compoundException = assertThrows(ValidationException.class, () ->
                loan.addLoan("C123", 1000.0, 5.0, 12,
                        "Fixed", "Monthly", "BadCompound",
                        LocalDate.now().plusDays(30).toString(),
                        LocalDate.now().plusDays(5).toString(),
                        null));
        assertEquals("Compound type must be a valid category code",
                compoundException.getResult().getError("compoundType"));

        verifyNoInteractions(adapter);
    }

    @Test
    void addLoanRejectsPastOrInvalidDates() throws Exception {
        String pastDate = LocalDate.now().minusDays(1).toString();

        // Date validation happens before preReqCheck, so no adapter calls needed
        ValidationException renewalDateException = assertThrows(ValidationException.class, () ->
                loan.addLoan("C123", 1000.0, 5.0, 12,
                        "Fixed", "Monthly", "Monthly",
                        pastDate,
                        LocalDate.now().plusDays(5).toString(),
                        null));
        assertEquals("Renewal date must be today or later",
                renewalDateException.getResult().getError("renewalDate"));

        ValidationException startDateException = assertThrows(ValidationException.class, () ->
                loan.addLoan("C123", 1000.0, 5.0, 12,
                        "Fixed", "Monthly", "Monthly",
                        LocalDate.now().plusDays(30).toString(),
                        pastDate,
                        null));
        assertEquals("Start date must be today or later",
                startDateException.getResult().getError("startDate"));

        ValidationException invalidDateFormatException = assertThrows(ValidationException.class, () ->
                loan.addLoan("C123", 1000.0, 5.0, 12,
                        "Fixed", "Monthly", "Monthly",
                        "31/12/2030",
                        "2026/10/14",
                        null));
        assertEquals("Renewal date must be in yyyy-MM-dd format",
                invalidDateFormatException.getResult().getError("renewalDate"));
        assertEquals("Start date must be in yyyy-MM-dd format",
                invalidDateFormatException.getResult().getError("startDate"));

        verifyNoInteractions(adapter);
    }

    @Test
    void addLoanPropagatesConnectionFailure() throws Exception {
        stubPreReqCheck();
        stubEmployers();
        when(adapter.send(any(Create.class))).thenThrow(new IOException("connection unavailable"));

        IOException exception = assertThrows(IOException.class, () ->
                loan.addLoan("C123", 1000.0, 5.0, 12,
                        "Fixed", "Monthly", "Monthly",
                        LocalDate.now().plusDays(30).toString(),
                        LocalDate.now().plusDays(5).toString(),
                        null));

        assertEquals("connection unavailable", exception.getMessage());
    }

    @Test
    void addLoanThrowsLavsExceptionWhenMainframeRejectsRequest() throws Exception {
        stubPreReqCheck();
        stubEmployers();
        when(adapter.send(any(Create.class))).thenReturn(new Status(500, "Loan rejected", 42L));

        LavsException exception = assertThrows(LavsException.class, () ->
                loan.addLoan("C123", 1000.0, 5.0, 12,
                        "Fixed", "Monthly", "Monthly",
                        LocalDate.now().plusDays(30).toString(),
                        LocalDate.now().plusDays(5).toString(),
                        null));

        assertEquals("Loan rejected", exception.getMessage());
    }

    @Test
    void addLoanToCustomerCheckValidatesAndReturnsTrue() throws Exception {
        assertTrue(loan.addLoanToCustomerCheck(
                "C123",
                1000.0,
                5.0,
                12,
                "Fixed",
                "Monthly",
                "Monthly",
                LocalDate.now().plusDays(30).toString(),
                LocalDate.now().plusDays(5).toString()));
    }

    @Test
    void validateLoanDetailsAcceptsValidValues() throws Exception {
        LoanValidation.validateLoanDetails(
                "C123",
                1000.0,
                5.0,
                12,
                "Fixed",
                "Monthly",
                "Monthly",
                LocalDate.now().plusDays(30).toString(),
                LocalDate.now().plusDays(5).toString());
    }

    @Test
    void retrieveLoansSendsListMessageAndReturnsItems() throws Exception {
        when(adapter.send(any(mainframe.client.messages.loan.List.class)))
                .thenReturn(new Status(1));

        mainframe.client.messages.loan.List.Item[] result = loan.retrieveLoans("C123", 1L, 25L);

        ArgumentCaptor<mainframe.client.messages.loan.List> captor =
                ArgumentCaptor.forClass(mainframe.client.messages.loan.List.class);
        verify(adapter).send(captor.capture());
        assertEquals(1L, captor.getValue().getPage());
        assertEquals(0, result.length);
    }

    @Test
    void retrieveRepaymentScheduleSendsListMessageAndReturnsItems() throws Exception {
        when(adapter.send(any(mainframe.client.messages.loanRepaymentSchedule.List.class)))
                .thenReturn(new Status(1));

        mainframe.client.messages.loanRepaymentSchedule.List.Item[] result =
                loan.retrieveRepaymentSchedule("L123", 1L, 25L);

        ArgumentCaptor<mainframe.client.messages.loanRepaymentSchedule.List> captor =
                ArgumentCaptor.forClass(mainframe.client.messages.loanRepaymentSchedule.List.class);
        verify(adapter).send(captor.capture());
        assertEquals(1L, captor.getValue().getPage());
        assertEquals(0, result.length);
    }

    @Test
    void retrieveLoanSummarySendsRetrieveMessageAndReturnsIt() throws Exception {
        when(adapter.send(any(mainframe.client.messages.loanSummary.Retrieve.class)))
                .thenReturn(new Status(1));

        mainframe.client.messages.loanSummary.Retrieve result = loan.retrieveLoanSummary("L123");

        ArgumentCaptor<mainframe.client.messages.loanSummary.Retrieve> captor =
                ArgumentCaptor.forClass(mainframe.client.messages.loanSummary.Retrieve.class);
        verify(adapter).send(captor.capture());
        assertEquals(result, captor.getValue());
    }

    @Test
    void retrieveLoanSendsRetrieveMessageAndReturnsIt() throws Exception {
        when(adapter.send(any(mainframe.client.messages.loan.Retrieve.class)))
                .thenReturn(new Status(1));

        mainframe.client.messages.loan.Retrieve result = loan.retrieveLoan("L123");

        ArgumentCaptor<mainframe.client.messages.loan.Retrieve> captor =
                ArgumentCaptor.forClass(mainframe.client.messages.loan.Retrieve.class);
        verify(adapter).send(captor.capture());
        assertEquals(result, captor.getValue());
    }

    @Test
    void updateLoanSendsTrimmedMessageAndReturnsIt() throws Exception {
        when(adapter.send(any(mainframe.client.messages.loan.Update.class)))
                .thenReturn(new Status(1));

        String renewalDate = LocalDate.now().plusDays(30).toString();
        String startDate = LocalDate.now().plusDays(5).toString();

        mainframe.client.messages.loan.Update result = loan.updateLoan(
                "  C123  ",
                "  L456  ",
                150.0,
                " Monthly ",
                5000.0,
                4.5,
                " Monthly ",
                " Fixed ",
                renewalDate,
                startDate);

        ArgumentCaptor<mainframe.client.messages.loan.Update> captor =
                ArgumentCaptor.forClass(mainframe.client.messages.loan.Update.class);
        verify(adapter).send(captor.capture());
        assertEquals("C123", captor.getValue().getCustomerNumber());
        assertEquals("L456", captor.getValue().getLoanNumber());
        assertEquals(150.0, captor.getValue().getPaymentAmount());
        assertEquals("Monthly", captor.getValue().getPaymentFrequency());
        assertEquals(5000.0, captor.getValue().getPrincipal());
        assertEquals(4.5, captor.getValue().getRateAmount());
        assertEquals("Monthly", captor.getValue().getRateFrequency());
        assertEquals("Fixed", captor.getValue().getRateType());
        assertEquals(renewalDate, captor.getValue().getRenewalDate());
        assertEquals(startDate, captor.getValue().getStartDate());
        assertEquals(result, captor.getValue());
    }

    @Test
    void updateLoanRejectsBlankRequiredFields() {
        ValidationException exception = assertThrows(ValidationException.class, () ->
                loan.updateLoan(
                        "", " ", 100.0, "", 1000.0, 5.0, "", "", "", ""));

        assertEquals("Customer number cannot be null or empty",
                exception.getResult().getError("customerNumber"));
        assertEquals("Loan number cannot be null or empty",
                exception.getResult().getError("loanNumber"));
        assertEquals("Payment frequency cannot be null or empty",
                exception.getResult().getError("paymentFrequency"));
        assertEquals("Rate frequency cannot be null or empty",
                exception.getResult().getError("rateFrequency"));
        assertEquals("Rate type cannot be null or empty",
                exception.getResult().getError("rateType"));
        assertEquals("Renewal date cannot be null or empty",
                exception.getResult().getError("renewalDate"));
        assertEquals("Start date cannot be null or empty",
                exception.getResult().getError("startDate"));

        verifyNoInteractions(adapter);
    }

    @Test
    void updateLoanRejectsInvalidAmountsCodesAndDates() {
        String pastDate = LocalDate.now().minusDays(1).toString();

        ValidationException exception = assertThrows(ValidationException.class, () ->
                loan.updateLoan(
                        "C123",
                        "L456",
                        0.0,
                        "BadFreq",
                        -10.0,
                        -1.0,
                        "BadRateFreq",
                        "BadRateType",
                        pastDate,
                        "invalid-date"));

        assertEquals("Payment amount must be greater than zero",
                exception.getResult().getError("paymentAmount"));
        assertEquals("Loan principal must be greater than zero",
                exception.getResult().getError("principal"));
        assertEquals("Rate amount cannot be negative",
                exception.getResult().getError("rateAmount"));
        assertEquals("Payment frequency must be a valid category code",
                exception.getResult().getError("paymentFrequency"));
        assertEquals("Rate frequency must be a valid category code",
                exception.getResult().getError("rateFrequency"));
        assertEquals("Rate type must be a valid category code",
                exception.getResult().getError("rateType"));
        assertEquals("Renewal date must be today or later",
                exception.getResult().getError("renewalDate"));
        assertEquals("Start date must be in yyyy-MM-dd format",
                exception.getResult().getError("startDate"));

        verifyNoInteractions(adapter);
    }

    @Test
    void updateLoanThrowsLavsExceptionWhenMainframeRejects() throws Exception {
        when(adapter.send(any(mainframe.client.messages.loan.Update.class)))
                .thenReturn(new Status(500, "Update rejected", 99L));

        String renewalDate = LocalDate.now().plusDays(30).toString();
        String startDate = LocalDate.now().plusDays(5).toString();

        LavsException exception = assertThrows(LavsException.class, () ->
                loan.updateLoan(
                        "C123",
                        "L456",
                        150.0,
                        "Monthly",
                        5000.0,
                        4.5,
                        "Monthly",
                        "Fixed",
                        renewalDate,
                        startDate));

        assertEquals("Update rejected", exception.getMessage());
    }

    @Test
    void validateCustomerJobAndGuarantorSucceedsWhenCustomerHasJobsWithoutGuarantor() throws Exception {
        when(adapter.send(any(mainframe.client.messages.customerEmployer.List.class))).thenAnswer(invocation -> {
            mainframe.client.messages.customerEmployer.List listMessage = invocation.getArgument(0);
            Field dataField = mainframe.client.messages.customerEmployer.List.class.getDeclaredField("_data");
            dataField.setAccessible(true);
            @SuppressWarnings("unchecked")
            ArrayList<mainframe.client.messages.customerEmployer.List.Item> items =
                    (ArrayList<mainframe.client.messages.customerEmployer.List.Item>) dataField.get(listMessage);
            items.add(listMessage.new Item(1L, "Acme", "Primary"));
            return new Status(0, null, 1L);
        });

        LoanValidation.validateCustomerJobAndGuarantor("C123", null, adapter);
        LoanValidation.validateCustomerJobAndGuarantor("C123", "", adapter);
    }

    @Test
    void validateCustomerJobAndGuarantorThrowsWhenNoJobsAndNoGuarantor() throws Exception {
        when(adapter.send(any(mainframe.client.messages.customerEmployer.List.class)))
                .thenReturn(new Status(0, null, 1L));

        ValidationException exceptionNull = assertThrows(ValidationException.class, () ->
                LoanValidation.validateCustomerJobAndGuarantor("C123", null, adapter));
        assertEquals("Guarantor is required when customer has no employment.",
                exceptionNull.getResult().getError("guarantorId"));

        ValidationException exceptionBlank = assertThrows(ValidationException.class, () ->
                LoanValidation.validateCustomerJobAndGuarantor("C123", "   ", adapter));
        assertEquals("Guarantor is required when customer has no employment.",
                exceptionBlank.getResult().getError("guarantorId"));
    }

    @Test
    void validateCustomerJobAndGuarantorSucceedsWithValidGuarantorWhenNoJobs() throws Exception {
        when(adapter.send(any(mainframe.client.messages.customerEmployer.List.class)))
                .thenReturn(new Status(0, null, 1L));
        when(adapter.send(any(mainframe.client.messages.customer.Retrieve.class)))
                .thenReturn(new Status(1));

        LoanValidation.validateCustomerJobAndGuarantor("C123", "G999", adapter);
    }

    @Test
    void validateCustomerJobAndGuarantorThrowsWhenGuarantorIsInvalid() throws Exception {
        when(adapter.send(any(mainframe.client.messages.customerEmployer.List.class)))
                .thenReturn(new Status(0, null, 1L));
        when(adapter.send(any(mainframe.client.messages.customer.Retrieve.class)))
                .thenReturn(new Status(404, "Customer not found", 99L));

        ValidationException exception = assertThrows(ValidationException.class, () ->
                LoanValidation.validateCustomerJobAndGuarantor("C123", "G999", adapter));
        assertEquals("Guarantor must be a valid customer.",
                exception.getResult().getError("guarantorId"));
    }
}
