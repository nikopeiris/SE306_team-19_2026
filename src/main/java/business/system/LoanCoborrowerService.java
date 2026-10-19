package business.system;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import business.lavsexceptions.LavsException;
import business.system.dto.LoanCoborrower;
import business.validation.LoanValidation;
import business.validation.ValidationException;
import business.validation.ValidationResult;
import dataaccess.systemadapters.MainframeAdapter;
import mainframe.client.messages.loan.AddCoborrower;
import mainframe.client.messages.loan.Retrieve;
import mainframe.client.messages.loan.UpdateStatus;
import mainframe.client.messages.loanCoborrower.List.Item;

/**
 * Business service for the co-borrowers and guarantors of a loan: list, add, change
 * the type of, and remove them. Co-borrowers can only be changed while the loan is New,
 * because a Pending loan has already been sent for checking.
 */
public class LoanCoborrowerService {
    /** The kinds of co-borrower the mainframe accepts. */
    public static final List<String> TYPES = List.of(
            AddCoborrower.TYPE_COBORROWER,
            AddCoborrower.TYPE_GUARANTOR);

    /** Mainframe pages are one-based. */
    private static final long FIRST_PAGE = 1L;

    /** How many co-borrowers to ask the mainframe for per request. */
    private static final long PAGE_SIZE = 100L;

    /** Safety stop so a misbehaving server can never keep us paging forever. */
    private static final int MAX_PAGES = 500;

    /** Checks a customer is ready to be on a loan (mailing address and a contact method). */
    @FunctionalInterface
    interface ReadinessCheck {
        void check(String customerNumber) throws IOException, LavsException;
    }

    private final Loan loanService;
    private final Coborrower coborrowerService;
    private final ReadinessCheck readiness;

    public LoanCoborrowerService(MainframeAdapter adapter) {
        this(createLoan(adapter), new Coborrower(adapter),
                customerNumber -> LoanValidation.preReqCheck(customerNumber, adapter));
    }

    LoanCoborrowerService(Loan loanService, Coborrower coborrowerService,
            ReadinessCheck readiness) {
        this.loanService = loanService;
        this.coborrowerService = coborrowerService;
        this.readiness = readiness;
    }

    /** True if a loan with this status may still have its co-borrowers changed. */
    public static boolean canChange(String loanStatus) {
        return loanStatus != null && UpdateStatus.STATUS_NEW.equals(loanStatus.trim());
    }

    /**
     * Reads every co-borrower and guarantor (all pages) of a loan.
     *
     * @throws ValidationException if the loan number is blank
     * @throws LavsException if the mainframe rejects the request
     * @throws IOException if the mainframe cannot be reached
     */
    public List<LoanCoborrower> listCoborrowers(String loanNumber)
            throws IOException, LavsException {
        List<LoanCoborrower> coborrowers = new ArrayList<>();
        long page = FIRST_PAGE;
        for (int i = 0; i < MAX_PAGES; i++) {
            Item[] items = coborrowerService.retrieveCoborrowers(loanNumber, page, PAGE_SIZE);
            if (items == null || items.length == 0) {
                break;
            }
            for (Item item : items) {
                coborrowers.add(new LoanCoborrower(item.getId(), item.getCoborrowerNumber(),
                        item.getCoborrowerName(), item.getType()));
            }
            if (items.length < PAGE_SIZE) {
                break;   // last page
            }
            page++;
        }
        return coborrowers;
    }

    /**
     * Adds an existing customer to a loan. The customer must have a mailing address and a
     * phone or email, must differ from the primary borrower and must not already be on
     * the loan.
     *
     * @param type "Coborrower" or "Guarantor"
     * @throws ValidationException if a rule is broken
     * @throws LavsException if the mainframe rejects a request
     * @throws IOException if the mainframe cannot be reached
     */
    public void addCoborrower(String loanNumber, String customerNumber, String type)
            throws IOException, LavsException {
        coborrowerService.validateCoborrowerDetails(customerNumber, loanNumber, type);
        String loan = loanNumber.trim();
        String customer = customerNumber.trim();

        Retrieve loanDetails = requireChangeable(loan);
        if (customer.equals(loanDetails.getCustomerNumber())) {
            throw problem("customerNumber", "The primary borrower cannot also be a co-borrower.");
        }
        for (LoanCoborrower existing : listCoborrowers(loan)) {
            if (customer.equals(existing.customerNumber())) {
                throw problem("customerNumber",
                        "Customer " + customer + " is already on this loan.");
            }
        }
        addToLoan(customer, loan, type.trim());
    }

