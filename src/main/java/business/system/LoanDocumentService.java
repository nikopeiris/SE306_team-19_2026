package business.system;

import java.io.IOException;

import business.Main;
import business.lavsexceptions.LavsException;
import business.system.customer.CustomerDocumentService;
import business.validation.ValidationException;
import business.validation.ValidationResult;
import dataaccess.systemadapters.MainframeAdapter;
import mainframe.client.Message;
import mainframe.client.Status;
import mainframe.client.messages.loan.AddDocument;
import mainframe.client.messages.loan.DeleteDocument;
import mainframe.client.messages.loanDocument.List;
import mainframe.client.messages.loanDocument.Retrieve;

/**
 * Business service for managing a loan's documents through the mainframe adapter.
 * Only the document's metadata is stored: its type, file name, file type and size.
 */
public class LoanDocumentService {
    private final MainframeAdapter adapter;

    public LoanDocumentService(MainframeAdapter adapter) {
        if (adapter == null) {
            throw new IllegalArgumentException("Loan document adapter cannot be null");
        }
        this.adapter = adapter;
    }

    /**
     * Adds a document to an existing loan after validating the document fields.
     *
     * @param loanNumber the loan's unique number
     * @param fileName the name of the file
     * @param fileType the type/extension of the file (optional)
     * @param size the size of the file in bytes
     * @param type the document type classification
     * @return the mainframe message containing the created document details
     * @throws ValidationException if a required field or field length is invalid
     * @throws LavsException if the mainframe rejects the request
     * @throws IOException if the mainframe cannot be reached
     */
    public AddDocument addDocument(String loanNumber, String fileName, String fileType, Long size,
            String type) throws IOException, LavsException {
        // same rules as a customer document, so they are kept in one place
        requireLoanNumber(CustomerDocumentService.buildDocumentValidation(type, fileName, fileType, size),
                loanNumber)
                .throwIfInvalid();

        AddDocument addMessage = new AddDocument(
                fileName.trim(), trimOrNull(fileType), loanNumber.trim(), size, type.trim());
        send(addMessage);
        return addMessage;
    }

    /**
     * Changes the type and file name of an existing loan document. The mainframe can't edit
     * a document in place, so the new details are added first and the old document is removed
     * after, which means the document gets a new id. The file type and size carry over unchanged.
     *
     * @param loanNumber the loan's unique number
     * @param documentId the ID of the document to update
     * @param type the new document type classification
     * @param fileName the new name of the file
     * @return the mainframe message containing the newly created document details
     * @throws ValidationException if the identifiers or the new type or file name are invalid
     * @throws LavsException if the mainframe rejects the request
     * @throws IOException if the mainframe cannot be reached
     */
    public AddDocument updateDocument(String loanNumber, Long documentId, String type,
            String fileName) throws IOException, LavsException {
        Retrieve existing = retrieveDocument(loanNumber, documentId);

        // adding first means a failed validation leaves the old document untouched
        AddDocument updated = addDocument(
                loanNumber, fileName, existing.getFileType(), existing.getSize(), type);
        deleteDocument(loanNumber, documentId);
        return updated;
    }

    /**
     * Deletes a document belonging to a loan.
     *
     * @param loanNumber the loan's unique number
     * @param documentId the ID of the document to delete
     * @return the mainframe message for the deleted document
     * @throws ValidationException if either identifier is missing or invalid
     * @throws LavsException if the mainframe rejects the request
     * @throws IOException if the mainframe cannot be reached
     */
    public DeleteDocument deleteDocument(String loanNumber, Long documentId)
            throws IOException, LavsException {
        validateDocumentIdentifiers(loanNumber, documentId);

        DeleteDocument deleteMessage = new DeleteDocument(documentId, loanNumber.trim());
        send(deleteMessage);
        return deleteMessage;
    }

    /**
     * Lists a loan's documents using the mainframe's paginated operation.
     *
     * @param loanNumber the loan's unique number
     * @param page the one-based page number
     * @param size the maximum number of documents to return
     * @return the documents returned for the requested page
     * @throws ValidationException if the loan number, page, or size is missing or invalid
     * @throws LavsException if the mainframe rejects the request
     * @throws IOException if the mainframe cannot be reached
     */
    public List.Item[] listDocuments(String loanNumber, Long page, Long size)
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
     * Retrieves one document belonging to a loan, including its file type and size.
     *
     * @param loanNumber the loan's unique number
     * @param documentId the ID of the document to retrieve
     * @return the retrieved document and its full details
     * @throws ValidationException if either identifier is missing or invalid
     * @throws LavsException if the mainframe rejects the request
     * @throws IOException if the mainframe cannot be reached
     */
    public Retrieve retrieveDocument(String loanNumber, Long documentId)
            throws IOException, LavsException {
        validateDocumentIdentifiers(loanNumber, documentId);

        Retrieve retrieveMessage = new Retrieve(documentId, loanNumber.trim());
        send(retrieveMessage);
        return retrieveMessage;
    }

    private void validateDocumentIdentifiers(String loanNumber, Long documentId)
            throws ValidationException {
        requireLoanNumber(new ValidationResult(), loanNumber)
                .requirePositive("documentId", documentId, "Document id")
                .throwIfInvalid();
    }

    private static ValidationResult requireLoanNumber(ValidationResult result, String loanNumber) {
        return result.requireNotBlank("loanNumber", loanNumber,
                "Loan number cannot be null or empty");
    }

    private static String trimOrNull(String value) {
        return value == null ? null : value.trim();
    }

    private void send(Message message) throws IOException, LavsException {
        Status status = adapter.send(message);
        Main.isValidStatus(status);
    }
}
