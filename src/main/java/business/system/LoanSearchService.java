package business.system;

import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import business.lavsexceptions.LavsException;
import business.system.dto.CustomerSummary;
import business.system.dto.LoanSummary;

/**
 * Collects and filters loans across customers. The mainframe only lists loans
 * per customer, so "all loans" means asking for each customer's loans in turn.
 */
public class LoanSearchService {
    /** How many loans to ask the mainframe for per request. */
    private static final long PAGE_SIZE = 100L;

    /** Mainframe pages are one-based (customer search rejects page < 1). */
    private static final long FIRST_PAGE = 1L;

    /** Safety stop so a misbehaving server can never keep us paging forever. */
    private static final int MAX_PAGES = 500;

    private final Loan loanService;

    public LoanSearchService(Loan loanService) {
        if (loanService == null) {
            throw new IllegalArgumentException("Loan service cannot be null");
        }
        this.loanService = loanService;
    }

    /**
     * Every loan (all pages) for the given customers, de-duplicated by loan number.
     *
     * @throws LavsException if the mainframe rejects a request
     * @throws IOException if the mainframe cannot be reached
     */
    public List<LoanSummary> listLoansFor(List<CustomerSummary> customers)
            throws IOException, LavsException {
        Map<String, LoanSummary> byNumber = new LinkedHashMap<>();
        for (CustomerSummary customer : customers) {
            collectLoans(customer, byNumber);
        }
        return new ArrayList<>(byNumber.values());
    }

    /**
     * Narrows a loan list by loan number. A blank query returns the whole list;
     * otherwise this is a case-insensitive "contains" match on the loan number.
     */
    public List<LoanSummary> filterByLoanNumber(List<LoanSummary> loans, String query) {
        String q = query == null ? "" : query.trim().toLowerCase();
        if (q.isEmpty()) {
            return loans;
        }
        List<LoanSummary> matches = new ArrayList<>();
        for (LoanSummary loan : loans) {
            if (loan.getLoanNumber() != null && loan.getLoanNumber().toLowerCase().contains(q)) {
                matches.add(loan);
            }
        }
        return matches;
    }

    /** Pages through one customer's loans and adds them to the map. */
    private void collectLoans(CustomerSummary customer, Map<String, LoanSummary> into)
            throws IOException, LavsException {
        long page = FIRST_PAGE;
        for (int i = 0; i < MAX_PAGES; i++) {
            mainframe.client.messages.loan.List.Item[] items =
                    loanService.retrieveLoans(customer.getNumber(), page, PAGE_SIZE);
            if (items == null || items.length == 0) {
                break;
            }
            for (mainframe.client.messages.loan.List.Item item : items) {
                into.putIfAbsent(item.getLoanNumber(), new LoanSummary(
                        item.getLoanNumber(),
                        customer.getNumber(),
                        customer.getFullName(),
                        parseAmount(item.getPrincipal()),
                        item.getStartDate(),
                        item.getStatus()));
            }
            if (items.length < PAGE_SIZE) {
                break;   // last page
            }
            page++;
        }
    }

    /** The mainframe sends the principal as text; null if it is missing or not a number. */
    private static Double parseAmount(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return Double.valueOf(value.trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }
}