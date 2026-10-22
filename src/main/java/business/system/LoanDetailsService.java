package business.system;

import java.io.IOException;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import business.lavsexceptions.LavsException;
import business.system.dto.CoborrowerRow;
import business.system.dto.CustomerSummary;
import business.system.dto.LoanApplication;
import business.system.dto.LoanDetail;
import business.system.dto.LoanRow;
import business.system.dto.PaymentRow;
import business.system.dto.RepaymentRow;
import business.system.dto.NoteDetail;
import business.system.dto.NoteRow;
import business.validation.LoanValidation;
import business.validation.ValidationException;
import business.validation.ValidationResult;
import dataaccess.systemadapters.MainframeAdapter;
import mainframe.client.messages.loan.AddCoborrower;
import mainframe.client.messages.loan.Create;
import mainframe.client.messages.loan.Retrieve;
import mainframe.client.messages.loan.UpdateStatus;

/**
 * Business service behind the customer screen's Loan tab: the choices offered on the
 * "Add Loan" form, creating a loan from that form, and reading loans back for display.
 * All parsing, validation, defaults and calculations for that tab live here.
 */
public class LoanDetailsService {
    private static final long PAGE_SIZE = 100L;
    private static final int MAX_PAGES = 500;

    // label shown to the user -> code the mainframe uses
    private static final Map<String, String> LOAN_TYPES = labels(
            "Fixed", Create.RATETYPE_FIXED,
            "Variable", Create.RATETYPE_FLOATING);
    private static final Map<String, String> COMPOUND_TYPES = labels(
            "Daily", Create.RATEFREQUENCY_DAILY,
            "Weekly", Create.RATEFREQUENCY_WEEKLY,
            "Fortnightly", Create.RATEFREQUENCY_FORTNIGHTLY,
            "Monthly", Create.RATEFREQUENCY_MONTHLY,
            "Yearly", Create.RATEFREQUENCY_YEARLY);
    private static final Map<String, String> PAYMENT_FREQUENCIES = labels(
            "Weekly", Create.PAYMENTFREQUENCY_WEEKLY,
            "Fortnightly", Create.PAYMENTFREQUENCY_FORTNIGHTLY,
            "Monthly", Create.PAYMENTFREQUENCY_MONTHLY,
            "Yearly", Create.PAYMENTFREQUENCY_YEARLY,
            "On renewal", Create.PAYMENTFREQUENCY_ONRENEWAL);

    private static final Map<String, Integer> PAYMENTS_PER_YEAR = Map.of(
            Create.PAYMENTFREQUENCY_WEEKLY, 52,
            Create.PAYMENTFREQUENCY_FORTNIGHTLY, 26,
            Create.PAYMENTFREQUENCY_MONTHLY, 12,
            Create.PAYMENTFREQUENCY_YEARLY, 1,
            Create.PAYMENTFREQUENCY_ONRENEWAL, 1);

    // what the user can pick as the role of someone added to a loan
    private static final List<String> COBORROWER_TYPES = List.of(
            AddCoborrower.TYPE_COBORROWER,
            AddCoborrower.TYPE_GUARANTOR);

    private final MainframeAdapter adapter;
    private final Loan loanService;
    private final Payment paymentService;
    private final LoanNoteService noteService;
    // Created on first use. Coborrower extends Customer, and a Customer builds a
    // LoanDetailsService, so creating it in this constructor would never stop.
    private Coborrower coborrowerService;

    public LoanDetailsService(MainframeAdapter adapter) {
        if (adapter == null) {
            throw new IllegalArgumentException("Loan details adapter cannot be null");
        }
        this.adapter = adapter;
        this.loanService = new Loan(adapter);
        this.paymentService = new Payment(adapter);
        this.noteService = new LoanNoteService(adapter);
    }

    // ------------------------------------------------------------------
    // Choices for the Add Loan form
    // ------------------------------------------------------------------

    public List<String> loanTypeOptions() { return new ArrayList<>(LOAN_TYPES.keySet()); }

    public List<String> compoundTypeOptions() { return new ArrayList<>(COMPOUND_TYPES.keySet()); }

    public List<String> paymentFrequencyOptions() { return new ArrayList<>(PAYMENT_FREQUENCIES.keySet()); }

