package business.system;

import java.io.IOException;

import business.Main;
import business.lavsexceptions.LavsException;
import business.validation.LoanValidation;
import business.validation.ValidationException;
import dataaccess.systemadapters.MainframeAdapter;
import mainframe.client.Status;
import mainframe.client.messages.loan.AddCoborrower;
import mainframe.client.messages.loan.Create;
import mainframe.client.messages.loan.List;
import mainframe.client.messages.loan.Retrieve;
import mainframe.client.messages.loan.Update;

/**
 * Business service for creating customer loans.
 */
public class Loan {
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
     * @param renewalDate renewal date in yyyy-MM-dd format
     * @param startDate start date in yyyy-MM-dd format
     * @param guarantorId guarantor identifier (customer id of guarantor)
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
            String compoundType, String renewalDate, String startDate, String guarantorId)
            throws IOException, LavsException {
        LoanValidation.validateLoanDetails(
                customerNumber,
                principal,
                annualRate,
                term,
                loanType,
                paymentFrequency,
                compoundType, renewalDate, startDate);
        LoanValidation.preReqCheck(customerNumber, loanAdapter);
        LoanValidation.validateCustomerJobAndGuarantor(customerNumber, guarantorId, loanAdapter);

        String normalizedCustomerNumber = customerNumber.trim();
        String normalizedLoanType = loanType.trim();
        String normalizedPaymentFrequency = paymentFrequency.trim();
        String normalizedCompoundType = compoundType.trim();
        String normalizedRenewalDate = renewalDate.trim();
        String normalizedStartDate = startDate.trim();

        Create createMessage = new Create(
                normalizedCustomerNumber,
                principal / Math.max(term, 1),
                normalizedPaymentFrequency,
                principal,
                annualRate,
                normalizedCompoundType,
                normalizedLoanType,
                normalizedRenewalDate,
                normalizedStartDate);

        Status status = loanAdapter.send(createMessage);
        Main.isValidStatus(status);
        if (guarantorId != null && !guarantorId.isBlank()) {
            Coborrower coborrower = new Coborrower(loanAdapter);
            coborrower.addCoborrower(guarantorId.trim(), createMessage.getLoanNumber(), AddCoborrower.TYPE_GUARANTOR);
        }
        return createMessage;
    }

    /**
     * Retrieves a list of loans for a customer after validating the core business rules.
     *
     * @param customerNumber customer identifier to retrieve loans for
     * @param page page number of loans to retrieve
     * @param size number of loans to retrieve per page
     * @return an array of loan items
     * @throws LavsException if the mainframe rejects the request
     * @throws IOException if the mainframe cannot be reached
     */
    public List.Item[] retrieveLoans(String customerNumber, long page, long size)
            throws IOException, LavsException {
        List listMessage = new List(customerNumber, page, size);
        Status status = loanAdapter.send(listMessage);
        Main.isValidStatus(status);
        return listMessage.getItems();
    }

    /**
     * Retrieves a loan by its unique loan number.
     *
     * @param loanNumber loan identifier to retrieve
     * @return the retrieved loan details
     * @throws LavsException if the mainframe rejects the request
     * @throws IOException if the mainframe cannot be reached
     */
    public Retrieve retrieveLoan(String loanNumber) throws IOException, LavsException {
        Retrieve retrieveMessage = new Retrieve(loanNumber);
        Status status = loanAdapter.send(retrieveMessage);
        Main.isValidStatus(status);
        return retrieveMessage;
    }

    /**
     * Retrieves the repayment schedule for a loan.
     *
     * @param loanNumber loan identifier to retrieve the repayment schedule for
     * @param page page number of the schedule to retrieve
     * @param size number of schedule items to retrieve per page
     * @return an array of repayment schedule items
     * @throws LavsException if the mainframe rejects the request
     * @throws IOException if the mainframe cannot be reached
     */
    public mainframe.client.messages.loanRepaymentSchedule.List.Item[] retrieveRepaymentSchedule(
            String loanNumber, long page, long size) throws IOException, LavsException {
        mainframe.client.messages.loanRepaymentSchedule.List listMessage =
                new mainframe.client.messages.loanRepaymentSchedule.List(loanNumber, page, size);
        Status status = loanAdapter.send(listMessage);
        Main.isValidStatus(status);
        return listMessage.getItems();
    }

    /**
     * Retrieves the summary of a loan.
     *
     * @param loanNumber loan identifier to retrieve the summary for
     * @return the loan summary details
     * @throws LavsException if the mainframe rejects the request
     * @throws IOException if the mainframe cannot be reached
     */
    public mainframe.client.messages.loanSummary.Retrieve retrieveLoanSummary(String loanNumber)
            throws IOException, LavsException {
        mainframe.client.messages.loanSummary.Retrieve retrieveMessage =
                new mainframe.client.messages.loanSummary.Retrieve(loanNumber);
        Status status = loanAdapter.send(retrieveMessage);
        Main.isValidStatus(status);
        return retrieveMessage;
    }

    /**
     * Updates loan details in the mainframe after validating the input values.
     *
     * @param customerNumber customer identifier
     * @param loanNumber loan identifier
     * @param paymentAmount repayment amount per period
     * @param paymentFrequency payment frequency category code
     * @param principal total loan principal amount
     * @param rateAmount interest rate amount
     * @param rateFrequency compounding rate frequency code
     * @param rateType loan rate type code
     * @param renewalDate renewal date in yyyy-MM-dd format
     * @param startDate start date in yyyy-MM-dd format
     * @return the updated Update mainframe message
     * @throws ValidationException if business validation fails
     * @throws LavsException if the mainframe rejects the request
     * @throws IOException if the mainframe cannot be reached
     */
    public Update updateLoan(
            String customerNumber,
            String loanNumber,
            double paymentAmount,
            String paymentFrequency,
            double principal,
            double rateAmount,
            String rateFrequency,
            String rateType,
            String renewalDate,
            String startDate) throws IOException, LavsException {
        LoanValidation.validateUpdateLoanDetails(
                customerNumber,
                loanNumber,
                paymentAmount,
                paymentFrequency,
                principal,
                rateAmount,
                rateFrequency,
                rateType,
                renewalDate,
                startDate);

        Update updateMessage = new Update(
                customerNumber.trim(),
                loanNumber.trim(),
                paymentAmount,
                paymentFrequency.trim(),
                principal,
                rateAmount,
                rateFrequency.trim(),
                rateType.trim(),
                renewalDate.trim(),
                startDate.trim());

        Status status = loanAdapter.send(updateMessage);
        Main.isValidStatus(status);
        return updateMessage;
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
            String compoundType,
            String renewalDate,
            String startDate)
            throws LavsException, IOException {
        LoanValidation.validateLoanDetails(
                customerNumber,
                principal,
                annualRate,
                term,
                loanType,
                paymentFrequency,
                compoundType,
                renewalDate,
                startDate);
        return true;
    }
}
