package business.system;

import java.io.IOException;

import business.Main;
import business.lavsexceptions.LavsException;
import business.validation.ValidationException;
import business.validation.ValidationResult;
import dataaccess.systemadapters.MainframeAdapter;
import mainframe.client.Status;
import mainframe.client.messages.customer.Create;
import mainframe.client.messages.customer.Retrieve;
import mainframe.client.messages.customer.Search;

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

  /**
   * Retrieves a customer by their unique customer number.
   */
  public Retrieve retrieveCustomer(String number) throws IOException, LavsException {
      if (number == null || number.isBlank()) {
          throw new IllegalArgumentException("Customer number cannot be null or empty");
      }
      Retrieve retrieveMessage = new Retrieve(number);
      Status status = customerAdapter.send(retrieveMessage);
      Main.isValidStatus(status);
      return retrieveMessage;
  }

  /**
   * Lists a customer's phone numbers using the mainframe's paginated operation.
   */
  public mainframe.client.messages.customerPhoneNumber.List.Item[] listPhoneNumbers(
          String customerNumber, Long page, Long size)
          throws IOException, LavsException {
      validateCustomerNumber(customerNumber);
      validatePageDetails(page, size);

      mainframe.client.messages.customerPhoneNumber.List listMessage =
              new mainframe.client.messages.customerPhoneNumber.List(
                      customerNumber.trim(), page, size);
      Status status = customerAdapter.send(listMessage);
      Main.isValidStatus(status);
      return listMessage.getItems();
  }

  /**
   * Retrieves one phone number belonging to a customer.
   */
  public mainframe.client.messages.customerPhoneNumber.Retrieve retrievePhoneNumber(
          String customerNumber, Long phoneNumberId)
          throws IOException, LavsException {
      ValidationResult result = validateContactIdentifier(
              customerNumber, phoneNumberId, "Phone number id");
      if (!result.isValid()) {
          throw new ValidationException(result);
      }

      mainframe.client.messages.customerPhoneNumber.Retrieve retrieveMessage =
              new mainframe.client.messages.customerPhoneNumber.Retrieve(
                      phoneNumberId, customerNumber.trim());
      Status status = customerAdapter.send(retrieveMessage);
      Main.isValidStatus(status);
      return retrieveMessage;
  }

  /**
   * Lists a customer's email addresses using the mainframe's paginated operation.
   */
  public mainframe.client.messages.customerEmail.List.Item[] listEmails(
          String customerNumber, Long page, Long size)
          throws IOException, LavsException {
      validateCustomerNumber(customerNumber);
      validatePageDetails(page, size);

      mainframe.client.messages.customerEmail.List listMessage =
              new mainframe.client.messages.customerEmail.List(
                      customerNumber.trim(), page, size);
      Status status = customerAdapter.send(listMessage);
      Main.isValidStatus(status);
      return listMessage.getItems();
  }

  /**
   * Retrieves one email address belonging to a customer.
   */
  public mainframe.client.messages.customerEmail.Retrieve retrieveEmail(
          String customerNumber, Long emailId)
          throws IOException, LavsException {
      ValidationResult result = validateContactIdentifier(
              customerNumber, emailId, "Email id");
      if (!result.isValid()) {
          throw new ValidationException(result);
      }

      mainframe.client.messages.customerEmail.Retrieve retrieveMessage =
              new mainframe.client.messages.customerEmail.Retrieve(
                      emailId, customerNumber.trim());
      Status status = customerAdapter.send(retrieveMessage);
      Main.isValidStatus(status);
      return retrieveMessage;
  }

  private ValidationResult validateContactIdentifier(
          String customerNumber, Long identifier, String identifierLabel) {
      ValidationResult result = new ValidationResult();
      addCustomerNumberValidation(result, customerNumber);
      String fieldName = identifierLabel.startsWith("Phone") ? "phoneNumberId" : "emailId";
      if (identifier == null) {
          result.addError(fieldName, identifierLabel + " cannot be empty");
      } else if (identifier < 1) {
          result.addError(fieldName, identifierLabel + " must be a positive integer");
      }
      return result;
  }

  private void validateCustomerNumber(String customerNumber)
          throws ValidationException {
      ValidationResult result = new ValidationResult();
      addCustomerNumberValidation(result, customerNumber);
      if (!result.isValid()) {
          throw new ValidationException(result);
      }
  }

  private void addCustomerNumberValidation(
          ValidationResult result, String customerNumber) {
      if (customerNumber == null || customerNumber.isBlank()) {
          result.addError("customerNumber", "Customer number cannot be null or empty");
      }
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

  private void validatePageDetails(Long page, Long size)
          throws ValidationException {
      ValidationResult result = new ValidationResult();
      if (page == null) {
          result.addError("page", "Page cannot be empty");
      } else if (page < 1) {
          result.addError("page", "Page must be a positive integer");
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