    // ------------------------------------------------------------------
    // Adding a loan
    // ------------------------------------------------------------------

    /**
     * Creates a loan from the Add Loan form. The amount, term and rate are text; "$", "%"
     * and commas are ignored. The term is in years. The loan starts today and renews when
     * the term ends. An interest-only loan overrides the chosen loan type. No guarantor is
     * supplied yet, so a customer with no employer is rejected by the existing loan rules.
     *
     * @throws ValidationException if anything on the form is not acceptable
     * @throws LavsException if the mainframe rejects a request
     * @throws IOException if the mainframe cannot be reached
     */
    public Create addLoan(String customerNumber, LoanApplication application)
            throws IOException, LavsException {
        ValidationResult result = new ValidationResult().requireNotBlank("customerNumber",
                customerNumber, "Customer number cannot be null or empty");
        Terms terms = parseTerms(application, result);

        LocalDate start = LocalDate.now();
        LocalDate renewal = start.plusMonths(months(terms.years()));
        return loanService.addLoan(customerNumber, terms.principal(), terms.rate(),
                terms.payments(), terms.rateType(), terms.frequency(), terms.compound(),
                renewal.toString(), start.toString(), null);
    }

    // ------------------------------------------------------------------
    // Editing a loan
    // ------------------------------------------------------------------

    /**
     * Changes an existing loan using the same form as adding one. The start date stays as it
     * is; the payment amount and the renewal date (start date plus the term) are worked out
     * again from the new details. Only New and Pending loans can be edited.
     *
     * @throws ValidationException if the loan cannot be edited or anything on the form is not acceptable
     * @throws LavsException if the mainframe rejects a request
     * @throws IOException if the mainframe cannot be reached
     */
    public void updateLoan(String loanNumber, LoanApplication application)
            throws IOException, LavsException {
        requireNumber("loanNumber", loanNumber, "Loan number");
        String number = loanNumber.trim();

        Retrieve loan = loanService.retrieveLoan(number);
        if (!isEditable(loan.getStatus())) {
            throw new ValidationException(new ValidationResult().addError("status",
                    "A loan that is " + loan.getStatus() + " cannot be edited."));
        }
        Terms terms = parseTerms(application, new ValidationResult());

        String startDate = loan.getStartDate() == null ? "" : loan.getStartDate().trim();
        LocalDate renewal;
        try {
            renewal = LocalDate.parse(startDate).plusMonths(months(terms.years()));
        } catch (DateTimeParseException e) {
            throw new ValidationException(new ValidationResult().addError("startDate",
                    "The loan's start date is not in yyyy-MM-dd format, so it cannot be edited."));
        }

        loanService.updateLoan(loan.getCustomerNumber(), number,
                terms.principal() / terms.payments(), terms.frequency(), terms.principal(),
                terms.rate(), terms.compound(), terms.rateType(), renewal.toString(),
                startDate, startDate);
    }

    // ------------------------------------------------------------------
    // Reading loans
    // ------------------------------------------------------------------

    /** Every loan the customer has (all pages). */
    public List<LoanRow> loansFor(String customerNumber) throws IOException, LavsException {
        requireNumber("customerNumber", customerNumber, "Customer number");
        List<LoanRow> rows = new ArrayList<>();
        long page = 1L;
        for (int i = 0; i < MAX_PAGES; i++) {
            mainframe.client.messages.loan.List.Item[] items =
                    loanService.retrieveLoans(customerNumber.trim(), page, PAGE_SIZE);
            if (items == null || items.length == 0) {
                break;
            }
            for (mainframe.client.messages.loan.List.Item item : items) {
                rows.add(toRow(item.getLoanNumber(), parseAmount(item.getPrincipal()),
                        item.getStartDate(), item.getStatus()));
            }
            if (items.length < PAGE_SIZE) {
                break;
            }
            page++;
        }
        return rows;
    }

