package business.system;

import java.io.IOException;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import org.mockito.InOrder;
import org.mockito.Mock;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import org.mockito.junit.jupiter.MockitoExtension;

import business.lavsexceptions.LavsException;
import business.system.dto.LoanCoborrower;
import business.validation.ValidationException;
import business.validation.ValidationResult;
import dataaccess.systemadapters.MainframeAdapter;
import mainframe.client.messages.loan.Retrieve;
import mainframe.client.messages.loanCoborrower.List.Item;

@ExtendWith(MockitoExtension.class)
class LoanCoborrowerServiceTest {
    private static final long PAGE_SIZE = 100L;

    @Mock
    private Loan loan;

    @Mock
    private Coborrower coborrowerService;

    @Mock
    private LoanCoborrowerService.ReadinessCheck readiness;

    private LoanCoborrowerService service;

    @BeforeEach
    void setUp() {
        service = new LoanCoborrowerService(loan, coborrowerService, readiness);
    }

    // ---- helpers ------------------------------------------------------

    private void stubLoan(String status) throws Exception {
        Retrieve retrieved = mock(Retrieve.class);
        when(retrieved.getStatus()).thenReturn(status);
        lenient().when(retrieved.getCustomerNumber()).thenReturn("C1");
        when(loan.retrieveLoan("L1")).thenReturn(retrieved);
    }

    private static Item item(long id, String number, String name, String type) {
        return new mainframe.client.messages.loanCoborrower.List()
                .new Item(name, number, id, type);
    }

    private static LoanCoborrower bob() {
        return new LoanCoborrower(7L, "C2", "Bob Ray", "Coborrower");
    }

    private static ValidationException notReady(String reason) {
        return new ValidationException(new ValidationResult().addError("x", reason));
    }

    // ---- basics -------------------------------------------------------

    @Test
    void onlyNewLoansCanHaveTheirCoborrowersChanged() {
        assertTrue(LoanCoborrowerService.canChange("New"));
        assertTrue(LoanCoborrowerService.canChange(" New "));
        assertFalse(LoanCoborrowerService.canChange("Pending"));
        assertFalse(LoanCoborrowerService.canChange(null));
    }

    @Test
    void offersTheTwoTypesTheMainframeAccepts() {
        assertEquals(List.of("Coborrower", "Guarantor"), LoanCoborrowerService.TYPES);
    }

    @Test
    void constructorRejectsANullAdapter() {
        assertThrows(IllegalArgumentException.class,
                () -> new LoanCoborrowerService((MainframeAdapter) null));
    }

    // ---- list ---------------------------------------------------------

    @Test
    void listCoborrowersMapsEveryRow() throws Exception {
        when(coborrowerService.retrieveCoborrowers("L1", 1L, PAGE_SIZE)).thenReturn(
                new Item[] { item(7, "C2", "Bob Ray", "Coborrower"),
                             item(8, "C3", "Gus Fox", "Guarantor") });

        List<LoanCoborrower> result = service.listCoborrowers("L1");

        assertEquals(2, result.size());
        assertEquals(new LoanCoborrower(7L, "C2", "Bob Ray", "Coborrower"), result.get(0));
        assertEquals("Guarantor", result.get(1).type());
    }

    @Test
    void listCoborrowersKeepsPagingWhilePagesAreFull() throws Exception {
        Item[] fullPage = new Item[(int) PAGE_SIZE];
        for (int i = 0; i < fullPage.length; i++) {
            fullPage[i] = item(i, "C" + i, "Name " + i, "Coborrower");
        }
        when(coborrowerService.retrieveCoborrowers("L1", 1L, PAGE_SIZE)).thenReturn(fullPage);
        when(coborrowerService.retrieveCoborrowers("L1", 2L, PAGE_SIZE)).thenReturn(
                new Item[] { item(999, "CLAST", "Last", "Guarantor") });

        List<LoanCoborrower> result = service.listCoborrowers("L1");

        assertEquals(101, result.size());
        assertEquals("CLAST", result.get(100).customerNumber());
    }

