package business.system.customer;

import java.io.IOException;
import java.util.regex.Pattern;

import business.Main;
import business.lavsexceptions.LavsException;
import business.validation.ValidationException;
import business.validation.ValidationResult;
import dataaccess.systemadapters.MainframeAdapter;
import mainframe.client.Message;
import mainframe.client.Status;
import mainframe.client.messages.customer.AddEmail;
import mainframe.client.messages.customer.DeleteEmail;
import mainframe.client.messages.customer.UpdateEmail;
import mainframe.client.messages.customerEmail.List;
import mainframe.client.messages.customerEmail.Retrieve;

/**
 * Business service for managing a customer's email addresses through the mainframe adapter.
 */
public class CustomerEmailService {
    private static final long PRIMARY_EMAIL_SEARCH_SIZE = 100L;

    // something@something.something, with no spaces and a single @
    private static final Pattern EMAIL_FORMAT = Pattern.compile("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$");

    private final MainframeAdapter adapter;

    public CustomerEmailService(MainframeAdapter adapter) {
        if (adapter == null) {
            throw new IllegalArgumentException("Email adapter cannot be null");
        }
        this.adapter = adapter;
    }

    /**
     * Adds an email address to an existing customer after validating the email fields.
     *
     * @return the mainframe message containing the created email details
     * @throws ValidationException if a required field, the address format or the email type is invalid
     * @throws LavsException if the mainframe rejects the request
     * @throws IOException if the mainframe cannot be reached
     */
    public AddEmail addEmail(
            String address,
            String customer,
            boolean isPrimary,
            String otherType,
            String type) throws IOException, LavsException {
        validateEmailDetails(address, customer, otherType, type);

        AddEmail addMessage = new AddEmail(
                address.trim(),
                customer.trim(),
                isPrimary,
                trimOrNull(otherType),
                type.trim());
        send(addMessage);
        return addMessage;
    }

    /**
     * Updates an existing email address of a customer after validating the email fields.
     *
     * @return the mainframe message containing the updated email details
     * @throws ValidationException if the email id, a required field, the address format
     *         or the email type is invalid
     * @throws LavsException if the mainframe rejects the request
     * @throws IOException if the mainframe cannot be reached
     */
    public UpdateEmail updateEmail(
            String address,
            String customer,
            Long id,
            boolean isPrimary,
            String otherType,
            String type) throws IOException, LavsException {
        buildEmailValidation(address, customer, otherType, type)
                .requirePositive("emailId", id, "Email id")
                .throwIfInvalid();

        UpdateEmail updateMessage = new UpdateEmail(
                address.trim(),
                customer.trim(),
                id,
                isPrimary,
                trimOrNull(otherType),
                type.trim());
        send(updateMessage);
        return updateMessage;
    }

    /**
     * Deletes an email address belonging to a customer.
     *
     * @param customerNumber the customer's unique number
     * @param emailId the email identifier
     * @return the mainframe message for the deleted email
     * @throws ValidationException if either identifier is missing or invalid
     * @throws LavsException if the mainframe rejects the request
     * @throws IOException if the mainframe cannot be reached
     */
    public DeleteEmail deleteEmail(String customerNumber, Long emailId)
            throws IOException, LavsException {
        validateEmailIdentifiers(customerNumber, emailId);

        DeleteEmail deleteMessage = new DeleteEmail(customerNumber.trim(), emailId);
        send(deleteMessage);
        return deleteMessage;
    }

    /**
     * Lists a customer's email addresses using the mainframe's paginated operation.
     *
     * @param customerNumber the customer's unique number
     * @param page the one-based page number
     * @param size the maximum number of emails to return
     * @return the emails returned for the requested page
     * @throws ValidationException if the customer number, page, or size is missing or invalid
     * @throws IOException if the mainframe cannot be reached
     * @throws LavsException if the mainframe rejects the request
     */
    public List.Item[] listEmails(String customerNumber, Long page, Long size)
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
     * Retrieves one email address belonging to a customer.
     *
     * @param customerNumber the customer's unique number
     * @param emailId the email identifier
     * @return the retrieved email and its full details
     * @throws ValidationException if either identifier is missing or invalid
     * @throws IOException if the mainframe cannot be reached
     * @throws LavsException if the mainframe rejects the request
     */
    public Retrieve retrieveEmail(String customerNumber, Long emailId)
            throws IOException, LavsException {
        validateEmailIdentifiers(customerNumber, emailId);

        Retrieve retrieveMessage = new Retrieve(emailId, customerNumber.trim());
        send(retrieveMessage);
        return retrieveMessage;
    }

    /**
     * Returns the customer's primary email address from the first page of their emails,
     * or an empty string if they have no email marked as primary.
     *
     * @param customerNumber the customer's unique number
     * @throws ValidationException if the customer number is missing
     * @throws IOException if the mainframe cannot be reached
     * @throws LavsException if the mainframe rejects the request
     */
    public String findPrimaryEmail(String customerNumber)
            throws IOException, LavsException {
        List.Item[] emails = listEmails(customerNumber, 1L, PRIMARY_EMAIL_SEARCH_SIZE);
        for (List.Item email : emails) {
            if ("y".equals(email.getIsPrimary())) {
                return email.getAddress() == null ? "" : email.getAddress();
            }
        }
        return "";
    }

    /**
     * Validates the fields of an email address without sending it to the mainframe.
     * Static so the UI can check a form before a customer (and their number) exists.
     *
     * @throws ValidationException if a required field, the address format or the email type is invalid
     */
    public static void validateEmailDetails(
            String address,
            String customer,
            String otherType,
            String type) throws ValidationException {
        buildEmailValidation(address, customer, otherType, type).throwIfInvalid();
    }

    private static ValidationResult buildEmailValidation(
            String address,
            String customer,
            String otherType,
            String type) {
        ValidationResult result = new ValidationResult()
                .requireNotBlank("address", address, "Email address cannot be empty")
                .requireNotBlank("customer", customer, "Customer cannot be empty")
                .requireNotBlank("type", type, "Email type cannot be empty");

        if (address != null && !address.isBlank()
                && !EMAIL_FORMAT.matcher(address.trim()).matches()) {
            result.addError("address", "Email address is invalid");
        }
        if (type != null && !type.isBlank()
                && !type.equals(AddEmail.TYPE_PERSONAL)
                && !type.equals(AddEmail.TYPE_WORK)
                && !type.equals(AddEmail.TYPE_OTHER)) {
            result.addError("type", "Email type is invalid");
        }
        if (AddEmail.TYPE_OTHER.equals(type)
                && (otherType == null || otherType.isBlank())) {
            result.addError("otherType", "Other email type cannot be empty");
        }
        return result;
    }

    private void validateEmailIdentifiers(String customerNumber, Long emailId)
            throws ValidationException {
        requireCustomerNumber(new ValidationResult(), customerNumber)
                .requirePositive("emailId", emailId, "Email id")
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