    /** The full picture of one loan for the summary screen. */
    public LoanDetail loanDetail(String loanNumber) throws IOException, LavsException {
        requireNumber("loanNumber", loanNumber, "Loan number");
        String number = loanNumber.trim();

        Retrieve loan = loanService.retrieveLoan(number);

        mainframe.client.messages.loanSummary.Retrieve summary = null;
        try {
            summary = loanService.retrieveLoanSummary(number);
        } catch (LavsException e) {
            // no payment figures yet; those fields stay empty
        }

        Double interestPaid = summary == null ? null : summary.getTotalInterestPaid();
        Double amountPaid = summary == null ? null : summary.getTotalAmountPaid();
        Double principalPaid = (interestPaid == null || amountPaid == null)
                ? null : amountPaid - interestPaid;

        String rateType = loan.getRateType();
        boolean interestOnly = Create.RATETYPE_INTERESTONLY.equals(rateType);
        Double years = termYears(summary == null ? null : summary.getNumberOfPayments(),
                loan.getPaymentFrequency());

        LoanApplication editForm = new LoanApplication(
                interestOnly ? null : typeLabel(rateType),
                plain(loan.getPrincipal()),
                plain(years),
                plain(loan.getRateAmount()),
                labelFor(COMPOUND_TYPES, loan.getRateFrequency()),
                labelFor(PAYMENT_FREQUENCIES, loan.getPaymentFrequency()),
                interestOnly);

        return new LoanDetail(
                number,
                loan.getCustomerName(),
                loan.getCustomerNumber(),
                typeLabel(rateType),
                termText(years),
                labelFor(COMPOUND_TYPES, loan.getRateFrequency()),
                loan.getPrincipal(),
                labelFor(PAYMENT_FREQUENCIES, loan.getPaymentFrequency()),
                interestOnly,
                loan.getRateAmount(),
                loan.getPaymentAmount(),
                loan.getRenewalDate(),
                interestPaid,
                principalPaid,
                loan.getStatus(),
                isEditable(loan.getStatus()),
                editForm);
    }

    /** The loan's whole repayment schedule (all pages). */
    public List<RepaymentRow> repaymentSchedule(String loanNumber)
            throws IOException, LavsException {
        requireNumber("loanNumber", loanNumber, "Loan number");
        List<RepaymentRow> rows = new ArrayList<>();
        long page = 1L;
        for (int i = 0; i < MAX_PAGES; i++) {
            mainframe.client.messages.loanRepaymentSchedule.List.Item[] items =
                    loanService.retrieveRepaymentSchedule(loanNumber.trim(), page, PAGE_SIZE);
            if (items == null || items.length == 0) {
                break;
            }
            for (mainframe.client.messages.loanRepaymentSchedule.List.Item item : items) {
                rows.add(new RepaymentRow(item.getPaymentNumber(), item.getDate(),
                        item.getAmountPaid(), item.getPrincipalAmount(),
                        item.getInterestAmount(), item.getAmountRemaining()));
            }
            if (items.length < PAGE_SIZE) {
                break;
            }
            page++;
        }
        return rows;
    }

    /** Every recorded payment for the loan (all pages). */
    public List<PaymentRow> paymentsFor(String loanNumber)
            throws IOException, LavsException {
        requireNumber("loanNumber", loanNumber, "Loan number");
        List<PaymentRow> rows = new ArrayList<>();
        long page = 1L;
        for (int i = 0; i < MAX_PAGES; i++) {
            mainframe.client.messages.loanPayment.List.Item[] items =
                    paymentService.listPayments(loanNumber.trim(), page, PAGE_SIZE);
            if (items == null || items.length == 0) {
                break;
            }
            for (mainframe.client.messages.loanPayment.List.Item item : items) {
                rows.add(new PaymentRow(item.getId(), item.getAmountPaid(), item.getDate()));
            }
            if (items.length < PAGE_SIZE) {
                break;
            }
            page++;
        }
        return rows;
    }

    public void addPayment(String loanNumber, double amountPaid, String date)
            throws IOException, LavsException {
        requireNumber("loanNumber", loanNumber, "Loan number");
        paymentService.addPayment(amountPaid, date, loanNumber.trim());
    }

    public void updatePayment(String loanNumber, long paymentId, double amountPaid, String date)
            throws IOException, LavsException {
        requireNumber("loanNumber", loanNumber, "Loan number");
        paymentService.deletePayment(paymentId, loanNumber.trim());
        paymentService.addPayment(amountPaid, date, loanNumber.trim());
    }

