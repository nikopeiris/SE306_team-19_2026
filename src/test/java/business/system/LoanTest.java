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

    @Test
    void constructorRejectsNullAdapter() {
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                () -> new Loan(null));

        assertEquals("Loan adapter cannot be null", exception.getMessage());
    }

    @Test
    void addLoanSendsTrimmedMessageAndReturnsIt() throws Exception {
        stubMailingAddress();
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
                futureStartDate);

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
                        LocalDate.now().plusDays(5).toString()));

        assertThrows(ValidationException.class, () ->
                loan.addLoan("C123", 1000.0, 5.0, 12,
                        "", "Monthly", "Monthly",
                        LocalDate.now().plusDays(30).toString(),
                        LocalDate.now().plusDays(5).toString()));

        verifyNoInteractions(adapter);
    }

    @Test
    void addLoanRejectsCustomerWithoutMailingAddress() throws Exception {
        when(adapter.send(any(List.class))).thenReturn(new Status(1L));

        LavsException exception = assertThrows(LavsException.class, () ->
                loan.addLoan("C123", 1000.0, 5.0, 12,
                        "Fixed", "Monthly", "Monthly",
                        LocalDate.now().plusDays(30).toString(),
                        LocalDate.now().plusDays(5).toString()));

        assertEquals("Does not have a mailing address. Please add a mailing address before adding a loan.",
                exception.getMessage());
    }

    @Test
    void addLoanRejectsInvalidPrincipalRateTermAndCodes() throws Exception {
        stubMailingAddress();

        ValidationException principalException = assertThrows(ValidationException.class, () ->
                loan.addLoan("C123", 0, 5.0, 12,
                        "Fixed", "Monthly", "Monthly",
                        LocalDate.now().plusDays(30).toString(),
                        LocalDate.now().plusDays(5).toString()));
        assertEquals("Loan principal must be greater than zero",
                principalException.getResult().getError("principal"));

        ValidationException annualRateException = assertThrows(ValidationException.class, () ->
                loan.addLoan("C123", 1000.0, -1.0, 12,
                        "Fixed", "Monthly", "Monthly",
                        LocalDate.now().plusDays(30).toString(),
                        LocalDate.now().plusDays(5).toString()));
        assertEquals("Annual rate cannot be negative",
                annualRateException.getResult().getError("annualRate"));

        ValidationException termException = assertThrows(ValidationException.class, () ->
                loan.addLoan("C123", 1000.0, 5.0, 0,
                        "Fixed", "Monthly", "Monthly",
                        LocalDate.now().plusDays(30).toString(),
                        LocalDate.now().plusDays(5).toString()));
        assertEquals("Term must be greater than zero",
                termException.getResult().getError("term"));

        ValidationException loanTypeException = assertThrows(ValidationException.class, () ->
                loan.addLoan("C123", 1000.0, 5.0, 12,
                        "BadType", "Monthly", "Monthly",
                        LocalDate.now().plusDays(30).toString(),
                        LocalDate.now().plusDays(5).toString()));
        assertEquals("Loan type must be a valid category code",
                loanTypeException.getResult().getError("loanType"));

        ValidationException paymentException = assertThrows(ValidationException.class, () ->
                loan.addLoan("C123", 1000.0, 5.0, 12,
                        "Fixed", "BadPeriod", "Monthly",
                        LocalDate.now().plusDays(30).toString(),
                        LocalDate.now().plusDays(5).toString()));
        assertEquals("Payment frequency must be a valid category code",
                paymentException.getResult().getError("paymentFrequency"));

        ValidationException compoundException = assertThrows(ValidationException.class, () ->
                loan.addLoan("C123", 1000.0, 5.0, 12,
                        "Fixed", "Monthly", "BadCompound",
                        LocalDate.now().plusDays(30).toString(),
                        LocalDate.now().plusDays(5).toString()));
        assertEquals("Compound type must be a valid category code",
                compoundException.getResult().getError("compoundType"));
    }

    @Test
    void addLoanRejectsPastOrInvalidDates() throws Exception {
        stubMailingAddress();
        String pastDate = LocalDate.now().minusDays(1).toString();

        ValidationException renewalDateException = assertThrows(ValidationException.class, () ->
                loan.addLoan("C123", 1000.0, 5.0, 12,
                        "Fixed", "Monthly", "Monthly",
                        pastDate,
                        LocalDate.now().plusDays(5).toString()));
        assertEquals("Renewal date must be today or later",
                renewalDateException.getResult().getError("renewalDate"));

        ValidationException startDateException = assertThrows(ValidationException.class, () ->
                loan.addLoan("C123", 1000.0, 5.0, 12,
                        "Fixed", "Monthly", "Monthly",
                        LocalDate.now().plusDays(30).toString(),
                        pastDate));
        assertEquals("Start date must be today or later",
                startDateException.getResult().getError("startDate"));

        ValidationException invalidDateFormatException = assertThrows(ValidationException.class, () ->
                loan.addLoan("C123", 1000.0, 5.0, 12,
                        "Fixed", "Monthly", "Monthly",
                        "31/12/2030",
                        "2026/10/14"));
        assertEquals("Renewal date must be in yyyy-MM-dd format",
                invalidDateFormatException.getResult().getError("renewalDate"));
        assertEquals("Start date must be in yyyy-MM-dd format",
                invalidDateFormatException.getResult().getError("startDate"));
    }

    @Test
    void addLoanPropagatesConnectionFailure() throws Exception {
        stubMailingAddress();
        when(adapter.send(any(Create.class))).thenThrow(new IOException("connection unavailable"));

        IOException exception = assertThrows(IOException.class, () ->
                loan.addLoan("C123", 1000.0, 5.0, 12,
                        "Fixed", "Monthly", "Monthly",
                        LocalDate.now().plusDays(30).toString(),
                        LocalDate.now().plusDays(5).toString()));

        assertEquals("connection unavailable", exception.getMessage());
    }

    @Test
    void addLoanThrowsLavsExceptionWhenMainframeRejectsRequest() throws Exception {
        stubMailingAddress();
        when(adapter.send(any(Create.class))).thenReturn(new Status(500, "Loan rejected", 42L));

        LavsException exception = assertThrows(LavsException.class, () ->
                loan.addLoan("C123", 1000.0, 5.0, 12,
                        "Fixed", "Monthly", "Monthly",
                        LocalDate.now().plusDays(30).toString(),
                        LocalDate.now().plusDays(5).toString()));

        assertEquals("Loan rejected", exception.getMessage());
    }

    @Test
    void addLoanToCustomerCheckValidatesAndReturnsTrue() throws Exception {
        stubMailingAddress();

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
        stubMailingAddress();

        loan.validateLoanDetails(
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
}
