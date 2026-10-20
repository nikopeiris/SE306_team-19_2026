package business.system;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.junit.jupiter.api.extension.ExtendWith;

import business.system.dto.LoanApplication;
import business.system.dto.LoanDetail;
import business.system.dto.LoanRow;
import business.validation.ValidationException;
import dataaccess.systemadapters.MainframeAdapter;
import mainframe.client.Message;
import mainframe.client.Status;
import mainframe.client.messages.loan.Create;
import mainframe.client.messages.loan.Retrieve;
import mainframe.client.messages.loan.UpdateStatus;

@ExtendWith(MockitoExtension.class)
class LoanDetailsServiceTest {
    @Mock
    private MainframeAdapter adapter;
    private LoanDetailsService service;

    @BeforeEach
    void setUp() {
        service = new LoanDetailsService(adapter);
    }

    @Test
    void constructorAndChoiceListsExposeFormOptions() {
        assertThrows(IllegalArgumentException.class, () -> new LoanDetailsService(null));
        assertEquals(List.of("Fixed", "Variable"), service.loanTypeOptions());
        assertEquals(List.of("Daily", "Weekly", "Fortnightly", "Monthly", "Yearly"),
                service.compoundTypeOptions());
        assertEquals(List.of("Weekly", "Fortnightly", "Monthly", "Yearly", "On renewal"),
                service.paymentFrequencyOptions());
    }

    @Test
    void invalidApplicationsAndIdentifiersAreRejectedBeforeAdapterCalls() {
        assertThrows(ValidationException.class, () -> service.addLoan(" ", null));
        assertThrows(ValidationException.class, () -> service.addLoan("C1",
                new LoanApplication("Bad", "", "-1", "x", "Bad", "Bad", false)));
        assertThrows(ValidationException.class, () -> service.loansFor(" "));
        assertThrows(ValidationException.class, () -> service.loanDetail(null));
        assertThrows(ValidationException.class, () -> service.repaymentSchedule(""));
        assertThrows(ValidationException.class, () -> service.paymentsFor("\t"));
        verifyNoInteractions(adapter);
    }

    @Test
    void addLoanHappyPathParsesFormAndSendsCreateRequest() throws Exception {
        stubLoanPrerequisites();

        Create created = service.addLoan(" C1 ", new LoanApplication(
                " Fixed ", "$1,200", "2", "5%", "Monthly", "Monthly", false));

        assertEquals("C1", created.getCustomerNumber());
        assertEquals(1200.0, created.getPrincipal());
        assertEquals(5.0, created.getRateAmount());
        assertEquals(50.0, created.getPaymentAmount());
        assertEquals(Create.RATETYPE_FIXED, created.getRateType());
        assertEquals(Create.PAYMENTFREQUENCY_MONTHLY, created.getPaymentFrequency());
        assertEquals(Create.RATEFREQUENCY_MONTHLY, created.getRateFrequency());
        verify(adapter).send(any(Create.class));
        verify(adapter).send(any(UpdateStatus.class));
    }

    @Test
    void addLoanInterestOnlyAndInvalidFormsCoverParsingBranches() throws Exception {
        stubLoanPrerequisites();

        Create created = service.addLoan("C1", new LoanApplication(
                null, "1000", "0.5", "4.5", "Daily", "Weekly", true));
        assertEquals(Create.RATETYPE_INTERESTONLY, created.getRateType());
        assertEquals(1000.0 / 26.0, created.getPaymentAmount());

        assertThrows(ValidationException.class, () -> service.addLoan("C1",
                new LoanApplication("Fixed", "abc", "1", "5", "Monthly", "Monthly", false)));
        assertThrows(ValidationException.class, () -> service.addLoan("C1",
                new LoanApplication("Fixed", "100", "0", "5", "Monthly", "Monthly", false)));
        assertThrows(ValidationException.class, () -> service.addLoan("C1",
                new LoanApplication("Fixed", "100", "1", "5", "Unknown", "Monthly", false)));
    }

    @Test
    void updateLoanEditableRecalculatesPaymentAndRenewalDate() throws Exception {
        stubEditableLoan(UpdateStatus.STATUS_PENDING, "2025-01-15");

        service.updateLoan(" L1 ", new LoanApplication(
                "Variable", "1,200", "2", "6%", "Yearly", "Monthly", false));

        ArgumentCaptor<Message> captor = ArgumentCaptor.forClass(Message.class);
        verify(adapter, atLeastOnce()).send(captor.capture());
        Message update = captor.getAllValues().stream()
                .filter(mainframe.client.messages.loan.Update.class::isInstance)
                .findFirst().orElseThrow();
        assertEquals("C1", get(update, "_customerNumber"));
        assertEquals("L1", get(update, "_loanNumber"));
        assertEquals(50.0, get(update, "_paymentAmount"));
        assertEquals("2027-01-15", get(update, "_renewalDate"));
        assertEquals("2025-01-15", get(update, "_startDate"));
        assertEquals(Create.RATETYPE_FLOATING, get(update, "_rateType"));
    }

    @Test
    void updateLoanRejectsNonEditableAndMalformedStartDate() throws Exception {
        stubEditableLoan(UpdateStatus.STATUS_ACTIVE, "2025-01-15");
        assertThrows(ValidationException.class, () -> service.updateLoan("L1",
                new LoanApplication("Fixed", "100", "1", "5", "Monthly", "Monthly", false)));

        stubEditableLoan(UpdateStatus.STATUS_NEW, "not-a-date");
        ValidationException exception = assertThrows(ValidationException.class, () ->
                service.updateLoan("L1", new LoanApplication(
                        "Fixed", "100", "1", "5", "Monthly", "Monthly", false)));
        assertNotNull(exception.getResult().getError("startDate"));
    }

