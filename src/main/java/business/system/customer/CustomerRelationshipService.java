package business.system.customer;

import java.io.IOException;

import business.Main;
import business.lavsexceptions.LavsException;
import business.validation.ValidationException;
import business.validation.ValidationResult;
import dataaccess.systemadapters.MainframeAdapter;
import mainframe.client.Message;
import mainframe.client.Status;
import mainframe.client.messages.customer.AddRelationship;
import mainframe.client.messages.customer.DeleteRelationship;
import mainframe.client.messages.customer.UpdateRelationship;
import mainframe.client.messages.customerRelationship.List;
import mainframe.client.messages.customerRelationship.Retrieve;

/**
 * Business service for managing the relationships between customers through the mainframe adapter.
 * A relationship links a customer to a secondary customer with a type such as parent or spouse.
 */
public class CustomerRelationshipService {
    private final MainframeAdapter adapter;

    public CustomerRelationshipService(MainframeAdapter adapter) {
        if (adapter == null) {
            throw new IllegalArgumentException("Relationship adapter cannot be null");
        }
        this.adapter = adapter;
    }

    /**
     * Adds a relationship from a customer to another customer after validating the fields.
     *
     * @param customer the customer's unique number
     * @param otherType the custom relationship type, required only when the type is Other
     * @param secondaryCustomerNumber the related customer's unique number
     * @param type the relationship type
     * @return the mainframe message containing the created relationship details
     * @throws ValidationException if a required field is missing or invalid
     * @throws LavsException if the mainframe rejects the request
     * @throws IOException if the mainframe cannot be reached
     */
    public AddRelationship addRelationship(
            String customer, String otherType, String secondaryCustomerNumber, String type)
            throws IOException, LavsException {
        buildRelationshipValidation(customer, otherType, secondaryCustomerNumber, type)
                .requireNotBlank("customer", customer, "Customer cannot be empty")
                .throwIfInvalid();

        AddRelationship addMessage = new AddRelationship(
                customer.trim(),
                otherTypeFor(type, otherType),
                secondaryCustomerNumber.trim(),
                type.trim());
        send(addMessage);
        return addMessage;
    }

    /**
     * Updates an existing relationship of a customer after validating the fields.
     *
     * @param customer the customer's unique number
     * @param id the relationship identifier
     * @param otherType the custom relationship type, required only when the type is Other
     * @param secondaryCustomerNumber the related customer's unique number
     * @param type the relationship type
     * @return the mainframe message containing the updated relationship details
     * @throws ValidationException if the relationship id or a required field is invalid
     * @throws LavsException if the mainframe rejects the request
     * @throws IOException if the mainframe cannot be reached
     */
    public UpdateRelationship updateRelationship(String customer, Long id, String otherType,
            String secondaryCustomerNumber, String type) throws IOException, LavsException {
        buildRelationshipValidation(customer, otherType, secondaryCustomerNumber, type)
                .requireNotBlank("customer", customer, "Customer cannot be empty")
                .requirePositive("relationshipId", id, "Relationship id")
                .throwIfInvalid();

        UpdateRelationship updateMessage = new UpdateRelationship(
                customer.trim(),
                id,
                otherTypeFor(type, otherType),
                secondaryCustomerNumber.trim(),
                type.trim());
        send(updateMessage);
        return updateMessage;
    }

    /**
     * Deletes a relationship belonging to a customer.
     *
     * @param customerNumber the customer's unique number
     * @param relationshipId the relationship identifier
     * @return the mainframe message for the deleted relationship
     * @throws ValidationException if either identifier is missing or invalid
     * @throws LavsException if the mainframe rejects the request
     * @throws IOException if the mainframe cannot be reached
     */
    public DeleteRelationship deleteRelationship(String customerNumber, Long relationshipId)
            throws IOException, LavsException {
        validateRelationshipIdentifiers(customerNumber, relationshipId);

        DeleteRelationship deleteMessage = new DeleteRelationship(customerNumber.trim(), relationshipId);
        send(deleteMessage);
        return deleteMessage;
    }

