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

  public CustomerDocumentService(MainframeAdapter adapter) {
    if (adapter == null) {
      throw new IllegalArgumentException("Document adpater cannot be null");
    }
    this.adapter = adapter;
  }


  public AddDocument addDocument(String customer, String fileName, String fileType, long size, String type)
      throws IOException, LavsException {
    AddDocument addDocument = new AddDocument(customer.trim(), fileName.trim(), fileType.trim(), size,
        type.trim());
    send(addDocument);
    return addDocument;
  }

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

  public DeleteDocument deleteDocument(String customer, long id) throws IOException, LavsException {
    DeleteDocument deleteDocument = new DeleteDocument(customer.trim(), id);
    send(deleteDocument);
    return deleteDocument;
  }
  

  public List.Item[] listDocuments(String customerNumber, Long page, Long size) throws IOException, LavsException {
    requireCustomerNumber(new ValidationResult(), customerNumber).requirePositive("page", page, "Page")
        .requirePositive("size", size, "Size").throwIfInvalid();
    List listMessage = new List(customerNumber.trim(), page, size);
    send(listMessage);
    return listMessage.getItems();

  }
  
  public Retrieve retrieveCustomerDocument(String customerNumber, Long documentId) throws IOException, LavsException {
    validateDocumentIdentifiers(customerNumber, documentId);
    Retrieve retrieveMessage = new Retrieve(documentId, customerNumber.trim());
    send(retrieveMessage);
    return retrieveMessage;
  }


  public static void validateDocumentIdDetails(String type, String filename, String fileType, long size) throws ValidationException {
    buildDocumentValidation(type, filename, fileType, size).throwIfInvalid();
  }

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

    return result;
  }


  private void validateDocumentIdentifiers(String customerNumber, Long documentId)
            throws ValidationException {
    requireCustomerNumber(new ValidationResult(), customerNumber)
            .requirePositive("document id", documentId, "Document id")
            .throwIfInvalid();
  }



  private void send(Message message) throws IOException, LavsException {
    Status status = adapter.send(message);
    Main.isValidStatus(status);
  }

  private static ValidationResult requireCustomerNumber(
            ValidationResult result, String customerNumber) {
    return result.requireNotBlank("customerNumber", customerNumber,
            "Customer number cannot be null or empty");
  }
    

}
