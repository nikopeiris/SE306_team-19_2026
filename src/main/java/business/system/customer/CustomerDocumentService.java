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

    deleteDocument(customer, documentId);
    AddDocument updatedDocument = addDocument(customer, fileName, fileType, size, type);
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
  
  public Retrieve retrieveCustomerDocument(String CustomerNumber, Long documentId) throws IOException, LavsException {
    validateDocumentIdentifiers(CustomerNumber, documentId);
    Retrieve retrieveMessage = new Retrieve(documentId, CustomerNumber.trim());
    send(retrieveMessage);
    return retrieveMessage;
  }


  public static void validateDocumentIdDetails(String type, String filename, String file, String size) throws ValidationException {
    buildDocumentValidation(type, filename, size).throwIfInvalid();
  }
  
  public static ValidationResult buildDocumentValidation(String type, String filename, String size) {
    ValidationResult result = new ValidationResult()
        .requireNotBlank("type", type, "Type cannot be empty")
        .requireNotBlank("filename", filename, "File Name cannot be empty")
        .requireNotBlank("size", size, "Size cannot be empty");

    return result;
  }


  private void validateDocumentIdentifiers(String customerNumber, Long documentId)
            throws ValidationException {
    requireCustomerNumber(new ValidationResult(), customerNumber)
            .requirePositive("phoneNumberId", documentId, "Phone number id")
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
