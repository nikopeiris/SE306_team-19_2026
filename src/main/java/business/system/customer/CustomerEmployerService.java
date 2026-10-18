package business.system.customer;

import java.io.IOException;

import business.Main;
import business.lavsexceptions.LavsException;
import business.validation.ValidationException;
import business.validation.ValidationResult;
import dataaccess.systemadapters.MainframeAdapter;
import mainframe.client.Message;
import mainframe.client.Status;
import mainframe.client.messages.customer.AddEmployer;
import mainframe.client.messages.customer.DeleteEmployer;
import mainframe.client.messages.customer.UpdateEmployer;
import mainframe.client.messages.customerEmployer.List;
import mainframe.client.messages.customerEmployer.Retrieve;

/**
 * Business service for managing a customer's employers through the mainframe adapter.
 * No employer field is required; a salary or type, if given, must still be valid.
 */
public class CustomerEmployerService {
    /** Employer types, re-exported so the UI does not depend on mainframe messages. */
    public static final String TYPE_PRIMARY = AddEmployer.TYPE_PRIMARY;
    public static final String TYPE_SECONDARY = AddEmployer.TYPE_SECONDARY;

    private final MainframeAdapter adapter;

    public CustomerEmployerService(MainframeAdapter adapter) {
        if (adapter == null) {
            throw new IllegalArgumentException("Employer adapter cannot be null");
        }
        this.adapter = adapter;
    }

    /**
     * Adds an employer to an existing customer after validating the employer fields.
     *
     * @return the mainframe message containing the created employer details
     * @throws ValidationException if the salary or the employer type is invalid
     * @throws LavsException if the mainframe rejects the request
     * @throws IOException if the mainframe cannot be reached
     */
    public AddEmployer addEmployer(
            String customer,
            String name,
            String position,
            Double salary,
            String type) throws IOException, LavsException {
        buildEmployerValidation(salary, type)
                .requireNotBlank("customer", customer, "Customer cannot be empty")
                .throwIfInvalid();

        AddEmployer addMessage = new AddEmployer(
                customer.trim(),
                trimOrNull(name),
                trimOrNull(position),
                salary,
                trimOrNull(type));
        send(addMessage);
        return addMessage;
    }

    /**
     * Updates an existing employer of a customer after validating the employer fields.
     *
     * @return the mainframe message containing the updated employer details
     * @throws ValidationException if the employer id, the salary or the employer type is invalid
     * @throws LavsException if the mainframe rejects the request
     * @throws IOException if the mainframe cannot be reached
     */
    public UpdateEmployer updateEmployer(
            String customer,
            Long id,
            String name,
            String position,
            Double salary,
            String type) throws IOException, LavsException {
        buildEmployerValidation(salary, type)
                .requireNotBlank("customer", customer, "Customer cannot be empty")
                .requirePositive("employerId", id, "Employer id")
                .throwIfInvalid();

        UpdateEmployer updateMessage = new UpdateEmployer(
                customer.trim(),
                id,
                trimOrNull(name),
                trimOrNull(position),
                salary,
                trimOrNull(type));
        send(updateMessage);
        return updateMessage;
    }

    /**
     * Deletes an employer belonging to a customer.
     *
     * @param customerNumber the customer's unique number
     * @param employerId the employer identifier
     * @return the mainframe message for the deleted employer
     * @throws ValidationException if either identifier is missing or invalid
     * @throws LavsException if the mainframe rejects the request
     * @throws IOException if the mainframe cannot be reached
     */
    public DeleteEmployer deleteEmployer(String customerNumber, Long employerId)
            throws IOException, LavsException {
        validateEmployerIdentifiers(customerNumber, employerId);

        DeleteEmployer deleteMessage = new DeleteEmployer(customerNumber.trim(), employerId);
        send(deleteMessage);
        return deleteMessage;
    }

    /**
     * Lists a customer's employers using the mainframe's paginated operation.
     *
     * @param customerNumber the customer's unique number
     * @param page the one-based page number
     * @param size the maximum number of employers to return
     * @return the employers returned for the requested page
     * @throws ValidationException if the customer number, page, or size is missing or invalid
     * @throws IOException if the mainframe cannot be reached
     * @throws LavsException if the mainframe rejects the request
     */
    public List.Item[] listEmployers(String customerNumber, Long page, Long size)
            throws IOException, LavsException {
        requireCustomerNumber(new ValidationResult(), customerNumber)
                .requirePositive("page", page, "Page")
                .requirePositive("size", size, "Size")
                .throwIfInvalid();

        List listMessage = new List(customerNumber.trim(), page, size);
        send(listMessage);
        return listMessage.getItems();
    }

    /**
     * Retrieves one employer belonging to a customer.
     *
     * @param customerNumber the customer's unique number
     * @param employerId the employer identifier
     * @return the retrieved employer and its full details
     * @throws ValidationException if either identifier is missing or invalid
     * @throws IOException if the mainframe cannot be reached
     * @throws LavsException if the mainframe rejects the request
     */
    public Retrieve retrieveEmployer(String customerNumber, Long employerId)
            throws IOException, LavsException {
        validateEmployerIdentifiers(customerNumber, employerId);

        Retrieve retrieveMessage = new Retrieve(employerId, customerNumber.trim());
        send(retrieveMessage);
        return retrieveMessage;
    }

    /**
     * Validates the fields of an employer without sending it to the mainframe.
     * Static so the UI can check a form before a customer (and their number) exists.
     * Nothing is required; a salary or type, if given, must be valid. The customer,
     * name and position parameters are accepted for compatibility with existing
     * callers but are not checked.
     *
     * @throws ValidationException if the salary or the employer type is invalid
     */
    public static void validateEmployerDetails(
            String customer,
            String name,
            String position,
            Double salary,
            String type) throws ValidationException {
        buildEmployerValidation(salary, type).throwIfInvalid();
    }

    private static ValidationResult buildEmployerValidation(Double salary, String type) {
        ValidationResult result = new ValidationResult();

        if (salary != null && (!Double.isFinite(salary) || salary < 0)) {
            result.addError("salary", "Salary must be a non-negative number");
        }
        if (type != null && !type.isBlank()
                && !type.equals(TYPE_PRIMARY)
                && !type.equals(TYPE_SECONDARY)) {
            result.addError("type", "Employer type is invalid");
        }
        return result;
    }

    private void validateEmployerIdentifiers(String customerNumber, Long employerId)
            throws ValidationException {
        requireCustomerNumber(new ValidationResult(), customerNumber)
                .requirePositive("employerId", employerId, "Employer id")
                .throwIfInvalid();
    }

    private static ValidationResult requireCustomerNumber(
            ValidationResult result, String customerNumber) {
        return result.requireNotBlank("customerNumber", customerNumber,
                "Customer number cannot be null or empty");
    }

    private static String trimOrNull(String value) {
        return value == null ? null : value.trim();
    }

    private void send(Message message) throws IOException, LavsException {
        Status status = adapter.send(message);
        Main.isValidStatus(status);
    }
}