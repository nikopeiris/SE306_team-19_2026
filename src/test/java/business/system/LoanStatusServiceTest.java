package business.system;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import org.mockito.Mock;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import org.mockito.junit.jupiter.MockitoExtension;

import business.lavsexceptions.LavsException;
import business.validation.ValidationException;
import business.validation.ValidationResult;
import dataaccess.systemadapters.MainframeAdapter;
import mainframe.client.Message;
import mainframe.client.Status;
import mainframe.client.messages.loan.AddCoborrower;
import mainframe.client.messages.loan.Retrieve;
import mainframe.client.messages.loan.UpdateStatus;
import mainframe.client.messages.loanCoborrower.List.Item;

@ExtendWith(MockitoExtension.class)
class LoanStatusServiceTest {
    @Mock
    private Loan loan;

    @Mock
    private LoanStatusService.BorrowerLookups lookups;

    @Mock
    private MainframeAdapter adapter;

    private LoanStatusService service;

    @BeforeEach
    void setUp() {
        service = new LoanStatusService(loan, lookups);
    }

    // ---- helpers ------------------------------------------------------

    private Retrieve stubLoan(String status) throws Exception {
        Retrieve retrieved = mock(Retrieve.class);
        when(retrieved.getStatus()).thenReturn(status);
        lenient().when(retrieved.getCustomerNumber()).thenReturn("C1");
        lenient().when(retrieved.getCustomerName()).thenReturn("Ann Lee");
        when(loan.retrieveLoan("L1")).thenReturn(retrieved);
        return retrieved;
    }

    private static Item coborrower(String name, String number, String type) {
        return new mainframe.client.messages.loanCoborrower.List().new Item(name, number, 7L, type);
    }

    private static ValidationException notReady(String reason) {
        return new ValidationException(new ValidationResult().addError("x", reason));
    }

    /** Makes the adapter fill a list message's results and report success. */
    private void stubList(Class<? extends Message> type, Object... items) throws Exception {
        lenient().when(adapter.send(any(type))).thenAnswer(invocation -> {
            Object message = invocation.getArgument(0);
            Field data = type.getDeclaredField("_data");
            data.setAccessible(true);
            @SuppressWarnings("unchecked")
            ArrayList<Object> target = (ArrayList<Object>) data.get(message);
            for (Object item : items) {
                target.add(item);
            }
            return new Status(0, null, 1L);
        });
    }

    // ---- allowed statuses ---------------------------------------------

    @Test
    void newLoansCanBeMovedToAnyOtherStatus() {
        assertEquals(List.of("Pending", "Active", "Defaulted", "Cancelled"),
                service.allowedStatuses(" New "));
    }

    @Test
    void pendingLoansCanBeMovedToAnyOtherStatus() {
        assertEquals(List.of("New", "Active", "Defaulted", "Cancelled"),
                service.allowedStatuses("Pending"));
    }

    @Test
    void activeAndDefaultedLoansCanBeMovedToAnyOtherStatus() {
        assertEquals(List.of("New", "Pending", "Defaulted", "Cancelled"),
                service.allowedStatuses("Active"));
        assertEquals(List.of("New", "Pending", "Active", "Cancelled"),
                service.allowedStatuses("Defaulted"));
    }

    @Test
    void cancelledUnknownOrMissingStatusesCannotBeChanged() {
        assertTrue(service.allowedStatuses("Cancelled").isEmpty());
        assertTrue(service.allowedStatuses("Nonsense").isEmpty());
        assertTrue(service.allowedStatuses(null).isEmpty());
    }

    // ---- changeStatus: input and transition rules ---------------------

    @Test
    void changeStatusRejectsBlankOrUnknownInputWithoutAskingTheMainframe() throws Exception {
        assertThrows(ValidationException.class, () -> service.changeStatus(" ", "Pending"));
        assertThrows(ValidationException.class, () -> service.changeStatus("L1", "Unknown"));
        verify(loan, never()).retrieveLoan(anyString());
    }

