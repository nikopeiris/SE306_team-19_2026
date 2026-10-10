package business.system;

import business.lavsexceptions.LavsException;
import business.validation.ValidationException;
import business.validation.ValidationResult;
import dataaccess.systemadapters.MainframeAdapter;
import mainframe.client.Status;
import mainframe.client.messages.customer.Create;

import java.io.IOException;
import java.util.function.Function;

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
        Status status = customerAdapter.addCustomer(createMessage);
        if (!status.getWasSuccessful()) {
            throw new LavsException(status);
        }
        return createMessage;
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
                        "Date of birth cannot empty")
                .requireNotBlank("familyName", familyName,
                        "Family name cannot empty")
                .requireNotBlank("firstName", firstName,
                        "First name cannot empty")
                .requireNotBlank("nationality", nationality,
                        "Nationality cannot empty")
                .requireNotBlank("title", title,
                        "Title cannot empty");

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
