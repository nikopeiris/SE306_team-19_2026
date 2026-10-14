package business.system;

import business.Main;
import business.lavsexceptions.LavsException;
import business.validation.ValidationException;
import business.validation.ValidationResult;
import dataaccess.systemadapters.MainframeAdapter;
import mainframe.client.Status;
import mainframe.client.messages.loan.Create;

import java.io.IOException;
import java.util.Set;

/**
 * Business service for creating customer loans.
 */
public class Loan {
    private static final Set<String> VALID_LOAN_TYPES = Set.of(
            Create.RATETYPE_FIXED,
            Create.RATETYPE_FLOATING,
            Create.RATETYPE_INTERESTONLY
    );

    private static final Set<String> VALID_PAYMENT_FREQUENCIES = Set.of(
            Create.PAYMENTFREQUENCY_WEEKLY,
            Create.PAYMENTFREQUENCY_FORTNIGHTLY,
            Create.PAYMENTFREQUENCY_MONTHLY,
            Create.PAYMENTFREQUENCY_YEARLY,
            Create.PAYMENTFREQUENCY_ONRENEWAL
    );

    private static final Set<String> VALID_COMPOUND_TYPES = Set.of(
            Create.RATEFREQUENCY_DAILY,
            Create.RATEFREQUENCY_WEEKLY,
            Create.RATEFREQUENCY_FORTNIGHTLY,
            Create.RATEFREQUENCY_MONTHLY,
            Create.RATEFREQUENCY_YEARLY
    );

    private final MainframeAdapter loanAdapter;

    public Loan(MainframeAdapter loanAdapter) {
        if (loanAdapter == null) {
            throw new IllegalArgumentException("Loan adapter cannot be null");
        }
        this.loanAdapter = loanAdapter;
    }

    /**
     * Adds a loan to the supplied customer after validating the core business rules.
     *
     * @param customerNumber customer identifier to attach the loan to
     * @param principal amount borrowed; must be greater than zero
     * @param annualRate annual rate as a percentage; must not be negative
     * @param term loan term in months/periods; must be greater than zero
     * @param loanType valid loan category code
     * @param paymentFrequency valid payment frequency code
     * @param compoundType valid compounding frequency code
     * @return the created mainframe loan message
     * @throws ValidationException if business validation fails
     * @throws LavsException if the mainframe rejects the request
     * @throws IOException if the mainframe cannot be reached
     */
    public Create addLoan(
            String customerNumber,
            double principal,
            double annualRate,
            int term,
            String loanType,
            String paymentFrequency,
            String compoundType)
            throws IOException, LavsException {
        validateLoanDetails(
                customerNumber,
                principal,
                annualRate,
                term,
                loanType,
                paymentFrequency,
                compoundType);

        String normalizedCustomerNumber = customerNumber.trim();
        String normalizedLoanType = loanType.trim();
        String normalizedPaymentFrequency = paymentFrequency.trim();
        String normalizedCompoundType = compoundType.trim();

        Create createMessage = new Create(
                normalizedCustomerNumber,
                principal / Math.max(term, 1),
                normalizedPaymentFrequency,
                principal,
                annualRate,
                normalizedCompoundType,
                normalizedLoanType,
                "",
                "");

        Status status = loanAdapter.send(createMessage);
        Main.isValidStatus(status);
        return createMessage;
    }

    /**
     * Convenience wrapper used when the business validation is the only requirement.
     */
    public boolean addLoanToCustomerCheck(
            String customerNumber,
            double principal,
            double annualRate,
            int term,
            String loanType,
            String paymentFrequency,
            String compoundType)
            throws ValidationException {
        validateLoanDetails(
                customerNumber,
                principal,
                annualRate,
                term,
                loanType,
                paymentFrequency,
                compoundType);
        return true;
    }

    public void validateLoanDetails(
            String customerNumber,
            double principal,
            double annualRate,
            int term,
            String loanType,
            String paymentFrequency,
            String compoundType)
            throws ValidationException {
        ValidationResult result = new ValidationResult()
                .requireNotBlank("customerNumber", customerNumber,
                        "Customer number cannot be null or empty")
                .requireNotBlank("loanType", loanType,
                        "Loan type cannot be null or empty")
                .requireNotBlank("paymentFrequency", paymentFrequency,
                        "Payment frequency cannot be null or empty")
                .requireNotBlank("compoundType", compoundType,
                        "Compound type cannot be null or empty");

        if (principal <= 0) {
            result.addError("principal", "Loan principal must be greater than zero");
        }
        if (annualRate < 0) {
            result.addError("annualRate", "Annual rate cannot be negative");
        }
        if (term <= 0) {
            result.addError("term", "Term must be greater than zero");
        }

        if (loanType != null && !loanType.isBlank() && !VALID_LOAN_TYPES.contains(loanType.trim())) {
            result.addError("loanType", "Loan type must be a valid category code");
        }
        if (paymentFrequency != null && !paymentFrequency.isBlank()
                && !VALID_PAYMENT_FREQUENCIES.contains(paymentFrequency.trim())) {
            result.addError("paymentFrequency", "Payment frequency must be a valid category code");
        }
        if (compoundType != null && !compoundType.isBlank()
                && !VALID_COMPOUND_TYPES.contains(compoundType.trim())) {
            result.addError("compoundType", "Compound type must be a valid category code");
        }

        if (!result.isValid()) {
            throw new ValidationException(result);
        }
    }
}
