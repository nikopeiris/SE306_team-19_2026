package business.system.customer;

import java.io.IOException;

import business.Main;
import business.lavsexceptions.LavsException;
import business.validation.ValidationException;
import business.validation.ValidationResult;
import dataaccess.systemadapters.MainframeAdapter;
import mainframe.client.Message;
import mainframe.client.Status;
import mainframe.client.messages.customer.AddDocument;
import mainframe.client.messages.customer.DeleteDocument;
import mainframe.client.messages.customerDocument.List;
import mainframe.client.messages.customerDocument.Retrieve;

public class CustomerDocumentService {
  private final MainframeAdapter adapter;

  /**
   * Constructs a new CustomerDocumentService with the specified mainframe adapter.
   *
   * @param adapter the mainframe adapter used to communicate with the backend
   */
  public CustomerDocumentService(MainframeAdapter adapter) {
    if (adapter == null) {
      throw new IllegalArgumentException("Document adpater cannot be null");
    }
    this.adapter = adapter;
  }

  /**
   * Adds a document to an existing customer after validating the document fields.
   *
   * @param customer the customer number or identifier
   * @param fileName the name of the file
   * @param fileType the type/extension of the file
   * @param size the size of the file in bytes
   * @param type the document type classification
   * @return the mainframe message containing the created document details
   * @throws ValidationException if a required field or field length is invalid
   * @throws LavsException if the mainframe rejects the request
   * @throws IOException if the mainframe cannot be reached
   */
  public AddDocument addDocument(String customer, String fileName, String fileType, long size, String type)
      throws IOException, LavsException {
    validateDocumentIdDetails(type, fileName, fileType, size);
    AddDocument addDocument = new AddDocument(customer.trim(), fileName.trim(), fileType.trim(), size,
        type.trim());
    send(addDocument);
    return addDocument;
  }

  /**
   * Updates an existing customer document by creating a new version and removing the old one.
   *
   * @param customer the customer number or identifier
   * @param documentId the ID of the document to update
   * @return the mainframe message containing the newly created document details
   * @throws ValidationException if the document validation fails during re-addition
   * @throws LavsException if the mainframe rejects the request
   * @throws IOException if the mainframe cannot be reached
   */
  public AddDocument updateDocument(String customer, long documentId) throws IOException, LavsException {
    Retrieve customerDocument = retrieveCustomerDocument(customer, documentId);
    String fileName = customerDocument.getFileName();
    String fileType = customerDocument.getFileType();
    Long size = customerDocument.getSize();
    String type = customerDocument.getType();

    AddDocument updatedDocument = addDocument(customer, fileName, fileType, size, type);
    deleteDocument(customer, documentId);
    return updatedDocument;
  }

  /**
   * Deletes a specific document associated with a customer.
   *
   * @param customer the customer number or identifier
   * @param id the ID of the document to delete
   * @return the mainframe message containing the deleted document details
   * @throws LavsException if the mainframe rejects the request
   * @throws IOException if the mainframe cannot be reached
   */
  public DeleteDocument deleteDocument(String customer, long id) throws IOException, LavsException {
    DeleteDocument deleteDocument = new DeleteDocument(customer.trim(), id);
    send(deleteDocument);
    return deleteDocument;
  }
  
  /**
   * Retrieves a paginated list of documents for a specified customer.
   *
   * @param customerNumber the customer number or identifier
   * @param page the page number to retrieve
   * @param size the number of items per page
   * @return an array of document items for the requested page
   * @throws ValidationException if the customer number or pagination parameters are invalid
   * @throws LavsException if the mainframe rejects the request
   * @throws IOException if the mainframe cannot be reached
   */
  public List.Item[] listDocuments(String customerNumber, Long page, Long size) throws IOException, LavsException {
    requireCustomerNumber(new ValidationResult(), customerNumber).requirePositive("page", page, "Page")
        .requirePositive("size", size, "Size").throwIfInvalid();
    List listMessage = new List(customerNumber.trim(), page, size);
    send(listMessage);
    return listMessage.getItems();
  }
  
  /**
   * Retrieves a single customer document by its identifiers.
   *
   * @param customerNumber the customer number or identifier
   * @param documentId the ID of the document to retrieve
   * @return the mainframe message containing the document details
   * @throws ValidationException if the identifiers are invalid
   * @throws LavsException if the mainframe rejects the request
   * @throws IOException if the mainframe cannot be reached
   */
  public Retrieve retrieveCustomerDocument(String customerNumber, Long documentId) throws IOException, LavsException {
    validateDocumentIdentifiers(customerNumber, documentId);
    Retrieve retrieveMessage = new Retrieve(documentId, customerNumber.trim());
    send(retrieveMessage);
    return retrieveMessage;
  }

  /**
   * Validates document details including required fields and maximum character lengths.
   *
   * @param type the document type classification
   * @param filename the name of the file
   * @param fileType the type/extension of the file
   * @param size the size of the file in bytes
   * @throws ValidationException if any document field fails validation rules
   */
  public static void validateDocumentIdDetails(String type, String filename, String fileType, long size) throws ValidationException {
    buildDocumentValidation(type, filename, fileType, size).throwIfInvalid();
  }
  
  /**
   * Builds and returns a ValidationResult containing validation checks for document fields.
   *
   * @param type the document type classification
   * @param filename the name of the file
   * @param fileType the type/extension of the file
   * @param size the size of the file in bytes
   * @return a ValidationResult detailing any validation errors found
   */
  public static ValidationResult buildDocumentValidation(String type, String filename, String fileType, long size) {
    ValidationResult result = new ValidationResult()
        .requireNotBlank("type", type, "Type cannot be empty")
        .requireNotBlank("filename", filename, "File Name cannot be empty");

    // Enforce max length constraints
    if (type != null && type.length() > 20) {
      result.addError("type", "Type must not exceed 20 characters");
    }
    if (filename != null && filename.length() > 50) {
      result.addError("filename", "Filename must not exceed 50 characters");
    }
    if (fileType != null && fileType.length() > 50) {
      result.addError("fileType", "File type must not exceed 50 characters");
    }
    if (String.valueOf(size).length() > 15) {
      result.addError("size", "Size must not exceed 15 characters");
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

    // validates whether phone number is correct
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
            .requirePositive("document id", documentId, "Document id")
            .throwIfInvalid();
  }

  /**
   * Sends a mainframe message and verifies its response status.
   *
   * @param message the mainframe message to send
   * @throws IOException if the mainframe cannot be reached
   * @throws LavsException if the response status is invalid
   */
  private void send(Message message) throws IOException, LavsException {
    Status status = adapter.send(message);
    Main.isValidStatus(status);
  }

  /**
   * Ensures that the provided customer number is present and not blank.
   *
   * @param result the validation result object to append errors to
   * @param customerNumber the customer number to check
   * @return the updated ValidationResult
   */
  private static ValidationResult requireCustomerNumber(
            ValidationResult result, String customerNumber) {
    return result.requireNotBlank("customerNumber", customerNumber,
            "Customer number cannot be null or empty");
  }
}