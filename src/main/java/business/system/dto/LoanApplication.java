package business.system.dto;

/**
 * Everything typed or picked on the "Add Loan" form, exactly as entered (all text).
 * The business layer parses and validates it.
 */
public record LoanApplication(
        String loanType,
        String amount,
        String term,
        String rate,
        String compoundType,
        String paymentFrequency,
        boolean interestOnly) {}