    @Test
    void changeStatusRefusesAChangeThatIsNotAllowed() throws Exception {
        stubLoan("Cancelled");

        ValidationException thrown = assertThrows(ValidationException.class,
                () -> service.changeStatus("L1", "Active"));

        assertEquals("A loan that is Cancelled cannot be changed to Active.",
                thrown.getResult().getError("status"));
        verify(loan, never()).updateLoanStatus(anyString(), anyString());
    }

    @Test
    void changeStatusCancelsWithoutCheckingBorrowers() throws Exception {
        stubLoan("New");
        UpdateStatus sent = mock(UpdateStatus.class);
        when(loan.updateLoanStatus("L1", "Cancelled")).thenReturn(sent);

        assertEquals(sent, service.changeStatus(" L1 ", " Cancelled "));

        verify(lookups, never()).coborrowers(anyString());
        verify(lookups, never()).checkContactDetails(anyString());
    }

    @Test
    void changeStatusSetsActiveDefaultedOrNewWithoutCheckingBorrowers() throws Exception {
        for (String target : List.of("Active", "Defaulted", "New")) {
            stubLoan("Pending");
            UpdateStatus sent = mock(UpdateStatus.class);
            when(loan.updateLoanStatus("L1", target)).thenReturn(sent);

            assertEquals(sent, service.changeStatus("L1", target));
        }
        verify(lookups, never()).coborrowers(anyString());
        verify(lookups, never()).checkContactDetails(anyString());
    }

    // ---- changeStatus: sending for checking ---------------------------

    @Test
    void changeStatusSendsAReadyLoanForChecking() throws Exception {
        stubLoan("New");
        when(lookups.coborrowers("L1")).thenReturn(
                new Item[] { coborrower("Bob Ray", "C2", AddCoborrower.TYPE_COBORROWER) });
        when(lookups.hasEmployer("C1")).thenReturn(true);
        UpdateStatus sent = mock(UpdateStatus.class);
        when(loan.updateLoanStatus("L1", "Pending")).thenReturn(sent);

        assertEquals(sent, service.changeStatus("L1", "Pending"));

        verify(lookups).checkContactDetails("C1");
        verify(lookups).checkContactDetails("C2");
    }

    @Test
    void aGuarantorCoversAnUnemployedBorrower() throws Exception {
        stubLoan("New");
        when(lookups.coborrowers("L1")).thenReturn(
                new Item[] { coborrower("Gus Fox", "C3", AddCoborrower.TYPE_GUARANTOR) });
        UpdateStatus sent = mock(UpdateStatus.class);
        when(loan.updateLoanStatus("L1", "Pending")).thenReturn(sent);

        assertEquals(sent, service.changeStatus("L1", "Pending"));

        verify(lookups, never()).hasEmployer(anyString());
    }

    @Test
    void changeStatusReportsEveryProblemAndLeavesTheStatusAlone() throws Exception {
        stubLoan("New");
        when(lookups.coborrowers("L1")).thenReturn(
                new Item[] { coborrower("Bob Ray", "C2", AddCoborrower.TYPE_COBORROWER) });
        doThrow(notReady("Does not have a mailing address.")).when(lookups)
                .checkContactDetails("C1");
        doThrow(notReady("Does not have a primary phone number.")).when(lookups)
                .checkContactDetails("C2");
        when(lookups.hasEmployer("C1")).thenReturn(false);

        ValidationException thrown = assertThrows(ValidationException.class,
                () -> service.changeStatus("L1", "Pending"));

        assertEquals(3, thrown.getResult().getErrors().size());
        assertEquals("Ann Lee (C1): Does not have a mailing address.",
                thrown.getResult().getError("C1"));
        assertEquals("Bob Ray (C2): Does not have a primary phone number.",
                thrown.getResult().getError("C2"));
        assertTrue(thrown.getResult().getError("guarantor").contains("guarantor"));
        verify(loan, never()).updateLoanStatus(anyString(), anyString());
    }