    @Test
    void loanDetailMapsMainframeValuesAndSummaryTotals() throws Exception {
        when(adapter.send(any(Message.class))).thenAnswer(invocation -> {
            Message message = invocation.getArgument(0);
            if (message instanceof Retrieve retrieve) {
                set(retrieve, "_customerName", "Jane Doe");
                set(retrieve, "_customerNumber", "C1");
                set(retrieve, "_loanNumber", "L1");
                set(retrieve, "_rateType", Create.RATETYPE_FIXED);
                set(retrieve, "_rateAmount", 6.0);
                set(retrieve, "_rateFrequency", Create.RATEFREQUENCY_MONTHLY);
                set(retrieve, "_paymentAmount", 100.0);
                set(retrieve, "_paymentFrequency", Create.PAYMENTFREQUENCY_MONTHLY);
                set(retrieve, "_principal", 1200.0);
                set(retrieve, "_startDate", "2026-01-01");
                set(retrieve, "_renewalDate", "2028-01-01");
                set(retrieve, "_status", UpdateStatus.STATUS_PENDING);
            } else if (message instanceof mainframe.client.messages.loanSummary.Retrieve summary) {
                set(summary, "_totalInterestPaid", 40.0);
                set(summary, "_totalAmountPaid", 240.0);
                set(summary, "_numberOfPayments", 24L);
            }
            return new Status(1L);
        });

        LoanDetail detail = service.loanDetail(" L1 ");
        assertEquals("L1", detail.loanNumber());
        assertEquals("Jane Doe", detail.borrowerName());
        assertEquals("Fixed", detail.loanType());
        assertEquals("2 years", detail.term());
        assertEquals("Monthly", detail.compoundType());
        assertEquals("Monthly", detail.paymentFrequency());
        assertEquals(200.0, detail.totalPrincipalPaid());
        assertTrue(detail.editable());
        assertEquals("1200", detail.editForm().amount());
        assertEquals("2", detail.editForm().term());
    }

    @Test
    void loansPaymentsAndScheduleMapItemsAndTrimNumbers() throws Exception {
        when(adapter.send(any(Message.class))).thenAnswer(invocation -> {
            Message message = invocation.getArgument(0);
            if (message instanceof mainframe.client.messages.loan.List list) {
                add(list, list.new Item("L1", "bad", "2026-01-01", "New"));
            } else if (message instanceof mainframe.client.messages.loan.Retrieve retrieve) {
                set(retrieve, "_rateType", Create.RATETYPE_FLOATING);
            } else if (message instanceof mainframe.client.messages.loanPayment.List list) {
                add(list, list.new Item(25.0, "2026-02-01", 7L));
            } else if (message instanceof mainframe.client.messages.loanRepaymentSchedule.List list) {
                add(list, list.new Item(30.0, 80.0, "2026-03-01", 5.0, 2L, 25.0));
            }
            return new Status(1L);
        });

        List<LoanRow> loans = service.loansFor(" L1 ");
        assertEquals(1, loans.size());
        assertNull(loans.get(0).amount());
        assertEquals("Variable", loans.get(0).type());
        assertEquals(1, service.paymentsFor(" L1 ").size());
        assertEquals(1, service.repaymentSchedule(" L1 ").size());
    }

    @Test
    void paymentOperationsDelegateWithTrimmedLoanNumber() throws Exception {
        when(adapter.send(any(Message.class))).thenReturn(new Status(1L));
        service.addPayment(" L1 ", 25.0, "2026-01-01");
        service.updatePayment(" L1 ", 4L, 30.0, "2026-01-02");
        service.deletePayment(" L1 ", 4L);
        verify(adapter, times(4)).send(any(Message.class));
    }

    private static void set(Object target, String name, Object value) throws Exception {
        Field field = target.getClass().getDeclaredField(name);
        field.setAccessible(true);
        field.set(target, value);
    }

    private static void add(Object target, Object item) throws Exception {
        Field field = target.getClass().getDeclaredField("_data");
        field.setAccessible(true);
        @SuppressWarnings("unchecked")
        ArrayList<Object> data = (ArrayList<Object>) field.get(target);
        data.add(item);
    }

    private void stubLoanPrerequisites() throws Exception {
        when(adapter.send(any(Message.class))).thenAnswer(invocation -> {
            Message message = invocation.getArgument(0);
            if (message instanceof mainframe.client.messages.customerAddress.List addresses) {
                add(addresses, addresses.new Item("Auckland", "NZ", 1L, "y", "n", "Home"));
            } else if (message instanceof mainframe.client.messages.customerEmail.List emails) {
                add(emails, emails.new Item("jane@example.com", 1L, "y", "Home"));
            } else if (message instanceof mainframe.client.messages.customerEmployer.List employers) {
                add(employers, employers.new Item(1L, "Employer", "Primary"));
            } else if (message instanceof Create create) {
                set(create, "_loanNumber", "L100");
            }
            return new Status(1L);
        });
    }

    private void stubEditableLoan(String status, String startDate) throws Exception {
        when(adapter.send(any(Message.class))).thenAnswer(invocation -> {
            Message message = invocation.getArgument(0);
            if (message instanceof Retrieve retrieve) {
                set(retrieve, "_customerNumber", "C1");
                set(retrieve, "_status", status);
                set(retrieve, "_startDate", startDate);
            }
            return new Status(1L);
        });
    }

    private static Object get(Object target, String name) throws Exception {
        Field field = target.getClass().getDeclaredField(name);
        field.setAccessible(true);
        return field.get(target);
    }
}