    public void deletePayment(String loanNumber, long paymentId)
            throws IOException, LavsException {
        requireNumber("loanNumber", loanNumber, "Loan number");
        paymentService.deletePayment(paymentId, loanNumber.trim());
    }

    // ------------------------------------------------------------------
    // Notes
    // ------------------------------------------------------------------

    public List<NoteRow> notesFor(String loanNumber) throws IOException, LavsException {
        requireNumber("loanNumber", loanNumber, "Loan number");
        List<NoteRow> rows = new ArrayList<>();
        long page = 1L;
        for (int i = 0; i < MAX_PAGES; i++) {
            mainframe.client.messages.loanNote.List.Item[] items =
                    noteService.listNotes(loanNumber.trim(), page, PAGE_SIZE);
            if (items == null || items.length == 0) {
                break;
            }
            for (mainframe.client.messages.loanNote.List.Item item : items) {
                rows.add(new NoteRow(item.getId(), item.getType(), item.getAuthor(),
                        item.getDateAdded()));
            }
            if (items.length < PAGE_SIZE) {
                break;
            }
            page++;
        }
        return rows;
    }

    public NoteDetail noteDetail(String loanNumber, long noteId)
            throws IOException, LavsException {
        mainframe.client.messages.loanNote.Retrieve note =
                noteService.retrieveNote(loanNumber, noteId);
        return new NoteDetail(note.getId(), note.getType(), note.getLines(), note.getAuthor(),
                note.getDateAdded());
    }

    public void addNote(String loanNumber, String lines, String type)
            throws IOException, LavsException {
        noteService.addNote(loanNumber, lines, type);
    }

    public void updateNote(String loanNumber, long noteId, String lines, String type)
            throws IOException, LavsException {
        noteService.updateNote(loanNumber, noteId, lines, type);
    }

    public void deleteNote(String loanNumber, long noteId) throws IOException, LavsException {
        noteService.deleteNote(loanNumber, noteId);
    }

    // ------------------------------------------------------------------
    // Co-borrowers and guarantors
    // ------------------------------------------------------------------

    /** The roles a person can have on a loan: "Coborrower" or "Guarantor". */
    public List<String> coborrowerTypeOptions() { return COBORROWER_TYPES; }

    /** Every co-borrower and guarantor on the loan (all pages). */
    public List<CoborrowerRow> coborrowersFor(String loanNumber)
            throws IOException, LavsException {
        requireNumber("loanNumber", loanNumber, "Loan number");
        List<CoborrowerRow> rows = new ArrayList<>();
        long page = 1L;
        for (int i = 0; i < MAX_PAGES; i++) {
            mainframe.client.messages.loanCoborrower.List.Item[] items =
                    coborrowers().retrieveCoborrowers(loanNumber.trim(), page, PAGE_SIZE);
            if (items == null || items.length == 0) {
                break;
            }
            for (mainframe.client.messages.loanCoborrower.List.Item item : items) {
                rows.add(new CoborrowerRow(item.getId(), item.getCoborrowerNumber(),
                        item.getCoborrowerName(), item.getType()));
            }
            if (items.length < PAGE_SIZE) {
                break;
            }
            page++;
        }
        return rows;
    }

    /**
     * Finds existing customers by name, for choosing who to add to a loan. The mainframe only
     * matches whole words. A blank search finds nothing.
     */
    public List<CustomerSummary> searchCustomers(String term) throws IOException, LavsException {
        return coborrowers().searchCustomers(term);
    }

    /**
     * Adds an existing customer to a loan as a co-borrower or guarantor. The customer must have
     * a mailing address and a phone number or email, must not be the loan's primary borrower,
     * and must not already be on the loan.
     *
     * @throws ValidationException if the customer or type is not acceptable
     * @throws LavsException if the mainframe rejects a request
     * @throws IOException if the mainframe cannot be reached
     */
    public void addCoborrower(String loanNumber, String customerNumber, String type)
            throws IOException, LavsException {
        coborrowers().validateCoborrowerDetails(customerNumber, loanNumber, type);
        String loan = loanNumber.trim();
        String customer = customerNumber.trim();
        requireCanJoin(loan, customer, null);
        coborrowers().addCoborrower(customer, loan, type.trim());
    }

