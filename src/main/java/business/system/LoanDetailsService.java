package business.system;

import java.io.IOException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import business.lavsexceptions.LavsException;
import business.system.dto.LoanApplication;
import business.system.dto.LoanDetail;
import business.system.dto.LoanRow;
import business.system.dto.RepaymentRow;
import business.validation.ValidationException;
import business.validation.ValidationResult;
import dataaccess.systemadapters.MainframeAdapter;
import mainframe.client.messages.loan.Create;
import mainframe.client.messages.loan.Retrieve;

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

    private final Loan loanService;

    public LoanDetailsService(MainframeAdapter adapter) {
        if (adapter == null) {
            throw new IllegalArgumentException("Loan details adapter cannot be null");
        }
        this.loanService = new Loan(adapter);
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
        ValidationResult result = new ValidationResult();
        if (application == null) {
            result.addError("loan", "Loan details are required");
            result.throwIfInvalid();
        }
        result.requireNotBlank("customerNumber", customerNumber,
                "Customer number cannot be null or empty");

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

        int payments = (int) Math.max(1, Math.round(years * PAYMENTS_PER_YEAR.get(frequency)));
        LocalDate start = LocalDate.now();
        LocalDate renewal = start.plusMonths(Math.max(1, Math.round(years * 12)));

        return loanService.addLoan(customerNumber, principal, rate, payments, rateType,
                frequency, compound, renewal.toString(), start.toString(), null);
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
        return new LoanDetail(
                number,
                loan.getCustomerName(),
                typeLabel(rateType),
                termText(summary == null ? null : summary.getNumberOfPayments(),
                        loan.getPaymentFrequency()),
                labelFor(COMPOUND_TYPES, loan.getRateFrequency()),
                loan.getPrincipal(),
                labelFor(PAYMENT_FREQUENCIES, loan.getPaymentFrequency()),
                Create.RATETYPE_INTERESTONLY.equals(rateType),
                loan.getRateAmount(),
                loan.getPaymentAmount(),
                loan.getRenewalDate(),
                interestPaid,
                principalPaid,
                loan.getStatus());
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

    // ------------------------------------------------------------------
    // Helpers
    // ------------------------------------------------------------------

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

    private static String termText(Long numberOfPayments, String paymentFrequencyCode) {
        Integer perYear = paymentFrequencyCode == null ? null : PAYMENTS_PER_YEAR.get(paymentFrequencyCode);
        if (numberOfPayments == null || numberOfPayments <= 0 || perYear == null) {
            return null;
        }
        double years = numberOfPayments / (double) perYear;
        if (years == Math.rint(years)) {
            long whole = (long) years;
            return whole + (whole == 1 ? " year" : " years");
        }
        return String.format("%.1f years", years);
    }

    private static Map<String, String> labels(String... pairs) {
        Map<String, String> map = new LinkedHashMap<>();
        for (int i = 0; i < pairs.length; i += 2) {
            map.put(pairs[i], pairs[i + 1]);
        }
        return Collections.unmodifiableMap(map);
    }
}