package business.system.customer;

import java.io.IOException;

import business.Main;
import business.lavsexceptions.LavsException;
import business.validation.ValidationException;
import business.validation.ValidationResult;
import dataaccess.systemadapters.MainframeAdapter;
import mainframe.client.Message;
import mainframe.client.Status;
import mainframe.client.messages.customer.AddAddress;
import mainframe.client.messages.customer.DeleteAddress;
import mainframe.client.messages.customer.UpdateAddress;
import mainframe.client.messages.customerAddress.List;
import mainframe.client.messages.customerAddress.Retrieve;

/**
 * Business service for managing a customer's addresses through the mainframe adapter.
 * An address only requires a type, line 1 and a country.
 */
public class CustomerAddressService {
    private static final long MAILING_ADDRESS_SEARCH_SIZE = 100L;

    private final MainframeAdapter adapter;

    public CustomerAddressService(MainframeAdapter adapter) {
        if (adapter == null) {
            throw new IllegalArgumentException("Address adapter cannot be null");
        }
        this.adapter = adapter;
    }

    /**
     * Adds an address to an existing customer after validating the address fields.
     *
     * @return the mainframe message containing the created address
     * @throws ValidationException if a required field or address type is invalid
     * @throws LavsException if the mainframe rejects the request
     * @throws IOException if the mainframe cannot be reached
     */
    public AddAddress addAddress(
            String city,
            String country,
            String customer,
            boolean isMailing,
            boolean isPrimary,
            String line1,
            String line2,
            String otherType,
            String postCode,
            String suburb,
            String type) throws IOException, LavsException {
        buildAddressValidation(country, line1, otherType, type)
                .requireNotBlank("customer", customer, "Customer cannot be empty")
                .throwIfInvalid();

        AddAddress addMessage = new AddAddress(
                trimOrNull(city),
                country.trim(),
                customer.trim(),
                isMailing,
                isPrimary,
                line1.trim(),
                trimOrNull(line2),
                trimOrNull(otherType),
                trimOrNull(postCode),
                trimOrNull(suburb),
                type.trim());
        send(addMessage);
        return addMessage;
    }

    /**
     * Updates an existing address of a customer after validating the address fields.
     *
     * @return the mainframe message containing the updated address
     * @throws ValidationException if the address id, a required field or the address type is invalid
     * @throws LavsException if the mainframe rejects the request
     * @throws IOException if the mainframe cannot be reached
     */
    public UpdateAddress updateAddress(
            String city,
            String country,
            String customer,
            Long id,
            boolean isMailing,
            boolean isPrimary,
            String line1,
            String line2,
            String otherType,
            String postCode,
            String suburb,
            String type) throws IOException, LavsException {
        buildAddressValidation(country, line1, otherType, type)
                .requireNotBlank("customer", customer, "Customer cannot be empty")
                .requirePositive("addressId", id, "Address id")
                .throwIfInvalid();

        UpdateAddress updateMessage = new UpdateAddress(
                trimOrNull(city),
                country.trim(),
                customer.trim(),
                id,
                isMailing,
                isPrimary,
                line1.trim(),
                trimOrNull(line2),
                trimOrNull(otherType),
                trimOrNull(postCode),
                trimOrNull(suburb),
                type.trim());
        send(updateMessage);
        return updateMessage;
    }

    /**
     * Deletes an address belonging to a customer.
     *
     * @param customerNumber the customer's unique number
     * @param addressId the address identifier
     * @return the mainframe message for the deleted address
     * @throws ValidationException if either identifier is missing or invalid
     * @throws LavsException if the mainframe rejects the request
     * @throws IOException if the mainframe cannot be reached
     */
    public DeleteAddress deleteAddress(String customerNumber, Long addressId)
            throws IOException, LavsException {
        validateAddressIdentifiers(customerNumber, addressId);

        DeleteAddress deleteMessage = new DeleteAddress(customerNumber.trim(), addressId);
        send(deleteMessage);
        return deleteMessage;
    }

    /**
     * Lists the addresses belonging to a customer using the mainframe's
     * paginated customer-address list operation.
     *
     * @param customerNumber the customer's unique number
     * @param page the one-based page number
     * @param size the maximum number of addresses to return
     * @return the addresses returned for the requested page
     * @throws ValidationException if the customer number, page, or size is missing or invalid
     * @throws IOException if the mainframe cannot be reached
     * @throws LavsException if the mainframe rejects the request
     */
    public List.Item[] listAddresses(String customerNumber, Long page, Long size)
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
     * Retrieves one address belonging to a customer.
     *
     * @param customerNumber the customer's unique number
     * @param addressId the address identifier
     * @return the retrieved address and its full details
     * @throws ValidationException if either identifier is missing or invalid
     * @throws IOException if the mainframe cannot be reached
     * @throws LavsException if the mainframe rejects the request
     */
    public Retrieve retrieveAddress(String customerNumber, Long addressId)
            throws IOException, LavsException {
        validateAddressIdentifiers(customerNumber, addressId);

        Retrieve retrieveMessage = new Retrieve(addressId, customerNumber.trim());
        send(retrieveMessage);
        return retrieveMessage;
    }

    /**
     * Finds the customer's mailing address from the first page of their addresses.
     *
     * @param customerNumber the customer's unique number
     * @return the mailing address, or null if the customer has none
     * @throws ValidationException if the customer number is missing
     * @throws IOException if the mainframe cannot be reached
     * @throws LavsException if the mainframe rejects the request
     */
    public List.Item findMailingAddress(String customerNumber)
            throws IOException, LavsException {
        List.Item[] addresses = listAddresses(customerNumber, 1L, MAILING_ADDRESS_SEARCH_SIZE);
        for (List.Item address : addresses) {
            if ("y".equals(address.getIsMailing())) {
                return address;
            }
        }
        return null;
    }

    /**
     * Validates the fields of an address without sending it to the mainframe.
     * Static so the UI can check a form before a customer (and their number) exists.
     * Only the type, line 1 and country are required; the other parameters are
     * accepted for compatibility with existing callers but are not checked.
     *
     * @throws ValidationException if a required field or the address type is invalid
     */
    public static void validateAddressDetails(
            String city,
            String country,
            String customer,
            String line1,
            String otherType,
            String postCode,
            String suburb,
            String type) throws ValidationException {
        buildAddressValidation(country, line1, otherType, type).throwIfInvalid();
    }

    private static ValidationResult buildAddressValidation(
            String country,
            String line1,
            String otherType,
            String type) {
        ValidationResult result = new ValidationResult()
                .requireNotBlank("country", country, "Country cannot be empty")
                .requireNotBlank("line1", line1, "Address line 1 cannot be empty")
                .requireNotBlank("type", type, "Address type cannot be empty");

        if (type != null && !type.isBlank()
                && !type.equals(AddAddress.TYPE_RESIDENTIAL)
                && !type.equals(AddAddress.TYPE_POSTAL)
                && !type.equals(AddAddress.TYPE_FORWARDING)
                && !type.equals(AddAddress.TYPE_DELEGATED)
                && !type.equals(AddAddress.TYPE_OTHER)) {
            result.addError("type", "Address type is invalid");
        }
        if (AddAddress.TYPE_OTHER.equals(type)
                && (otherType == null || otherType.isBlank())) {
            result.addError("otherType", "Other address type cannot be empty");
        }
        return result;
    }

    private void validateAddressIdentifiers(String customerNumber, Long addressId)
            throws ValidationException {
        requireCustomerNumber(new ValidationResult(), customerNumber)
                .requirePositive("addressId", addressId, "Address id")
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