    @Test
    void mainframeProblemsWhileCheckingAreNotHiddenAsValidationErrors() throws Exception {
        stubLoan("New");
        doThrow(new LavsException(new Status(500, "Mainframe down", 1L))).when(lookups)
                .checkContactDetails("C1");

        assertThrows(LavsException.class, () -> service.changeStatus("L1", "Pending"));
    }

    // ---- the real lookups, answered through the adapter ---------------

    @Test
    void constructorRejectsANullAdapter() {
        assertThrows(IllegalArgumentException.class,
                () -> new LoanStatusService((MainframeAdapter) null));
    }

    @Test
    void realLookupsListTheCoborrowersOfALoan() throws Exception {
        stubList(mainframe.client.messages.loanCoborrower.List.class,
                coborrower("Bob Ray", "C2", AddCoborrower.TYPE_COBORROWER));

        Item[] items = LoanStatusService.lookupsFor(adapter).coborrowers("L1");

        assertEquals(1, items.length);
        assertEquals("C2", items[0].getCoborrowerNumber());
    }

    @Test
    void realLookupsReportAMainframeRefusal() throws Exception {
        when(adapter.send(any(mainframe.client.messages.loanCoborrower.List.class)))
                .thenReturn(new Status(500, "Refused", 1L));

        assertThrows(LavsException.class,
                () -> LoanStatusService.lookupsFor(adapter).coborrowers("L1"));
    }

    @Test
    void realLookupsKnowWhetherACustomerHasAnEmployer() throws Exception {
        mainframe.client.messages.customerEmployer.List employers =
                new mainframe.client.messages.customerEmployer.List();
        stubList(mainframe.client.messages.customerEmployer.List.class,
                employers.new Item(1L, "Acme", "Primary"));
        assertTrue(LoanStatusService.lookupsFor(adapter).hasEmployer("C1"));

        stubList(mainframe.client.messages.customerEmployer.List.class);
        assertEquals(false, LoanStatusService.lookupsFor(adapter).hasEmployer("C1"));
    }

    @Test
    void realLookupsUseTheMailingAddressAndContactRules() throws Exception {
        mainframe.client.messages.customerAddress.List addresses =
                new mainframe.client.messages.customerAddress.List();
        mainframe.client.messages.customerEmail.List emails =
                new mainframe.client.messages.customerEmail.List();
        stubList(mainframe.client.messages.customerAddress.List.class,
                addresses.new Item("Auckland", "NZ", 5L, "y", "n", "Home"));
        stubList(mainframe.client.messages.customerEmail.List.class,
                emails.new Item("a@example.com", 1L, "y", "Work"));
        LoanStatusService.lookupsFor(adapter).checkContactDetails("C1");   // no exception

        stubList(mainframe.client.messages.customerAddress.List.class);   // no addresses
        assertThrows(ValidationException.class,
                () -> LoanStatusService.lookupsFor(adapter).checkContactDetails("C1"));
    }

    // ---- DTO ----------------------------------------------------------

    @Test
    void loanSummaryKeepsEverythingButTheStatusWhenItChanges() {
        business.system.dto.LoanSummary before = new business.system.dto.LoanSummary(
                "L1", "C1", "Ann Lee", 100.0, "2026-11-01", "New");

        business.system.dto.LoanSummary after = before.withStatus("Pending");

        assertEquals("Pending", after.getStatus());
        assertEquals("L1", after.getLoanNumber());
        assertEquals("C1", after.getCustomerNumber());
        assertEquals("Ann Lee", after.getCustomerName());
        assertEquals(100.0, after.getPrincipal());
        assertEquals("2026-11-01", after.getStartDate());
        assertEquals("New", before.getStatus());
    }

    @Test
    void customerPassesStatusChangesToTheStatusService() throws Exception {
        MainframeAdapter customerAdapter = mock(MainframeAdapter.class);
        Customer customer = new Customer(customerAdapter);

        assertEquals(List.of("Cancelled"), customer.allowedLoanStatuses("Pending"));
        assertThrows(ValidationException.class, () -> customer.changeLoanStatus(" ", "Pending"));
    }
}