    @Test
    void listCoborrowersStopsOnAnEmptyOrMissingPage() throws Exception {
        when(coborrowerService.retrieveCoborrowers("L1", 1L, PAGE_SIZE)).thenReturn(new Item[0]);
        assertTrue(service.listCoborrowers("L1").isEmpty());

        when(coborrowerService.retrieveCoborrowers("L2", 1L, PAGE_SIZE)).thenReturn(null);
        assertTrue(service.listCoborrowers("L2").isEmpty());
    }

    // ---- add ----------------------------------------------------------

    @Test
    void addCoborrowerAddsAnEligibleCustomer() throws Exception {
        stubLoan("New");
        when(coborrowerService.retrieveCoborrowers("L1", 1L, PAGE_SIZE)).thenReturn(
                new Item[] { item(8, "C3", "Gus Fox", "Guarantor") });

        service.addCoborrower(" L1 ", " C2 ", " Guarantor ");

        verify(coborrowerService).addCoborrower("C2", "L1", "Guarantor");
    }

    @Test
    void addCoborrowerStopsWhenTheDetailsAreInvalid() throws Exception {
        doThrow(notReady("bad")).when(coborrowerService)
                .validateCoborrowerDetails("", "L1", "Coborrower");

        assertThrows(ValidationException.class,
                () -> service.addCoborrower("L1", "", "Coborrower"));
        verify(loan, never()).retrieveLoan(anyString());
    }

    @Test
    void addCoborrowerRefusesOnceTheLoanIsNoLongerNew() throws Exception {
        stubLoan("Pending");

        ValidationException thrown = assertThrows(ValidationException.class,
                () -> service.addCoborrower("L1", "C2", "Coborrower"));

        assertTrue(thrown.getResult().getError("status").contains("only be changed while"));
        verify(coborrowerService, never()).addCoborrower(anyString(), anyString(), anyString());
    }

    @Test
    void addCoborrowerRefusesThePrimaryBorrower() throws Exception {
        stubLoan("New");

        ValidationException thrown = assertThrows(ValidationException.class,
                () -> service.addCoborrower("L1", "C1", "Coborrower"));

        assertEquals("The primary borrower cannot also be a co-borrower.",
                thrown.getResult().getError("customerNumber"));
    }

    @Test
    void addCoborrowerRefusesSomeoneAlreadyOnTheLoan() throws Exception {
        stubLoan("New");
        when(coborrowerService.retrieveCoborrowers("L1", 1L, PAGE_SIZE)).thenReturn(
                new Item[] { item(7, "C2", "Bob Ray", "Coborrower") });

        ValidationException thrown = assertThrows(ValidationException.class,
                () -> service.addCoborrower("L1", "C2", "Guarantor"));

        assertEquals("Customer C2 is already on this loan.",
                thrown.getResult().getError("customerNumber"));
    }

    @Test
    void addCoborrowerNamesTheCustomerWhoIsNotReady() throws Exception {
        stubLoan("New");
        when(coborrowerService.retrieveCoborrowers("L1", 1L, PAGE_SIZE)).thenReturn(null);
        doThrow(notReady("Does not have a mailing address.")).when(coborrowerService)
                .addCoborrower("C2", "L1", "Coborrower");

        ValidationException thrown = assertThrows(ValidationException.class,
                () -> service.addCoborrower("L1", "C2", "Coborrower"));

        assertEquals("Customer C2: Does not have a mailing address.",
                thrown.getResult().getError("customerNumber"));
    }

    // ---- change type --------------------------------------------------

    @Test
    void changeTypeRemovesThenAddsAgainWithTheNewType() throws Exception {
        stubLoan("New");

        service.changeType("L1", bob(), "Guarantor");

        InOrder order = inOrder(readiness, coborrowerService);
        order.verify(readiness).check("C2");
        order.verify(coborrowerService).deleteCoborrower(7L, "L1");
        order.verify(coborrowerService).addCoborrower("C2", "L1", "Guarantor");
    }

