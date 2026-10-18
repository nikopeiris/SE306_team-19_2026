package business.system.customer;

import java.io.IOException;

import business.Main;
import business.lavsexceptions.LavsException;
import business.validation.ValidationException;
import business.validation.ValidationResult;
import dataaccess.systemadapters.MainframeAdapter;
import mainframe.client.Message;
import mainframe.client.Status;
import mainframe.client.messages.customer.AddPhoneNumber;
import mainframe.client.messages.customer.DeletePhoneNumber;
import mainframe.client.messages.customer.UpdatePhoneNumber;
import mainframe.client.messages.customerPhoneNumber.List;
import mainframe.client.messages.customerPhoneNumber.Retrieve;

/**
 * Business service for managing a customer's phone numbers through the mainframe adapter.
 */
public class CustomerPhoneNumberService {
    private final MainframeAdapter adapter;

    public CustomerPhoneNumberService(MainframeAdapter adapter) {
        if (adapter == null) {
            throw new IllegalArgumentException("Phone number adapter cannot be null");
        }
        this.adapter = adapter;
    }

    /**
     * Adds a phone number to an existing customer after validating the phone fields.
     *
     * @return the mainframe message containing the created phone number details
     * @throws ValidationException if a required field or the phone type is invalid
     * @throws LavsException if the mainframe rejects the request
     * @throws IOException if the mainframe cannot be reached
     */
    public AddPhoneNumber addPhoneNumber(
            boolean canTxt,
            String countryCode,
            String customer,
            boolean isPrimary,
            String number,
            String otherType,
            String type) throws IOException, LavsException {
        validatePhoneNumberDetails(countryCode, customer, number, otherType, type);

        AddPhoneNumber addMessage = new AddPhoneNumber(
                canTxt,
                countryCode.trim(),
                customer.trim(),
                isPrimary,
                number.trim(),
                trimOrNull(otherType),
                type.trim());
        send(addMessage);
        return addMessage;
    }

    /**
     * Updates an existing phone number of a customer after validating the phone fields.
     *
     * @return the mainframe message containing the updated phone number details
     * @throws ValidationException if the phone number id, a required field or the phone type is invalid
     * @throws LavsException if the mainframe rejects the request
     * @throws IOException if the mainframe cannot be reached
     */
    public UpdatePhoneNumber updatePhoneNumber(
            boolean canTxt,
            String countryCode,
            String customer,
            Long id,
            boolean isPrimary,
            String number,
            String otherType,
            String type) throws IOException, LavsException {
        buildPhoneNumberValidation(countryCode, customer, number, otherType, type)
                .requirePositive("phoneNumberId", id, "Phone number id")
                .throwIfInvalid();

        UpdatePhoneNumber updateMessage = new UpdatePhoneNumber(
                canTxt,
                countryCode.trim(),
                customer.trim(),
                id,
                isPrimary,
                number.trim(),
                trimOrNull(otherType),
                type.trim());
        send(updateMessage);
        return updateMessage;
    }

    /**
     * Deletes a phone number belonging to a customer.
     *
     * @param customerNumber the customer's unique number
     * @param phoneNumberId the phone number identifier
     * @return the mainframe message for the deleted phone number
     * @throws ValidationException if either identifier is missing or invalid
     * @throws LavsException if the mainframe rejects the request
     * @throws IOException if the mainframe cannot be reached
     */
    public DeletePhoneNumber deletePhoneNumber(String customerNumber, Long phoneNumberId)
            throws IOException, LavsException {
        validatePhoneNumberIdentifiers(customerNumber, phoneNumberId);

        DeletePhoneNumber deleteMessage =
                new DeletePhoneNumber(customerNumber.trim(), phoneNumberId);
        send(deleteMessage);
        return deleteMessage;
    }

    /**
     * Lists a customer's phone numbers using the mainframe's paginated operation.
     *
     * @param customerNumber the customer's unique number
     * @param page the one-based page number
     * @param size the maximum number of phone numbers to return
     * @return the phone numbers returned for the requested page
     * @throws ValidationException if the customer number, page, or size is missing or invalid
     * @throws IOException if the mainframe cannot be reached
     * @throws LavsException if the mainframe rejects the request
     */
    public List.Item[] listPhoneNumbers(String customerNumber, Long page, Long size)
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
     * Retrieves one phone number belonging to a customer.
     *
     * @param customerNumber the customer's unique number
     * @param phoneNumberId the phone number identifier
     * @return the retrieved phone number and its full details
     * @throws ValidationException if either identifier is missing or invalid
     * @throws IOException if the mainframe cannot be reached
     * @throws LavsException if the mainframe rejects the request
     */
    public Retrieve retrievePhoneNumber(String customerNumber, Long phoneNumberId)
            throws IOException, LavsException {
        validatePhoneNumberIdentifiers(customerNumber, phoneNumberId);

        Retrieve retrieveMessage = new Retrieve(phoneNumberId, customerNumber.trim());
        send(retrieveMessage);
        return retrieveMessage;
    }

    /**
     * Validates the fields of a phone number without sending it to the mainframe.
     * Static so the UI can check a form before a customer (and their number) exists.
     *
     * @throws ValidationException if a required field or the phone type is invalid
     */
    public static void validatePhoneNumberDetails(
            String countryCode,
            String customer,
            String number,
            String otherType,
            String type) throws ValidationException {
        buildPhoneNumberValidation(countryCode, customer, number, otherType, type)
                .throwIfInvalid();
    }

    private static ValidationResult buildPhoneNumberValidation(
            String countryCode,
            String customer,
            String number,
            String otherType,
            String type) {
        ValidationResult result = new ValidationResult()
                .requireNotBlank("countryCode", countryCode, "Country code cannot be empty")
                .requireNotBlank("customer", customer, "Customer cannot be empty")
                .requireNotBlank("number", number, "Phone number cannot be empty")
                .requireNotBlank("type", type, "Phone type cannot be empty");

        if (type != null && !type.isBlank()
                && !type.equals(AddPhoneNumber.TYPE_HOME)
                && !type.equals(AddPhoneNumber.TYPE_MOBILE)
                && !type.equals(AddPhoneNumber.TYPE_WORK)
                && !type.equals(AddPhoneNumber.TYPE_OTHER)) {
            result.addError("type", "Phone type is invalid");
        }
        if (AddPhoneNumber.TYPE_OTHER.equals(type)
                && (otherType == null || otherType.isBlank())) {
            result.addError("otherType", "Other phone type cannot be empty");
        }
        return result;
    }

    private void validatePhoneNumberIdentifiers(String customerNumber, Long phoneNumberId)
            throws ValidationException {
        requireCustomerNumber(new ValidationResult(), customerNumber)
                .requirePositive("phoneNumberId", phoneNumberId, "Phone number id")
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
