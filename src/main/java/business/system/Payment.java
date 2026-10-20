package business.system;

import java.io.IOException;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;

import business.Main;
import business.lavsexceptions.LavsException;
import business.validation.ValidationException;
import business.validation.ValidationResult;
import dataaccess.systemadapters.MainframeAdapter;
import mainframe.client.Status;
import mainframe.client.messages.loan.AddPayment;
import mainframe.client.messages.loan.DeletePayment;
import mainframe.client.messages.loanPayment.List;

/**
 * Business service for adding and deleting loan payments.
 */
public class Payment {
    private final MainframeAdapter paymentAdapter;

    public Payment(MainframeAdapter paymentAdapter) {
        if (paymentAdapter == null) {
            throw new IllegalArgumentException("Payment adapter cannot be null");
        }
        this.paymentAdapter = paymentAdapter;
    }

    /**
     * Adds a payment to a loan after validating the payment details.
     *
     * @param amountPaid payment amount; must be finite and greater than zero
     * @param date payment date in yyyy-MM-dd format
     * @param loanNumber loan identifier
     * @return the created payment message
     * @throws ValidationException if a payment field is invalid
     * @throws LavsException if the mainframe rejects the request
     * @throws IOException if the mainframe cannot be reached
     */
    public AddPayment addPayment(double amountPaid, String date, String loanNumber)
            throws IOException, LavsException {
        validateAddPaymentDetails(amountPaid, date, loanNumber);

        AddPayment addPaymentMessage = new AddPayment(
                amountPaid,
                date.trim(),
                loanNumber.trim());
        Status status = paymentAdapter.send(addPaymentMessage);
        Main.isValidStatus(status);
        return addPaymentMessage;
    }

    /**
     * Deletes a payment from a loan after validating its identifiers.
     *
     * @param id payment identifier
     * @param loanNumber loan identifier
     * @return the deleted payment message
     * @throws ValidationException if a payment identifier is invalid
     * @throws LavsException if the mainframe rejects the request
     * @throws IOException if the mainframe cannot be reached
     */
    public DeletePayment deletePayment(Long id, String loanNumber)
            throws IOException, LavsException {
        validateDeletePaymentDetails(id, loanNumber);

        DeletePayment deletePaymentMessage = new DeletePayment(id, loanNumber.trim());
        Status status = paymentAdapter.send(deletePaymentMessage);
        Main.isValidStatus(status);
        return deletePaymentMessage;
    }

    /**
     * Retrieves a paginated list of payments recorded against a loan.
     *
     * @param loanNumber loan identifier
     * @param page one-based page number
     * @param size maximum number of payments to retrieve
     * @return the payments returned for the requested page
     * @throws ValidationException if the loan number, page, or size is invalid
     * @throws LavsException if the mainframe rejects the request
     * @throws IOException if the mainframe cannot be reached
     */
    public List.Item[] listPayments(String loanNumber, Long page, Long size)
            throws IOException, LavsException {
        validateListPaymentsDetails(loanNumber, page, size);

        List listMessage = new List(loanNumber.trim(), page, size);
        Status status = paymentAdapter.send(listMessage);
        Main.isValidStatus(status);
        return listMessage.getItems();
    }

    public void validateAddPaymentDetails(double amountPaid, String date, String loanNumber)
            throws ValidationException {
        ValidationResult result = new ValidationResult()
                .requireNotBlank("date", date, "Payment date cannot be null or empty")
                .requireNotBlank("loanNumber", loanNumber, "Loan number cannot be null or empty");

        if (!Double.isFinite(amountPaid) || amountPaid <= 0) {
            result.addError("amountPaid", "Payment amount must be finite and greater than zero");
        }
        validateDate(result, date);
        result.throwIfInvalid();
    }

    public void validateDeletePaymentDetails(Long id, String loanNumber)
            throws ValidationException {
        ValidationResult result = new ValidationResult()
                .requireNotBlank("loanNumber", loanNumber, "Loan number cannot be null or empty")
                .requirePositive("id", id, "Payment id");
        result.throwIfInvalid();
    }

    public void validateListPaymentsDetails(String loanNumber, Long page, Long size)
            throws ValidationException {
        ValidationResult result = new ValidationResult()
                .requireNotBlank("loanNumber", loanNumber, "Loan number cannot be null or empty")
                .requirePositive("page", page, "Page")
                .requirePositive("size", size, "Size");
        result.throwIfInvalid();
    }

    private void validateDate(ValidationResult result, String date) {
        if (date != null && !date.isBlank()) {
            try {
                LocalDate.parse(date.trim());
            } catch (DateTimeParseException exception) {
                result.addError("date", "Payment date must be in yyyy-MM-dd format");
            }
        }
    }
}
