package business.system;

import business.Main;
import business.lavsexceptions.LavsException;
import business.validation.ValidationException;
import business.validation.ValidationResult;
import dataaccess.systemadapters.MainframeAdapter;
import mainframe.client.Status;
import mainframe.client.messages.customer.AddAddress;
import mainframe.client.messages.customer.Create;
import mainframe.client.messages.customer.Search;

import java.io.IOException;

/**
 * Business service for adding customers through the mainframe adapter.
 */
public class Customer {
    private final MainframeAdapter customerAdapter;

    public Customer(MainframeAdapter customerAdapter) {
        if (customerAdapter == null) {
            throw new IllegalArgumentException("Customer adapter cannot be null");
        }
        this.customerAdapter = customerAdapter;
    }

    /**
     * Adds a customer after validating all customer fields.
     *
     * @return the mainframe message containing the created customer's details
     * @throws ValidationException if a field is null, blank, or invalid
     * @throws LavsException if the mainframe rejects the request
     * @throws IOException if the mainframe cannot be reached
     */
    public Create addCustomer(
            String dateOfBirth,
            String familyName,
            String firstName,
            String nationality,
            Long organisation,
            String title) throws IOException, LavsException {
        validateCustomerDetails(
                dateOfBirth, familyName, firstName, nationality, organisation, title);

        Create createMessage = new Create(
                dateOfBirth.trim(),
                familyName.trim(),
                firstName.trim(),
                nationality.trim(),
                organisation,
                title.trim());
        Status status = customerAdapter.send(createMessage);
        Main.isValidStatus(status);
        return createMessage;
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
        validateAddressDetails(
                city, country, customer, line1, otherType, postCode, suburb, type);

        AddAddress addMessage = new AddAddress(
                city.trim(),
                country.trim(),
                customer.trim(),
                isMailing,
                isPrimary,
                line1.trim(),
                line2 == null ? null : line2.trim(),
                otherType == null ? null : otherType.trim(),
                postCode.trim(),
                suburb.trim(),
                type.trim());
        Status status = customerAdapter.send(addMessage);
        Main.isValidStatus(status);
        return addMessage;
    }

    /**
     * Searches for customers using the mainframe's paginated customer search.
     * An empty search string returns customers without filtering by name.
     *
     * @param page the one-based page number
     * @param search the customer search text, or an empty string for all customers
     * @param size the maximum number of customers to return
     * @return the customers returned for the requested page
     * @throws ValidationException if a required parameter is missing or invalid
     * @throws LavsException if the mainframe rejects the request
     * @throws IOException if the mainframe cannot be reached
     */
    public Search.Item[] listCustomers(Long page, String search, Long size)
            throws IOException, LavsException {
        validateCustomerSearchDetails(page, search, size);

        Search searchMessage = new Search(page, search.trim(), size);
        Status status = customerAdapter.send(searchMessage);
        Main.isValidStatus(status);
        return searchMessage.getItems();
    }

    private void validateCustomerSearchDetails(Long page, String search, Long size)
            throws ValidationException {
        ValidationResult result = new ValidationResult();
        if (page == null) {
            result.addError("page", "Page cannot be empty");
        } else if (page < 1) {
            result.addError("page", "Page must be a positive integer");
        }
        if (search == null) {
            result.addError("search", "Search cannot be null");
        }
        if (size == null) {
            result.addError("size", "Size cannot be empty");
        } else if (size < 1) {
            result.addError("size", "Size must be a positive integer");
        }
        if (!result.isValid()) {
            throw new ValidationException(result);
        }
    }

    public void validateAddressDetails(
            String city,
            String country,
            String customer,
            String line1,
            String otherType,
            String postCode,
            String suburb,
            String type) throws ValidationException {
        ValidationResult result = new ValidationResult()
                .requireNotBlank("city", city, "City cannot be empty")
                .requireNotBlank("country", country, "Country cannot be empty")
                .requireNotBlank("customer", customer, "Customer cannot be empty")
                .requireNotBlank("line1", line1, "Address line 1 cannot be empty")
                .requireNotBlank("postCode", postCode, "Post code cannot be empty")
                .requireNotBlank("suburb", suburb, "Suburb cannot be empty")
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

        if (!result.isValid()) {
            throw new ValidationException(result);
        }
    }

    public void validateCustomerDetails(
            String dateOfBirth,
            String familyName,
            String firstName,
            String nationality,
            Long organisation,
            String title) throws ValidationException {
        ValidationResult result = new ValidationResult()
                .requireNotBlank("dateOfBirth", dateOfBirth,
                        "Date of birth cannot be empty")
                .requireNotBlank("familyName", familyName,
                        "Family name cannot be empty")
                .requireNotBlank("firstName", firstName,
                        "First name cannot be empty")
                .requireNotBlank("nationality", nationality,
                        "Nationality cannot be empty")
                .requireNotBlank("title", title,
                        "Title cannot be empty");

        if (organisation == null) {
            result.addError("organisation", "Organisation cannot be empty");
        } else if (organisation < 1) {
            result.addError("organisation", "Organisation must be a positive integer");
        }

        if (!result.isValid()) {
            throw new ValidationException(result);
        }
    }
}