    /**
     * Changes a co-borrower to a guarantor or the other way round. The mainframe has no
     * update message, so the co-borrower is removed and added again; if adding fails, the
     * original is put back.
     *
     * @throws ValidationException if a rule is broken
     * @throws LavsException if the mainframe rejects a request
     * @throws IOException if the mainframe cannot be reached
     */
    public void changeType(String loanNumber, LoanCoborrower existing, String newType)
            throws IOException, LavsException {
        requireSelected(existing);
        ValidationResult result = new ValidationResult()
                .requireNotBlank("loanNumber", loanNumber, "Loan number cannot be null or empty");
        if (newType == null || !TYPES.contains(newType.trim())) {
            result.addError("type", "Co-borrower type is invalid");
        } else if (newType.trim().equals(existing.type())) {
            result.addError("type", "This customer is already a " + existing.type() + ".");
        }
        result.throwIfInvalid();
        String loan = loanNumber.trim();
        String target = newType.trim();

        requireChangeable(loan);
        // Make sure the add will work before anything is removed
        try {
            readiness.check(existing.customerNumber());
        } catch (ValidationException e) {
            throw prefixed(existing.customerNumber(), e);
        }

        coborrowerService.deleteCoborrower(existing.id(), loan);
        try {
            coborrowerService.addCoborrower(existing.customerNumber(), loan, target);
        } catch (IOException | LavsException e) {
            restore(existing, loan, e);
            throw e;
        }
    }

    /**
     * Removes a co-borrower or guarantor from a loan.
     *
     * @throws ValidationException if the loan is no longer New
     * @throws LavsException if the mainframe rejects the request
     * @throws IOException if the mainframe cannot be reached
     */
    public void removeCoborrower(String loanNumber, LoanCoborrower existing)
            throws IOException, LavsException {
        requireSelected(existing);
        new ValidationResult()
                .requireNotBlank("loanNumber", loanNumber, "Loan number cannot be null or empty")
                .throwIfInvalid();
        String loan = loanNumber.trim();
        requireChangeable(loan);
        coborrowerService.deleteCoborrower(existing.id(), loan);
    }

    /** Puts a removed co-borrower back after a failed change, or says it could not. */
    private void restore(LoanCoborrower original, String loan, Exception cause)
            throws LavsException {
        try {
            coborrowerService.addCoborrower(original.customerNumber(), loan, original.type());
        } catch (IOException | LavsException restoreFailure) {
            throw new LavsException("Customer " + original.customerNumber()
                    + " was removed from the loan but could not be added again ("
                    + cause.getMessage() + "). Please add them again as a "
                    + original.type() + ".");
        }
    }

    /** Adds the co-borrower, naming the customer in any readiness problem. */
    private void addToLoan(String customer, String loan, String type)
            throws IOException, LavsException {
        try {
            coborrowerService.addCoborrower(customer, loan, type);
        } catch (ValidationException e) {
            throw prefixed(customer, e);
        }
    }

    /** Loads the loan and makes sure its co-borrowers may still be changed. */
    private Retrieve requireChangeable(String loanNumber) throws IOException, LavsException {
        Retrieve loan = loanService.retrieveLoan(loanNumber);
        if (!canChange(loan.getStatus())) {
            throw problem("status", "Co-borrowers can only be changed while the loan is New"
                    + " (this loan is " + loan.getStatus() + ").");
        }
        return loan;
    }

    private static void requireSelected(LoanCoborrower existing) throws ValidationException {
        if (existing == null) {
            throw problem("coborrower", "Select a co-borrower first.");
        }
    }

    private static ValidationException problem(String field, String message) {
        return new ValidationException(new ValidationResult().addError(field, message));
    }

    private static ValidationException prefixed(String customerNumber, ValidationException e) {
        return problem("customerNumber",
                "Customer " + customerNumber + ": " + e.getMessage().replace("\n", " "));
    }

    private static Loan createLoan(MainframeAdapter adapter) {
        if (adapter == null) {
            throw new IllegalArgumentException("Co-borrower adapter cannot be null");
        }
        return new Loan(adapter);
    }
}