    /**
     * Changes who a co-borrower is, or their role. The mainframe has no update message, so the
     * old entry is removed and a new one added. Everything that can be checked is checked before
     * anything is removed, and if the new entry is rejected the old one is put back.
     *
     * @throws ValidationException if the customer or type is not acceptable
     * @throws LavsException if the mainframe rejects a request
     * @throws IOException if the mainframe cannot be reached
     */
    public void updateCoborrower(String loanNumber, long coborrowerId, String customerNumber,
            String type) throws IOException, LavsException {
        Coborrower service = coborrowers();
        service.validateCoborrowerDetails(customerNumber, loanNumber, type);
        String loan = loanNumber.trim();
        String customer = customerNumber.trim();
        String newType = type.trim();

        mainframe.client.messages.loanCoborrower.Retrieve existing =
                service.retrieveCoborrower(coborrowerId, loan);
        String oldCustomer = existing.getCoborrowerNumber() == null
                ? "" : existing.getCoborrowerNumber().trim();
        String oldType = existing.getType() == null ? "" : existing.getType().trim();
        if (oldCustomer.equals(customer) && oldType.equals(newType)) {
            return;     // nothing changed
        }

        requireCanJoin(loan, customer, coborrowerId);
        if (!oldCustomer.equals(customer)) {
            LoanValidation.preReqCheck(customer, adapter);
        }

        service.deleteCoborrower(coborrowerId, loan);
        try {
            service.addCoborrower(customer, loan, newType);
        } catch (IOException | LavsException | RuntimeException e) {
            try {
                service.addCoborrower(oldCustomer, loan, oldType);
            } catch (IOException | LavsException | RuntimeException restoreFailed) {
                e.addSuppressed(restoreFailed);
            }
            throw e;
        }
    }

    /** Removes a co-borrower or guarantor from a loan. */
    public void deleteCoborrower(String loanNumber, long coborrowerId)
            throws IOException, LavsException {
        coborrowers().deleteCoborrower(coborrowerId, loanNumber);
    }

    // ------------------------------------------------------------------
    // Helpers
    // ------------------------------------------------------------------

    private Coborrower coborrowers() {
        if (coborrowerService == null) {
            coborrowerService = new Coborrower(adapter);
        }
        return coborrowerService;
    }

    /**
     * Checks the customer may be added to the loan: not its primary borrower and not already on
     * it. {@code ignoreId} is the entry being replaced when an existing one is edited.
     */
    private void requireCanJoin(String loanNumber, String customerNumber, Long ignoreId)
            throws IOException, LavsException {
        ValidationResult result = new ValidationResult();
        String primary = loanService.retrieveLoan(loanNumber).getCustomerNumber();
        if (primary != null && customerNumber.equals(primary.trim())) {
            result.addError("coborrowerNumber",
                    "The primary borrower cannot also be a co-borrower on the same loan.");
        } else {
            for (CoborrowerRow row : coborrowersFor(loanNumber)) {
                boolean replaced = ignoreId != null && row.id() == ignoreId;
                if (!replaced && row.customerNumber() != null
                        && customerNumber.equals(row.customerNumber().trim())) {
                    result.addError("coborrowerNumber",
                            "Customer " + customerNumber + " is already on this loan.");
                    break;
                }
            }
        }
        result.throwIfInvalid();
    }

    private LoanRow toRow(String loanNumber, Double amount, String startDate, String status)
            throws IOException {
        String type = null;
        try {
            type = typeLabel(loanService.retrieveLoan(loanNumber).getRateType());
        } catch (LavsException e) {
            // show the loan without its type
        }
        return new LoanRow(loanNumber, type, amount, startDate, status);
    }

    private static void requireNumber(String field, String value, String label)
            throws ValidationException {
        new ValidationResult()
                .requireNotBlank(field, value, label + " cannot be null or empty")
                .throwIfInvalid();
    }

    private static String codeFor(Map<String, String> options, String label,
            ValidationResult result, String field, String message) {
        String code = label == null ? null : options.get(label.trim());
        if (code == null) {
            result.addError(field, message);
        }
        return code;
    }

