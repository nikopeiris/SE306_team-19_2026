package business.system;

import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import business.lavsexceptions.LavsException;
import business.system.dto.CustomerSummary;
import business.system.dto.LoanListResult;
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

    /** Told about each customer's loans as soon as they have been read. */
    @FunctionalInterface
    public interface ProgressListener {
        /**
         * @param newLoans loans just read for one customer (may be empty)
         * @param customersDone how many customers have been processed so far
         * @param customersTotal how many customers there are in total
         * @return true to keep going, false to stop early
         */
        boolean onCustomerDone(List<LoanSummary> newLoans, int customersDone, int customersTotal);
    }

    private final Loan loanService;

    public LoanSearchService(Loan loanService) {
        if (loanService == null) {
            throw new IllegalArgumentException("Loan service cannot be null");
        }
        this.loanService = loanService;
    }

    /**
     * Reads every loan (all pages) for the given customers, one customer at a time.
     * If the mainframe refuses one customer's request, that customer is skipped and
     * counted in the result so one bad customer cannot block everyone else's loans.
     *
     * @param listener optional; told after each customer so a screen can show loans as they arrive
     * @throws IOException if the mainframe cannot be reached
     */
    public LoanListResult listLoansFor(List<CustomerSummary> customers, ProgressListener listener)
            throws IOException {
        Map<String, LoanSummary> byNumber = new LinkedHashMap<>();
        int failed = 0;
        String firstError = null;
        int done = 0;

        for (CustomerSummary customer : customers) {
            List<LoanSummary> fresh = new ArrayList<>();
            try {
                collectLoans(customer, byNumber, fresh);
            } catch (LavsException e) {
                failed++;
                if (firstError == null) {
                    firstError = e.getMessage() == null ? "The mainframe rejected the request" : e.getMessage();
                }
            }
            done++;
            if (listener != null && !listener.onCustomerDone(fresh, done, customers.size())) {
                break;
            }
        }
        return new LoanListResult(new ArrayList<>(byNumber.values()), failed, firstError);
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

    /** Pages through one customer's loans; new ones go into both {@code seen} and {@code fresh}. */
    private void collectLoans(CustomerSummary customer, Map<String, LoanSummary> seen,
                              List<LoanSummary> fresh) throws IOException, LavsException {
        long page = FIRST_PAGE;
        for (int i = 0; i < MAX_PAGES; i++) {
            mainframe.client.messages.loan.List.Item[] items =
                    loanService.retrieveLoans(customer.getNumber(), page, PAGE_SIZE);
            if (items == null || items.length == 0) {
                break;
            }
            for (mainframe.client.messages.loan.List.Item item : items) {
                if (seen.containsKey(item.getLoanNumber())) {
                    continue;
                }
                LoanSummary loan = new LoanSummary(
                        item.getLoanNumber(),
                        customer.getNumber(),
                        customer.getFullName(),
                        parseAmount(item.getPrincipal()),
                        item.getStartDate(),
                        item.getStatus());
                seen.put(item.getLoanNumber(), loan);
                fresh.add(loan);
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