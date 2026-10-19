package business.system.customer;

import java.io.IOException;

import business.Main;
import business.lavsexceptions.LavsException;
import business.validation.ValidationException;
import business.validation.ValidationResult;
import dataaccess.systemadapters.MainframeAdapter;
import mainframe.client.Message;
import mainframe.client.Status;
import mainframe.client.messages.customer.AddNote;
import mainframe.client.messages.customer.DeleteNote;
import mainframe.client.messages.customer.UpdateNote;
import mainframe.client.messages.customerNote.List;
import mainframe.client.messages.customerNote.Retrieve;

/**
 * Business service for managing a customer's notes through the mainframe adapter.
 * A note only requires a type and its lines; the author and date added are set by the mainframe.
 */
public class CustomerNoteService {
    private final MainframeAdapter adapter;

    public CustomerNoteService(MainframeAdapter adapter) {
        if (adapter == null) {
            throw new IllegalArgumentException("Note adapter cannot be null");
        }
        this.adapter = adapter;
    }

    /**
     * Adds a note to an existing customer after validating the note fields.
     *
     * @param customer the customer's unique number
     * @param lines the note text, with lines separated by new lines
     * @param type the free-text note type
     * @return the mainframe message containing the created note details
     * @throws ValidationException if a required field is missing
     * @throws LavsException if the mainframe rejects the request
     * @throws IOException if the mainframe cannot be reached
     */
    public AddNote addNote(String customer, String lines, String type)
            throws IOException, LavsException {
        buildNoteValidation(lines, type)
                .requireNotBlank("customer", customer, "Customer cannot be empty")
                .throwIfInvalid();

        // strip rather than trim each line so the note keeps its internal line breaks
        AddNote addMessage = new AddNote(customer.trim(), lines.strip(), type.trim());
        send(addMessage);
        return addMessage;
    }

    /**
     * Updates an existing note of a customer after validating the note fields.
     *
     * @param customer the customer's unique number
     * @param id the note identifier
     * @param lines the note text, with lines separated by new lines
     * @param type the free-text note type
     * @return the mainframe message containing the updated note details
     * @throws ValidationException if the note id or a required field is invalid
     * @throws LavsException if the mainframe rejects the request
     * @throws IOException if the mainframe cannot be reached
     */
    public UpdateNote updateNote(String customer, Long id, String lines, String type)
            throws IOException, LavsException {
        buildNoteValidation(lines, type)
                .requireNotBlank("customer", customer, "Customer cannot be empty")
                .requirePositive("noteId", id, "Note id")
                .throwIfInvalid();

        UpdateNote updateMessage = new UpdateNote(customer.trim(), id, lines.strip(), type.trim());
        send(updateMessage);
        return updateMessage;
    }

    /**
     * Deletes a note belonging to a customer.
     *
     * @param customerNumber the customer's unique number
     * @param noteId the note identifier
     * @return the mainframe message for the deleted note
     * @throws ValidationException if either identifier is missing or invalid
     * @throws LavsException if the mainframe rejects the request
     * @throws IOException if the mainframe cannot be reached
     */
    public DeleteNote deleteNote(String customerNumber, Long noteId)
            throws IOException, LavsException {
        validateNoteIdentifiers(customerNumber, noteId);

        DeleteNote deleteMessage = new DeleteNote(customerNumber.trim(), noteId);
        send(deleteMessage);
        return deleteMessage;
    }

    /**
     * Lists a customer's notes using the mainframe's paginated operation.
     *
     * @param customerNumber the customer's unique number
     * @param page the one-based page number
     * @param size the maximum number of notes to return
     * @return the notes returned for the requested page
     * @throws ValidationException if the customer number, page, or size is missing or invalid
     * @throws IOException if the mainframe cannot be reached
     * @throws LavsException if the mainframe rejects the request
     */
    public List.Item[] listNotes(String customerNumber, Long page, Long size)
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
     * Retrieves one note belonging to a customer, including its full text.
     *
     * @param customerNumber the customer's unique number
     * @param noteId the note identifier
     * @return the retrieved note and its full details
     * @throws ValidationException if either identifier is missing or invalid
     * @throws IOException if the mainframe cannot be reached
     * @throws LavsException if the mainframe rejects the request
     */
    public Retrieve retrieveNote(String customerNumber, Long noteId)
            throws IOException, LavsException {
        validateNoteIdentifiers(customerNumber, noteId);

        Retrieve retrieveMessage = new Retrieve(noteId, customerNumber.trim());
        send(retrieveMessage);
        return retrieveMessage;
    }

    /**
     * Validates the fields of a note without sending it to the mainframe.
     * Static so the UI can check a form before sending it.
     *
     * @throws ValidationException if the lines or type are missing
     */
    public static void validateNoteDetails(String lines, String type) throws ValidationException {
        buildNoteValidation(lines, type).throwIfInvalid();
    }

    private static ValidationResult buildNoteValidation(String lines, String type) {
        return new ValidationResult()
                .requireNotBlank("type", type, "Note type cannot be empty")
                .requireNotBlank("lines", lines, "Note cannot be empty");
    }

    private void validateNoteIdentifiers(String customerNumber, Long noteId)
            throws ValidationException {
        requireCustomerNumber(new ValidationResult(), customerNumber)
                .requirePositive("noteId", noteId, "Note id")
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