    private static Double parseNumber(ValidationResult result, String field, String text,
            String label) {
        if (text == null || text.isBlank()) {
            result.addError(field, label + " cannot be empty");
            return null;
        }
        try {
            return Double.valueOf(text.replaceAll("[,$%\\s]", ""));
        } catch (NumberFormatException e) {
            result.addError(field, label + " must be a number");
            return null;
        }
    }

    /** The mainframe sends amounts as text; null if missing or not a number. */
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

    private static String typeLabel(String rateType) {
        if (rateType == null) {
            return null;
        }
        if (Create.RATETYPE_INTERESTONLY.equals(rateType)) {
            return "Interest Only";
        }
        return labelFor(LOAN_TYPES, rateType);
    }

    private static String labelFor(Map<String, String> options, String code) {
        if (code == null) {
            return null;
        }
        for (Map.Entry<String, String> e : options.entrySet()) {
            if (e.getValue().equals(code)) {
                return e.getKey();
            }
        }
        return code;
    }

    /** A loan can be edited while it is New or Pending. */
    private static boolean isEditable(String status) {
        String s = status == null ? "" : status.trim();
        return UpdateStatus.STATUS_NEW.equals(s) || UpdateStatus.STATUS_PENDING.equals(s);
    }

    /** What the Add/Edit form holds once its text and choices have been read and checked. */
    private record Terms(String rateType, String compound, String frequency,
            double principal, double rate, double years) {
        int payments() {
            return (int) Math.max(1, Math.round(years * PAYMENTS_PER_YEAR.get(frequency)));
        }
    }

    /** Reads the form. Throws a ValidationException listing every problem found. */
    private static Terms parseTerms(LoanApplication application, ValidationResult result)
            throws ValidationException {
        if (application == null) {
            result.addError("loan", "Loan details are required");
            result.throwIfInvalid();
        }
        String rateType = application.interestOnly()
                ? Create.RATETYPE_INTERESTONLY
                : codeFor(LOAN_TYPES, application.loanType(), result, "loanType",
                        "Loan type must be selected");
        String compound = codeFor(COMPOUND_TYPES, application.compoundType(), result,
                "compoundType", "Compound type must be selected");
        String frequency = codeFor(PAYMENT_FREQUENCIES, application.paymentFrequency(), result,
                "paymentFrequency", "Payment frequency must be selected");
        Double principal = parseNumber(result, "principal", application.amount(), "Amount");
        Double rate = parseNumber(result, "annualRate", application.rate(), "Rate");
        Double years = parseNumber(result, "term", application.term(), "Loan term");
        if (years != null && years <= 0) {
            result.addError("term", "Term must be greater than zero");
        }
        result.throwIfInvalid();
        return new Terms(rateType, compound, frequency, principal, rate, years);
    }

    private static long months(double years) {
        return Math.max(1, Math.round(years * 12));
    }

    /** The loan term in years, worked out from the number of payments; null if unknown. */
    private static Double termYears(Long numberOfPayments, String paymentFrequencyCode) {
        Integer perYear = paymentFrequencyCode == null ? null : PAYMENTS_PER_YEAR.get(paymentFrequencyCode);
        if (numberOfPayments == null || numberOfPayments <= 0 || perYear == null) {
            return null;
        }
        return numberOfPayments / (double) perYear;
    }

    private static String termText(Double years) {
        if (years == null) {
            return null;
        }
        if (years == Math.rint(years)) {
            long whole = years.longValue();
            return whole + (whole == 1 ? " year" : " years");
        }
        return String.format("%.1f years", years);
    }

    /** A number as plain text for a form field (no exponent, no trailing zeros); empty if null. */
    private static String plain(Double value) {
        return value == null ? "" : BigDecimal.valueOf(value).stripTrailingZeros().toPlainString();
    }

    private static Map<String, String> labels(String... pairs) {
        Map<String, String> map = new LinkedHashMap<>();
        for (int i = 0; i < pairs.length; i += 2) {
            map.put(pairs[i], pairs[i + 1]);
        }
        return Collections.unmodifiableMap(map);
    }
}