    @Test
    void changeTypeRejectsAMissingSelectionAnInvalidTypeOrNoChange() throws Exception {
        assertThrows(ValidationException.class, () -> service.changeType("L1", null, "Guarantor"));
        assertThrows(ValidationException.class, () -> service.changeType("L1", bob(), "Boss"));
        assertThrows(ValidationException.class, () -> service.changeType("L1", bob(), null));
        assertThrows(ValidationException.class,
                () -> service.changeType("L1", bob(), "Coborrower"));
        assertThrows(ValidationException.class, () -> service.changeType(" ", bob(), "Guarantor"));
        verify(coborrowerService, never()).deleteCoborrower(anyLong(), anyString());
    }

    @Test
    void changeTypeRefusesOnceTheLoanIsNoLongerNew() throws Exception {
        stubLoan("Pending");

        assertThrows(ValidationException.class, () -> service.changeType("L1", bob(), "Guarantor"));
        verify(coborrowerService, never()).deleteCoborrower(anyLong(), anyString());
    }

    @Test
    void changeTypeChecksTheCustomerBeforeRemovingAnything() throws Exception {
        stubLoan("New");
        doThrow(notReady("Does not have a mailing address.")).when(readiness).check("C2");

        ValidationException thrown = assertThrows(ValidationException.class,
                () -> service.changeType("L1", bob(), "Guarantor"));

        assertEquals("Customer C2: Does not have a mailing address.",
                thrown.getResult().getError("customerNumber"));
        verify(coborrowerService, never()).deleteCoborrower(anyLong(), anyString());
    }

    @Test
    void changeTypePutsTheOriginalBackWhenAddingFails() throws Exception {
        stubLoan("New");
        doThrow(new LavsException("Mainframe refused")).when(coborrowerService)
                .addCoborrower("C2", "L1", "Guarantor");

        LavsException thrown = assertThrows(LavsException.class,
                () -> service.changeType("L1", bob(), "Guarantor"));

        assertEquals("Mainframe refused", thrown.getMessage());
        verify(coborrowerService).addCoborrower("C2", "L1", "Coborrower");   // restored
    }

    @Test
    void changeTypeSaysSoWhenTheOriginalCannotBePutBack() throws Exception {
        stubLoan("New");
        doThrow(new IOException("offline")).when(coborrowerService)
                .addCoborrower("C2", "L1", "Guarantor");
        doThrow(new IOException("still offline")).when(coborrowerService)
                .addCoborrower("C2", "L1", "Coborrower");

        LavsException thrown = assertThrows(LavsException.class,
                () -> service.changeType("L1", bob(), "Guarantor"));

        assertTrue(thrown.getMessage().contains("was removed from the loan"));
        assertTrue(thrown.getMessage().contains("offline"));
        verify(coborrowerService, times(1)).addCoborrower("C2", "L1", "Coborrower");
    }

    // ---- remove -------------------------------------------------------

    @Test
    void removeCoborrowerDeletesTheLink() throws Exception {
        stubLoan("New");

        service.removeCoborrower(" L1 ", bob());

        verify(coborrowerService).deleteCoborrower(7L, "L1");
    }

    @Test
    void removeCoborrowerRejectsBadInputAndNonNewLoans() throws Exception {
        assertThrows(ValidationException.class, () -> service.removeCoborrower("L1", null));
        assertThrows(ValidationException.class, () -> service.removeCoborrower("", bob()));

        stubLoan("Pending");
        assertThrows(ValidationException.class, () -> service.removeCoborrower("L1", bob()));
        verify(coborrowerService, never()).deleteCoborrower(anyLong(), anyString());
    }

    // ---- Customer facade ----------------------------------------------

    @Test
    void customerHandsCoborrowerWorkToTheCoborrowerService() throws Exception {
        Customer customer = new Customer(mock(MainframeAdapter.class));

        assertThrows(ValidationException.class, () -> customer.listLoanCoborrowers(" "));
        assertThrows(ValidationException.class, () -> customer.removeLoanCoborrower("L1", null));
        assertThrows(ValidationException.class,
                () -> customer.changeLoanCoborrowerType("L1", null, "Guarantor"));
        assertThrows(ValidationException.class, () -> customer.addLoanCoborrower("L1", "", "x"));
    }
}
