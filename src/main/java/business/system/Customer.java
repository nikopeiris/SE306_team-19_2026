package business.system;

import business.Main;
import business.lavsexceptions.LavsException;
import business.validation.ValidationException;
import business.validation.ValidationResult;
import dataaccess.systemadapters.MainframeAdapter;
import mainframe.client.Status;
import mainframe.client.messages.customer.AddAddress;
import mainframe.client.messages.customer.Create;
import mainframe.client.messages.customer.DeleteAddress;
import mainframe.client.messages.customer.Retrieve;
import mainframe.client.messages.customer.Search;
import mainframe.client.messages.customer.UpdateAddress;
import mainframe.client.messages.customerAddress.List;

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
   * Updates an existing address of a customer after validating the address fields.
   *
   * @return the mainframe message containing the updated address
   * @throws ValidationException if the address id, a required field or the address type is invalid
   * @throws LavsException if the mainframe rejects the request
   * @throws IOException if the mainframe cannot be reached
   */
  public UpdateAddress updateAddress(
          String city,
          String country,
          String customer,
          Long id,
          boolean isMailing,
          boolean isPrimary,
          String line1,
          String line2,
          String otherType,
          String postCode,
          String suburb,
          String type) throws IOException, LavsException {
      ValidationResult result = buildAddressValidation(
              city, country, customer, line1, otherType, postCode, suburb, type);
      addAddressIdValidation(result, id);
      if (!result.isValid()) {
          throw new ValidationException(result);
      }

      UpdateAddress updateMessage = new UpdateAddress(
              city.trim(),
              country.trim(),
              customer.trim(),
              id,
              isMailing,
              isPrimary,
              line1.trim(),
              line2 == null ? null : line2.trim(),
              otherType == null ? null : otherType.trim(),
              postCode.trim(),
              suburb.trim(),
              type.trim());
      Status status = customerAdapter.send(updateMessage);
      Main.isValidStatus(status);
      return updateMessage;
  }

  /**
   * Deletes an address belonging to a customer.
   *
   * @param customerNumber the customer's unique number
   * @param addressId the address identifier
   * @return the mainframe message for the deleted address
   * @throws ValidationException if either identifier is missing or invalid
   * @throws LavsException if the mainframe rejects the request
   * @throws IOException if the mainframe cannot be reached
   */
  public DeleteAddress deleteAddress(String customerNumber, Long addressId)
          throws IOException, LavsException {
      ValidationResult result = new ValidationResult();
      addCustomerNumberValidation(result, customerNumber);
      addAddressIdValidation(result, addressId);
      if (!result.isValid()) {
          throw new ValidationException(result);
      }

      DeleteAddress deleteMessage = new DeleteAddress(customerNumber.trim(), addressId);
      Status status = customerAdapter.send(deleteMessage);
      Main.isValidStatus(status);
      return deleteMessage;
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
   * Lists the addresses belonging to a customer using the mainframe's
   * paginated customer-address list operation.
   *
   * @param customerNumber the customer's unique number
   * @param page the one-based page number
   * @param size the maximum number of addresses to return
   * @return the addresses returned for the requested page
   * @throws ValidationException if page or size is missing or invalid
   * @throws ValidationException if the customer number, page, or size is missing or invalid
   * @throws IOException if the mainframe cannot be reached
   * @throws LavsException if the mainframe rejects the request
   */
  public List.Item[] listAddresses(String customerNumber, Long page, Long size)
          throws IOException, LavsException {
      validateCustomerNumber(customerNumber);
      validatePageDetails(page, size);

      List listMessage = new List(customerNumber.trim(), page, size);
      Status status = customerAdapter.send(listMessage);
      Main.isValidStatus(status);
      return listMessage.getItems();
  }

  /**
   * Retrieves one address belonging to a customer.
   *
   * @param customerNumber the customer's unique number
   * @param addressId the address identifier
   * @return the retrieved address and its full details
   * @throws ValidationException if either identifier is missing or invalid
   * @throws IOException if the mainframe cannot be reached
   * @throws LavsException if the mainframe rejects the request
   */
  public mainframe.client.messages.customerAddress.Retrieve retrieveAddress(
          String customerNumber, Long addressId) throws IOException, LavsException {
      ValidationResult result = new ValidationResult();
      addCustomerNumberValidation(result, customerNumber);
      addAddressIdValidation(result, addressId);
      if (!result.isValid()) {
          throw new ValidationException(result);
      }

      mainframe.client.messages.customerAddress.Retrieve retrieveMessage =
              new mainframe.client.messages.customerAddress.Retrieve(
                      addressId, customerNumber.trim());
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

  private void addAddressIdValidation(ValidationResult result, Long addressId) {
      if (addressId == null) {
          result.addError("addressId", "Address id cannot be empty");
      } else if (addressId < 1) {
          result.addError("addressId", "Address id must be a positive integer");
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

  public void validateAddressDetails(
          String city,
          String country,
          String customer,
          String line1,
          String otherType,
          String postCode,
          String suburb,
          String type) throws ValidationException {
      ValidationResult result = buildAddressValidation(
              city, country, customer, line1, otherType, postCode, suburb, type);
      if (!result.isValid()) {
          throw new ValidationException(result);
      }
  }

  private ValidationResult buildAddressValidation(
          String city,
          String country,
          String customer,
          String line1,
          String otherType,
          String postCode,
          String suburb,
          String type) {
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
      return result;
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
