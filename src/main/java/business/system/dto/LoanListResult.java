package business.system.dto;

import java.util.List;

/** The outcome of loading loans for many customers. */
public final class LoanListResult {
    private final List<LoanSummary> loans;
    private final int failedCustomers;
    private final String firstError;

    public LoanListResult(List<LoanSummary> loans, int failedCustomers, String firstError) {
        this.loans = loans;
        this.failedCustomers = failedCustomers;
        this.firstError = firstError;
    }

    /** Every loan that was read successfully. */
    public List<LoanSummary> getLoans() { return loans; }

    /** How many customers' loans the mainframe refused to list. */
    public int getFailedCustomers() { return failedCustomers; }

    /** The mainframe's message for the first refused customer, or null if none failed. */
    public String getFirstError() { return firstError; }
}