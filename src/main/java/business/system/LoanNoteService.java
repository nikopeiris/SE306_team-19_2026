package business.system;

import java.io.IOException;

import business.Main;
import business.lavsexceptions.LavsException;
import business.validation.ValidationException;
import business.validation.ValidationResult;
import dataaccess.systemadapters.MainframeAdapter;
import mainframe.client.Message;
import mainframe.client.Status;
import mainframe.client.messages.loan.AddNote;
import mainframe.client.messages.loan.DeleteNote;
import mainframe.client.messages.loan.UpdateNote;
import mainframe.client.messages.loanNote.List;
import mainframe.client.messages.loanNote.Retrieve;

/**
 * Business service for managing a loan's notes through the mainframe adapter.
 * A note only requires a type and its lines; the author and date added are set by the mainframe.
 */
public class LoanNoteService {
    private final MainframeAdapter adapter;

    public LoanNoteService(MainframeAdapter adapter) {
        if (adapter == null) {
            throw new IllegalArgumentException("Loan note adapter cannot be null");
        }
        this.adapter = adapter;
    }

    /**
     * Adds a new note to a loan.
     *
     * @param loanNumber the loan number
     * @param lines the note lines
     * @param type the note type
     * @return the added note
     * @throws IOException if an I/O error occurs
     * @throws LavsException if a business error occurs
     */
    public AddNote addNote(String loanNumber, String lines, String type)
            throws IOException, LavsException {
        requireLoanNumber(buildNoteValidation(lines, type), loanNumber).throwIfInvalid();

        // strip rather than trim each line so the note keeps its internal line breaks
        AddNote addMessage = new AddNote(lines.strip(), loanNumber.trim(), type.trim());
        send(addMessage);
        return addMessage;
    }

    /**
     * Updates an existing note of a loan.
     * @param loanNumber
     * @param id
     * @param lines
     * @param type
     * @return
     * @throws IOException
     * @throws LavsException
     */
    public UpdateNote updateNote(String loanNumber, Long id, String lines, String type)
            throws IOException, LavsException {
        requireLoanNumber(buildNoteValidation(lines, type), loanNumber)
                .requirePositive("noteId", id, "Note id")
                .throwIfInvalid();

        UpdateNote updateMessage = new UpdateNote(
                id, lines.strip(), loanNumber.trim(), type.trim());
        send(updateMessage);
        return updateMessage;
    }

    /**
     * Deletes an existing note from a loan.
     *
     * @param loanNumber the loan number
     * @param noteId the note ID
     * @return the deleted note
     * @throws IOException if an I/O error occurs
     * @throws LavsException if a business error occurs
     */
    public DeleteNote deleteNote(String loanNumber, Long noteId)
            throws IOException, LavsException {
        validateNoteIdentifiers(loanNumber, noteId);

        DeleteNote deleteMessage = new DeleteNote(noteId, loanNumber.trim());
        send(deleteMessage);
        return deleteMessage;
    }

    /**
     * Lists the notes for a loan.
     *
     * @param loanNumber the loan number
     * @param page the page number
     * @param size the number of notes per page
     * @return the list of notes
     * @throws IOException if an I/O error occurs
     * @throws LavsException if a business error occurs
     */
    public List.Item[] listNotes(String loanNumber, Long page, Long size)
            throws IOException, LavsException {
        requireLoanNumber(new ValidationResult(), loanNumber)
                .requirePositive("page", page, "Page")
                .requirePositive("size", size, "Size")
                .throwIfInvalid();

        List listMessage = new List(loanNumber.trim(), page, size);
        send(listMessage);
        return listMessage.getItems();
    }

    /**
     * Retrieves a specific note from a loan.
     *
     * @param loanNumber the loan number
     * @param noteId the note ID
     * @return the retrieved note
     * @throws IOException if an I/O error occurs
     * @throws LavsException if a business error occurs
     */
    public Retrieve retrieveNote(String loanNumber, Long noteId)
            throws IOException, LavsException {
        validateNoteIdentifiers(loanNumber, noteId);

        Retrieve retrieveMessage = new Retrieve(noteId, loanNumber.trim());
        send(retrieveMessage);
        return retrieveMessage;
    }

    /**
     * Validates the details of a note.
     *
     * @param lines the note lines
     * @param type the note type
     * @throws ValidationException if the note details are invalid
     */
    public static void validateNoteDetails(String lines, String type) throws ValidationException {
        buildNoteValidation(lines, type).throwIfInvalid();
    }

    /**
     * Validates the identifiers of a note.
     *
     * @param loanNumber the loan number
     * @param noteId the note ID
     * @throws ValidationException if the identifiers are invalid
     */
    private static ValidationResult buildNoteValidation(String lines, String type) {
        return new ValidationResult()
                .requireNotBlank("type", type, "Note type cannot be empty")
                .requireNotBlank("lines", lines, "Note cannot be empty");
    }

    private void validateNoteIdentifiers(String loanNumber, Long noteId)
            throws ValidationException {
        requireLoanNumber(new ValidationResult(), loanNumber)
                .requirePositive("noteId", noteId, "Note id")
                .throwIfInvalid();
    }

    private static ValidationResult requireLoanNumber(ValidationResult result, String loanNumber) {
        return result.requireNotBlank("loanNumber", loanNumber,
                "Loan number cannot be null or empty");
    }

    private void send(Message message) throws IOException, LavsException {
        Status status = adapter.send(message);
        Main.isValidStatus(status);
    }
}