    /**
     * Lists a customer's relationships using the mainframe's paginated operation.
     * Items only carry the secondary customer number; use
     * {@link #retrieveRelationship(String, Long)} for the related customer's name.
     *
     * @param customerNumber the customer's unique number
     * @param page the one-based page number
     * @param size the maximum number of relationships to return
     * @return the relationships returned for the requested page
     * @throws ValidationException if the customer number, page, or size is missing or invalid
     * @throws IOException if the mainframe cannot be reached
     * @throws LavsException if the mainframe rejects the request
     */
    public List.Item[] listRelationships(String customerNumber, Long page, Long size)
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
     * Retrieves one relationship belonging to a customer, including the related customer's name.
     *
     * @param customerNumber the customer's unique number
     * @param relationshipId the relationship identifier
     * @return the retrieved relationship and its full details
     * @throws ValidationException if either identifier is missing or invalid
     * @throws IOException if the mainframe cannot be reached
     * @throws LavsException if the mainframe rejects the request
     */
    public Retrieve retrieveRelationship(String customerNumber, Long relationshipId)
            throws IOException, LavsException {
        validateRelationshipIdentifiers(customerNumber, relationshipId);

        Retrieve retrieveMessage = new Retrieve(relationshipId, customerNumber.trim());
        send(retrieveMessage);
        return retrieveMessage;
    }

    /**
     * Validates the fields of a relationship without sending it to the mainframe.
     * Static so the UI can check a form before sending it.
     *
     * @throws ValidationException if a field is missing or invalid
     */
    public static void validateRelationshipDetails(String customer, String otherType,
            String secondaryCustomerNumber, String type) throws ValidationException {
        buildRelationshipValidation(customer, otherType, secondaryCustomerNumber, type)
                .throwIfInvalid();
    }

    private static ValidationResult buildRelationshipValidation(
            String customer, String otherType, String secondaryCustomerNumber, String type) {
        ValidationResult result = new ValidationResult()
                .requireNotBlank("secondaryCustomerNumber", secondaryCustomerNumber,
                        "Related customer cannot be empty")
                .requireNotBlank("type", type, "Relationship type cannot be empty");

        if (type != null && !type.isBlank()
                && !CustomerOptions.RELATIONSHIP_TYPES.contains(type.trim())) {
            result.addError("type", "Relationship type is invalid");
        }
        if (isOtherType(type) && (otherType == null || otherType.isBlank())) {
            result.addError("otherType", "Other relationship type cannot be empty");
        }
        if (customer != null && !customer.isBlank() && secondaryCustomerNumber != null
                && customer.trim().equalsIgnoreCase(secondaryCustomerNumber.trim())) {
            result.addError("secondaryCustomerNumber", "A customer cannot be related to themselves");
        }
        return result;
    }

    private static boolean isOtherType(String type) {
        return type != null && CustomerOptions.RELATIONSHIP_TYPE_OTHER.equals(type.trim());
    }

    // only Other relationships keep their custom text so a stale value is never stored
    private static String otherTypeFor(String type, String otherType) {
        return isOtherType(type) ? otherType.trim() : null;
    }

    private void validateRelationshipIdentifiers(String customerNumber, Long relationshipId)
            throws ValidationException {
        requireCustomerNumber(new ValidationResult(), customerNumber)
                .requirePositive("relationshipId", relationshipId, "Relationship id")
                .throwIfInvalid();
    }

    private static ValidationResult requireCustomerNumber(
            ValidationResult result, String customerNumber) {
        return result.requireNotBlank("customerNumber", customerNumber,
                "Customer number cannot be null or empty");
    }

    private void send(Message message) throws IOException, LavsException {
        Status status = adapter.send(message);
        Main.isValidStatus(status);
    }
}
