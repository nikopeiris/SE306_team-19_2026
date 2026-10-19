package business.system;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;

import business.lavsexceptions.LavsException;
import business.system.dto.CustomerSummary;
import business.system.dto.LoanListResult;
import mainframe.client.messages.loan.List.Item;

class LoanSearchServiceTest {
    @Test
    void collectsPagesDeduplicatesAndReportsProgress() throws Exception {
        Loan loan = mock(Loan.class);
        mainframe.client.messages.loan.List page = new mainframe.client.messages.loan.List("C1", 1, 100);
        Item first = page.new Item("L1", "1,200", "2026-01-01", "New");
        Item duplicate = page.new Item("L1", "999", "2026-01-02", "Old");
        Item second = page.new Item("L2", "not-a-number", null, "Pending");
        Item[] firstPage = new Item[100];
        firstPage[0] = first;
        for (int i = 1; i < firstPage.length; i++) {
            firstPage[i] = page.new Item("filler-" + i, "1", null, "New");
        }
        when(loan.retrieveLoans("C1", 1, 100)).thenReturn(firstPage);
        when(loan.retrieveLoans("C1", 2, 100)).thenReturn(new Item[] { duplicate, second });
        when(loan.retrieveLoans("C1", 3, 100)).thenReturn(new Item[0]);

        AtomicInteger callbacks = new AtomicInteger();
        LoanListResult result = new LoanSearchService(loan).listLoansFor(
                List.of(new CustomerSummary(" C1 ", "Jane", "Doe")),
                (newLoans, done, total) -> {
                    callbacks.incrementAndGet();
                    assertEquals(1, done);
                    assertEquals(1, total);
                    assertEquals(101, newLoans.size());
                    return true;
                });

        assertEquals(101, result.getLoans().size());
        assertEquals("L1", result.getLoans().get(0).getLoanNumber());
        assertNull(result.getLoans().get(0).getPrincipal());
        assertEquals("L2", result.getLoans().get(100).getLoanNumber());
        assertNull(result.getLoans().get(100).getPrincipal());
        assertEquals(1, callbacks.get());
        verify(loan).retrieveLoans("C1", 1, 100);
        verify(loan).retrieveLoans("C1", 2, 100);
    }

    @Test
    void skipsRejectedCustomersAndPreservesFirstError() throws Exception {
        Loan loan = mock(Loan.class);
        when(loan.retrieveLoans("C1", 1, 100))
                .thenThrow(new LavsException("rejected"));
        mainframe.client.messages.loan.List message = new mainframe.client.messages.loan.List("C2", 1, 100);
        when(loan.retrieveLoans("C2", 1, 100))
                .thenReturn(new Item[] { message.new Item("L2", "5", "d", "New") });

        LoanListResult result = new LoanSearchService(loan).listLoansFor(
                List.of(new CustomerSummary("C1", "A", "One"), new CustomerSummary("C2", "B", "Two")),
                null);

        assertEquals(1, result.getFailedCustomers());
        assertEquals("rejected", result.getFirstError());
        assertEquals(1, result.getLoans().size());
    }

    @Test
    void listenerCanStopAndFilteringHandlesBlankNullAndCase() throws Exception {
        Loan loan = mock(Loan.class);
        when(loan.retrieveLoans(anyString(), anyLong(), anyLong())).thenReturn(new Item[0]);
        LoanSearchService service = new LoanSearchService(loan);
        assertThrows(IllegalArgumentException.class, () -> new LoanSearchService(null));
        LoanListResult result = service.listLoansFor(
                List.of(new CustomerSummary("C1", "A", "One"), new CustomerSummary("C2", "B", "Two")),
                (newLoans, done, total) -> false);
        assertEquals(0, result.getLoans().size());

        business.system.dto.LoanSummary matching =
                new business.system.dto.LoanSummary("ab-123", "C1", "A", 1.0, null, "New");
        business.system.dto.LoanSummary missing =
                new business.system.dto.LoanSummary(null, "C1", "A", 1.0, null, "New");
        assertEquals(List.of(matching, missing),
                service.filterByLoanNumber(List.of(matching, missing), " "));
        assertEquals(List.of(matching),
                service.filterByLoanNumber(List.of(matching, missing), " B-12 "));
    }
}
