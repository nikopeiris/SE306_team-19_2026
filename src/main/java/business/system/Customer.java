package business.system;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.Map;

import business.Main;
import business.lavsexceptions.LavsException;
import business.system.customer.CustomerAddressService;
import business.system.dto.CustomerSummary;
import business.validation.ValidationException;
import business.validation.ValidationResult;
import dataaccess.systemadapters.MainframeAdapter;
import mainframe.client.Status;
import mainframe.client.messages.customer.AddAddress;
import mainframe.client.messages.customer.AddEmail;
import mainframe.client.messages.customer.AddPhoneNumber;
import mainframe.client.messages.customer.Create;
import mainframe.client.messages.customer.Retrieve;
import mainframe.client.messages.customer.Search;

/**
 * Business service for adding customers through the mainframe adapter.
 */
public class Customer {
    private final MainframeAdapter customerAdapter;
    /** How many customers to ask the mainframe for per request. */
    private static final long LIST_PAGE_SIZE = 100L;
    
    /** Safety stop so a misbehaving server can never keep us paging forever. */
  private static final int MAX_LIST_PAGES = 500;

  /**
   * Fallback search terms, used when an empty search returns nothing
   * (the mainframe seems to need some search text to return customers).
   */
  private static final String FALLBACK_SEARCH_TERMS = "abcdefghijklmnopqrstuvwxyz0123456789";

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



  /**
   * Returns every customer (all pages), sorted alphabetically by first name
   * (then family name), ignoring upper/lower case.
   *
   * It first tries an empty search. If that returns nothing, it searches each
   * letter and digit in turn and merges the results, removing duplicates by
   * customer number.
   *
   * @throws LavsException if the mainframe rejects the request
   * @throws IOException if the mainframe cannot be reached
   */
  public java.util.List<CustomerSummary> listAllCustomers()
          throws IOException, LavsException {
      Map<String, CustomerSummary> byNumber = new LinkedHashMap<>();

      collectCustomers("", byNumber);
      if (byNumber.isEmpty()) {
          for (char term : FALLBACK_SEARCH_TERMS.toCharArray()) {
              collectCustomers(String.valueOf(term), byNumber);
          }
      }

      java.util.List<CustomerSummary> all = new ArrayList<>(byNumber.values());
      all.sort(Comparator
              .comparing((CustomerSummary c) -> c.getFirstName().toLowerCase())
              .thenComparing(c -> c.getFamilyName().toLowerCase()));
      return all;
  }

  /** Pages through every result of one search and adds the customers to the map. */
  private void collectCustomers(String search, Map<String, CustomerSummary> into)
          throws IOException, LavsException {
      long page = 1;
      while (page <= MAX_LIST_PAGES) {
          Search.Item[] items = listCustomers(page, search, LIST_PAGE_SIZE);
          if (items == null || items.length == 0) {
              break;
          }
          for (Search.Item item : items) {
              CustomerSummary summary = toSummary(item);
              into.putIfAbsent(summary.getNumber(), summary);
          }
          if (items.length < LIST_PAGE_SIZE) {
              break;   // last page
          }
          page++;
      }
  }

  /**
   * Returns the customer's primary email address, or an empty string if
   * they have no email marked as primary.
   */
  public String findPrimaryEmail(String customerNumber)
          throws IOException, LavsException {
      mainframe.client.messages.customerEmail.List.Item[] emails =
              listEmails(customerNumber, 1L, LIST_PAGE_SIZE);
      if (emails == null) {
          return "";
      }
      for (mainframe.client.messages.customerEmail.List.Item email : emails) {
          if ("y".equals(email.getIsPrimary())) {
              return email.getAddress() == null ? "" : email.getAddress();
          }
      }
      return "";
  }
 
  /** Copies the fields we need out of a mainframe search result. */
  private CustomerSummary toSummary(Search.Item item) {
      return new CustomerSummary(
              item.getNumber(),
              item.getFirstName(),
              item.getFamilyName());
  }


  /**
     * Adds an email address to an existing customer after validating the email fields.
     *
     * @return the mainframe message containing the created email details
     * @throws ValidationException if a required field or email type is invalid
     * @throws LavsException if the mainframe rejects the request
     * @throws IOException if the mainframe cannot be reached
     */
    public AddEmail addEmail(
            String address,
            String customer,
            boolean isPrimary,
            String otherType,
            String type) throws IOException, LavsException {
        validateEmailDetails(address, customer, otherType, type);

        AddEmail addMessage = new AddEmail(
                address.trim(),
                customer.trim(),
                isPrimary,
                otherType == null ? null : otherType.trim(),
                type.trim());
        Status status = customerAdapter.send(addMessage);
        Main.isValidStatus(status);
        return addMessage;
    }

    /**
     * Adds a phone number to an existing customer after validating the phone fields.
     *
     * @return the mainframe message containing the created phone number details
     * @throws ValidationException if a required field or phone type is invalid
     * @throws LavsException if the mainframe rejects the request
     * @throws IOException if the mainframe cannot be reached
     */
    public AddPhoneNumber addPhoneNumber(
            boolean canTxt,
            String countryCode,
            String customer,
            boolean isPrimary,
            String number,
            String otherType,
            String type) throws IOException, LavsException {
        validatePhoneNumberDetails(countryCode, customer, number, otherType, type);

        AddPhoneNumber addMessage = new AddPhoneNumber(
                canTxt,
                countryCode.trim(),
                customer.trim(),
                isPrimary,
                number.trim(),
                otherType == null ? null : otherType.trim(),
                type.trim());
        Status status = customerAdapter.send(addMessage);
        Main.isValidStatus(status);
        return addMessage;
    }

    public void validateEmailDetails(
            String address,
            String customer,
            String otherType,
            String type) throws ValidationException {
        ValidationResult result = new ValidationResult()
                .requireNotBlank("address", address, "Email address cannot be empty")
                .requireNotBlank("customer", customer, "Customer cannot be empty")
                .requireNotBlank("type", type, "Email type cannot be empty");

        if (type != null && !type.isBlank()
                && !type.equals(AddEmail.TYPE_PERSONAL)
                && !type.equals(AddEmail.TYPE_WORK)
                && !type.equals(AddEmail.TYPE_OTHER)) {
            result.addError("type", "Email type is invalid");
        }
        if (AddEmail.TYPE_OTHER.equals(type)
                && (otherType == null || otherType.isBlank())) {
            result.addError("otherType", "Other email type cannot be empty");
        }

        if (!result.isValid()) {
            throw new ValidationException(result);
        }
    }

    public void validatePhoneNumberDetails(
            String countryCode,
            String customer,
            String number,
            String otherType,
            String type) throws ValidationException {
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
        new CustomerAddressService(customerAdapter).validateAddressDetails(
                city, country, customer, line1, otherType, postCode, suburb, type);
    }
}