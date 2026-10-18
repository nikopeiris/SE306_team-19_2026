package business.system;

import java.io.IOException;
import java.util.List;
import java.util.Map;

import business.Main;
import business.lavsexceptions.LavsException;
import business.system.customer.CustomerEmployerService;
import business.validation.LoanValidation;
import business.validation.ValidationException;
import business.validation.ValidationResult;
import dataaccess.systemadapters.MainframeAdapter;
import mainframe.client.Status;
import mainframe.client.messages.loan.AddCoborrower;
import mainframe.client.messages.loan.Retrieve;
import mainframe.client.messages.loan.UpdateStatus;
import mainframe.client.messages.loanCoborrower.List.Item;

/**
 * Business service for changing the status of a loan. It decides which changes are
 * allowed and, before a loan is sent for checking (New to Pending), makes sure the
 * loan and all of its borrowers are ready.
 */
public class LoanStatusService {
    /**
     * The changes an MMM may make. New loans are sent for checking (Pending) or
     * cancelled; a Pending loan can only be cancelled. Checking and verification
     * (which sets Active or Defaulted) is outside the scope of LAVS.
     */
    private static final Map<String, List<String>> ALLOWED_CHANGES = Map.of(
            UpdateStatus.STATUS_NEW,
            List.of(UpdateStatus.STATUS_PENDING, UpdateStatus.STATUS_CANCELLED),
            UpdateStatus.STATUS_PENDING,
            List.of(UpdateStatus.STATUS_CANCELLED));

    /** How many co-borrowers to ask the mainframe for (a loan has only a handful). */
    private static final long COBORROWER_PAGE_SIZE = 100L;

    /** The questions this service asks the mainframe about the borrowers of a loan. */
    interface BorrowerLookups {
        /** The co-borrowers and guarantors of a loan. */
        Item[] coborrowers(String loanNumber) throws IOException, LavsException;

        /** Throws a ValidationException if the customer lacks a mailing address or contact. */
        void checkContactDetails(String customerNumber) throws IOException, LavsException;

        /** True if the customer has at least one employer. */
        boolean hasEmployer(String customerNumber) throws IOException, LavsException;
    }

    private final Loan loanService;
    private final BorrowerLookups lookups;

    public LoanStatusService(MainframeAdapter adapter) {
        this(createLoan(adapter), lookupsFor(adapter));
    }

    LoanStatusService(Loan loanService, BorrowerLookups lookups) {
        this.loanService = loanService;
        this.lookups = lookups;
    }

    /**
     * The statuses a loan with the given status can be changed to.
     *
     * @return the allowed new statuses; empty if the loan can no longer be changed
     */
    public List<String> allowedStatuses(String currentStatus) {
        if (currentStatus == null) {
            return List.of();
        }
        return ALLOWED_CHANGES.getOrDefault(currentStatus.trim(), List.of());
    }

    /**
     * Changes the status of a loan. A change to Pending is only made once the primary
     * borrower and every co-borrower have a mailing address and a phone or email, and
     * the primary borrower has an employer or the loan has a guarantor.
     *
     * @param loanNumber the loan to change
     * @param newStatus the status to change it to
     * @return the status message sent to the mainframe
     * @throws ValidationException if the change is not allowed or a borrower is not ready
     * @throws LavsException if the mainframe rejects a request
     * @throws IOException if the mainframe cannot be reached
     */
    public UpdateStatus changeStatus(String loanNumber, String newStatus)
            throws IOException, LavsException {
        LoanValidation.validateUpdateLoanStatus(loanNumber, newStatus);
        String number = loanNumber.trim();
        String target = newStatus.trim();

        Retrieve loan = loanService.retrieveLoan(number);
        if (!allowedStatuses(loan.getStatus()).contains(target)) {
            throw new ValidationException(new ValidationResult().addError("status",
                    "A loan that is " + loan.getStatus() + " cannot be changed to "
                    + target + "."));
        }
        if (UpdateStatus.STATUS_PENDING.equals(target)) {
            checkReadyForChecking(loan, number);
        }
        return loanService.updateLoanStatus(number, target);
    }

    /** Collects every problem that stops the loan being sent for checking. */
    private void checkReadyForChecking(Retrieve loan, String loanNumber)
            throws IOException, LavsException {
        ValidationResult result = new ValidationResult();
        String primary = loan.getCustomerNumber();
        checkBorrower(result, primary, loan.getCustomerName());

        boolean hasGuarantor = false;
        for (Item coborrower : lookups.coborrowers(loanNumber)) {
            checkBorrower(result, coborrower.getCoborrowerNumber(),
                    coborrower.getCoborrowerName());
            if (AddCoborrower.TYPE_GUARANTOR.equals(coborrower.getType())) {
                hasGuarantor = true;
            }
        }

        if (!hasGuarantor && !lookups.hasEmployer(primary)) {
            result.addError("guarantor", "The customer has no employer, so the loan needs "
                    + "a guarantor.");
        }
        result.throwIfInvalid();
    }

    /** Records, against the borrower's number, why they are not ready. */
    private void checkBorrower(ValidationResult result, String customerNumber, String name)
            throws IOException, LavsException {
        try {
            lookups.checkContactDetails(customerNumber);
        } catch (ValidationException e) {
            result.addError(customerNumber, name + " (" + customerNumber + "): "
                    + e.getMessage().replace("\n", " "));
        }
    }

    private static Loan createLoan(MainframeAdapter adapter) {
        if (adapter == null) {
            throw new IllegalArgumentException("Loan status adapter cannot be null");
        }
        return new Loan(adapter);
    }

    /** The real lookups, answered by the mainframe. */
    static BorrowerLookups lookupsFor(MainframeAdapter adapter) {
        return new BorrowerLookups() {
            @Override
            public Item[] coborrowers(String loanNumber) throws IOException, LavsException {
                mainframe.client.messages.loanCoborrower.List list =
                        new mainframe.client.messages.loanCoborrower.List(
                                loanNumber, 1L, COBORROWER_PAGE_SIZE);
                Status status = adapter.send(list);
                Main.isValidStatus(status);
                return list.getItems();
            }

            @Override
            public void checkContactDetails(String customerNumber)
                    throws IOException, LavsException {
                LoanValidation.preReqCheck(customerNumber, adapter);
            }

            @Override
            public boolean hasEmployer(String customerNumber)
                    throws IOException, LavsException {
                return new CustomerEmployerService(adapter)
                        .listEmployers(customerNumber, 1L, 1L).length > 0;
            }
        };
    }
}
