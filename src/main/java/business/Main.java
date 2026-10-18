package business;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

import business.lavsexceptions.LavsException;
import business.system.Coborrower;
import business.system.Customer;
import business.system.Loan;
import business.system.LoginService;
import business.system.Payment;
import business.system.SignupService;
import business.system.customer.CustomerAddressService;
import business.system.customer.CustomerEmailService;
import business.system.customer.CustomerEmployerService;
import business.system.customer.CustomerPhoneNumberService;
import business.validation.ValidationException;
import dataaccess.systemadapters.MainframeAdapter;
import mainframe.client.Connection;
import mainframe.client.HttpConnection;
import mainframe.client.Status;
import mainframe.client.messages.customer.Retrieve;
import mainframe.client.messages.customer.Search;
import mainframe.client.messages.customerAddress.List;

public class Main {
  private String userName;
  private Connection connection = new HttpConnection("https://animating-eraser-arrogance.ngrok-free.dev/");
  private MainframeAdapter adapter = new MainframeAdapter(connection);
  private String currentLoanId;

  /**
   * Validates the status of a mainframe response.
   * @param status the status to validate
   * @throws LavsException if the status is not successful
   */
  public static boolean isValidStatus(Status status) throws LavsException {
        if (!status.getWasSuccessful()) {
          throw new LavsException(status);
        }
        return true;
  } 

  /**
   * Attempts to log in a user with the provided username and password.
   * @param userName the username of the user to log in
   * @param password the password of the user to log in
   * @return true if the login was successful
   * @throws IOException if network or mainframe connection fails
   * @throws LavsException if the mainframe rejects the request
   */
  public boolean login(String userName, String password) throws IOException, LavsException {
    LoginService loginService = new LoginService(adapter);
    if (loginService.attemptLogin(userName, password)){
      this.userName = userName;
      return true;
    }
    return false;
  }

  /**
   * Sets the current loan id.
   * @param loanId the loan id to set
   */
  public void setLoanId(String loanId){
    this.currentLoanId = loanId;
  }
  /**
   * Signs up a new user via admin credentials, then logs in as that user.
   *
   * @param login        the login/username for the new user
   * @param name         the display name for the new user
   * @param password     the password for the new user
   * @param organisation the organisation id
   * @param permissions  the permissions level
   * @throws IOException if network or mainframe connection fails
   */
  public boolean signup(String login, String name, String password, long organisation, long permissions)
      throws IOException, LavsException {
    // Create the user on the mainframe (logs in as admin internally)
    SignupService signupService = new SignupService(adapter);
    signupService.signup(login, name, password, organisation, permissions);

    // Log out the admin session so we can log in as the new user
    adapter.logOut();

    // Log in as the newly created user using the existing attemptLogin flow
    LoginService loginService = new LoginService(adapter);
    if (loginService.attemptLogin(login, password)) {
      this.userName = login;
      return true;
    }
    return false;
  }

  /**
   * Logs out the currently logged-in user.
   * @return true if logout was successful
   */
  public boolean logOut(){
    adapter.logOut();
    userName = null;
    return true;
  }

  /**
   * Returns the username of the currently logged-in user.
   * @return  the username of the logged-in user, or null if no user is logged in
   */
  public String getUserName(){
    return userName;
  }

  /**
   * Adds a customer via the mainframe adapter after validating all customer fields.
   *
   * @param dateOfBirth the date of birth of the customer
   * @param familyName the family name of the customer
   * @param firstName the first name of the customer
   * @param nationality the nationality of the customer
   * @param organisation the organisation id of the customer
   * @param title the title of the customer
   * @return true if the customer was added successfully
   * @throws IOException if network or mainframe connection fails
   * @throws LavsException if the mainframe rejects the request
   */
  public boolean addCustomer(String dateOfBirth, String familyName, String firstName, String nationality, Long organisation, String title) throws IOException, LavsException {
    Customer customerService = new Customer(adapter);
    customerService.addCustomer(dateOfBirth, familyName, firstName, nationality, organisation, title);
    return true;
  }

  /**
   * Adds an address to an existing customer via the mainframe adapter after validating the address fields.
   * @param city city of the address
   * @param country country of the address
   * @param customer customer number to which the address belongs
   * @param isMailing true if the address is a mailing address
   * @param isPrimary true if the address is the primary address
   * @param line1 first line of the address
   * @param line2 second line of the address
   * @param otherType other type of the address
   * @param postCode postcode of the address
   * @param suburb suburb of the address
   * @param type type of the address
   * @return true if the address was added successfully
   * @throws ValidationException  if a required field or address type is invalid
   * @throws IOException if network or mainframe connection fails
   * @throws LavsException if the mainframe rejects the request
   */
  public boolean addAddress(String city,
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
    CustomerAddressService addressService = new CustomerAddressService(adapter);
    addressService.addAddress(city,  country,  customer,  isMailing,  isPrimary,  line1,  line2,  otherType,  postCode,  suburb,  type);
    return true;
  }

  /**
   * Updates an existing address of a customer via the mainframe adapter after validating the address fields.
   * @param city city of the address
   * @param country country of the address
   * @param customer customer number to which the address belongs
   * @param addressId ID of the address to update
   * @param isMailing true if the address is a mailing address
   * @param isPrimary true if the address is the primary address
   * @param line1 first line of the address
   * @param line2 second line of the address
   * @param otherType other type of the address
   * @param postCode postcode of the address
   * @param suburb suburb of the address
   * @param type type of the address
   * @return true if the address was updated successfully
   * @throws ValidationException if the address id, a required field or address type is invalid
   * @throws IOException if network or mainframe connection fails
   * @throws LavsException if the mainframe rejects the request
   */
  public boolean updateAddress(String city,
            String country,
            String customer,
            long addressId,
            boolean isMailing,
            boolean isPrimary,
            String line1,
            String line2,
            String otherType,
            String postCode,
            String suburb,
            String type) throws IOException, LavsException {
    CustomerAddressService addressService = new CustomerAddressService(adapter);
    addressService.updateAddress(city, country, customer, addressId, isMailing, isPrimary, line1, line2, otherType, postCode, suburb, type);
    return true;
  }

  /**
   * Deletes an address of a customer via the mainframe adapter.
   * @param customerId the customer number the address belongs to
   * @param addressId the ID of the address to delete
   * @return true if the address was deleted successfully
   * @throws ValidationException if either identifier is missing or invalid
   * @throws IOException if network or mainframe connection fails
   * @throws LavsException if the mainframe rejects the request
   */
  public boolean deleteAddress(String customerId, long addressId) throws IOException, LavsException {
    CustomerAddressService addressService = new CustomerAddressService(adapter);
    addressService.deleteAddress(customerId, addressId);
    return true;
  }

  /**
   * Lists customers via the mainframe adapter.
   * @param name the name of the customer to search for
   * @param page the page number to retrieve
   * @param size the number of results per page
   * @return an array of customer items
   * @throws IOException
   * @throws LavsException
   */
  public Search.Item[] listCustomers(String name, int page, int size) throws IOException, LavsException {
    Customer customerService = new Customer(adapter);
    return customerService.listCustomers((long) page, name, (long) size);
  }

  /**
   * Retrieves a customer via the mainframe adapter.
   * @param customerId id of the customer to retrieve
   * @return a Retrieve object containing the customer details
   * @throws IOException if network or mainframe connection fails
   * @throws LavsException if the mainframe rejects the request
   */
  public Retrieve getCustomer(String customerId) throws IOException, LavsException{
    Customer customerService = new Customer(adapter);
    return customerService.retrieveCustomer(customerId);
  }

  /**
   * Lists customer addresses via the mainframe adapter.
   * @param customerId the customer number to retrieve addresses for
   * @param page the page number to retrieve
   * @param size the number of results per page
   * @return an array of customer address items
   * @throws IOException if network or mainframe connection fails
   * @throws LavsException if the mainframe rejects the request
   */
  public List.Item[] listCustomerAddresses(String customerId, int page, int size) throws IOException, LavsException {
    CustomerAddressService addressService = new CustomerAddressService(adapter);
    return addressService.listAddresses(customerId, (long) page, (long) size);
  }

  /**
   * Retrieves a customer address via the mainframe adapter.
   * @param customerId the customer number to retrieve the address for
   * @param addressId the ID of the address to retrieve
   * @return a Retrieve object containing the address details
   * @throws IOException if network or mainframe connection fails
   * @throws LavsException if the mainframe rejects the request
   */
  public mainframe.client.messages.customerAddress.Retrieve getAddress(String customerId, long addressId) throws IOException, LavsException {
    CustomerAddressService addressService = new CustomerAddressService(adapter);
    return addressService.retrieveAddress(customerId, addressId);
  }

  /**
   * Lists customer phone numbers via the mainframe adapter.
   * @param customerId the customer number to retrieve phone numbers for
   * @param page the page number to retrieve
   * @param size the number of results per page
   * @return an array of customer phone number items
   * @throws IOException if network or mainframe connection fails
   * @throws LavsException if the mainframe rejects the request
   */
  public mainframe.client.messages.customerPhoneNumber.List.Item[] listPhoneNumbers(String customerId, int page, int size) throws IOException, LavsException {
    CustomerPhoneNumberService phoneService = new CustomerPhoneNumberService(adapter);
    return phoneService.listPhoneNumbers(customerId, (long) page, (long) size);
  }

  /**
   * Retrieves a customer phone number via the mainframe adapter.
   * @param customerId the customer number to retrieve the phone number for
   * @param phoneNumberId the ID of the phone number to retrieve
   * @return a Retrieve object containing the phone number details
   * @throws IOException if network or mainframe connection fails
   * @throws LavsException if the mainframe rejects the request
   */
  public mainframe.client.messages.customerPhoneNumber.Retrieve getPhoneNumber(String customerId, long phoneNumberId) throws IOException, LavsException {
    CustomerPhoneNumberService phoneService = new CustomerPhoneNumberService(adapter);
    return phoneService.retrievePhoneNumber(customerId, phoneNumberId);
  }

  /**
   * Adds a phone number to an existing customer via the mainframe adapter after validating the phone fields.
   * @param canTxt true if the number can receive text messages
   * @param countryCode country calling code of the number
   * @param customer customer number to which the phone number belongs
   * @param isPrimary true if the number is the primary phone number
   * @param number the phone number
   * @param otherType other type of the phone number
   * @param type type of the phone number
   * @return true if the phone number was added successfully
   * @throws ValidationException if a required field or phone type is invalid
   * @throws IOException if network or mainframe connection fails
   * @throws LavsException if the mainframe rejects the request
   */
  public boolean addPhoneNumber(boolean canTxt, String countryCode, String customer, boolean isPrimary, String number, String otherType, String type) throws IOException, LavsException {
    CustomerPhoneNumberService phoneService = new CustomerPhoneNumberService(adapter);
    phoneService.addPhoneNumber(canTxt, countryCode, customer, isPrimary, number, otherType, type);
    return true;
  }

  /**
   * Updates an existing phone number of a customer via the mainframe adapter after validating the phone fields.
   * @param canTxt true if the number can receive text messages
   * @param countryCode country calling code of the number
   * @param customer customer number to which the phone number belongs
   * @param phoneNumberId ID of the phone number to update
   * @param isPrimary true if the number is the primary phone number
   * @param number the phone number
   * @param otherType other type of the phone number
   * @param type type of the phone number
   * @return true if the phone number was updated successfully
   * @throws ValidationException if the phone number id, a required field or phone type is invalid
   * @throws IOException if network or mainframe connection fails
   * @throws LavsException if the mainframe rejects the request
   */
  public boolean updatePhoneNumber(boolean canTxt, String countryCode, String customer, long phoneNumberId, boolean isPrimary, String number, String otherType, String type) throws IOException, LavsException {
    CustomerPhoneNumberService phoneService = new CustomerPhoneNumberService(adapter);
    phoneService.updatePhoneNumber(canTxt, countryCode, customer, phoneNumberId, isPrimary, number, otherType, type);
    return true;
  }

  /**
   * Deletes a phone number of a customer via the mainframe adapter.
   * @param customerId the customer number the phone number belongs to
   * @param phoneNumberId the ID of the phone number to delete
   * @return true if the phone number was deleted successfully
   * @throws ValidationException if either identifier is missing or invalid
   * @throws IOException if network or mainframe connection fails
   * @throws LavsException if the mainframe rejects the request
   */
  public boolean deletePhoneNumber(String customerId, long phoneNumberId) throws IOException, LavsException {
    CustomerPhoneNumberService phoneService = new CustomerPhoneNumberService(adapter);
    phoneService.deletePhoneNumber(customerId, phoneNumberId);
    return true;
  }

  /**
   * Adds an employer to an existing customer via the mainframe adapter after validating the employer fields.
   * @param customer customer number to which the employer belongs
   * @param name name of the employer
   * @param position the customer's position at the employer
   * @param salary the customer's salary from the employer
   * @param type type of the employer (Primary or Secondary)
   * @return true if the employer was added successfully
   * @throws ValidationException if a required field, the salary or employer type is invalid
   * @throws IOException if network or mainframe connection fails
   * @throws LavsException if the mainframe rejects the request
   */
  public boolean addEmployer(String customer, String name, String position, double salary, String type) throws IOException, LavsException {
    CustomerEmployerService employerService = new CustomerEmployerService(adapter);
    employerService.addEmployer(customer, name, position, salary, type);
    return true;
  }

  /**
   * Updates an existing employer of a customer via the mainframe adapter after validating the employer fields.
   * @param customer customer number to which the employer belongs
   * @param employerId ID of the employer to update
   * @param name name of the employer
   * @param position the customer's position at the employer
   * @param salary the customer's salary from the employer
   * @param type type of the employer (Primary or Secondary)
   * @return true if the employer was updated successfully
   * @throws ValidationException if the employer id, a required field, the salary or employer type is invalid
   * @throws IOException if network or mainframe connection fails
   * @throws LavsException if the mainframe rejects the request
   */
  public boolean updateEmployer(String customer, long employerId, String name, String position, double salary, String type) throws IOException, LavsException {
    CustomerEmployerService employerService = new CustomerEmployerService(adapter);
    employerService.updateEmployer(customer, employerId, name, position, salary, type);
    return true;
  }

  /**
   * Deletes an employer of a customer via the mainframe adapter.
   * @param customerId the customer number the employer belongs to
   * @param employerId the ID of the employer to delete
   * @return true if the employer was deleted successfully
   * @throws ValidationException if either identifier is missing or invalid
   * @throws IOException if network or mainframe connection fails
   * @throws LavsException if the mainframe rejects the request
   */
  public boolean deleteEmployer(String customerId, long employerId) throws IOException, LavsException {
    CustomerEmployerService employerService = new CustomerEmployerService(adapter);
    employerService.deleteEmployer(customerId, employerId);
    return true;
  }

  /**
   * Lists customer employers via the mainframe adapter.
   * @param customerId the customer number to retrieve employers for
   * @param page the page number to retrieve
   * @param size the number of results per page
   * @return an array of customer employer items
   * @throws IOException if network or mainframe connection fails
   * @throws LavsException if the mainframe rejects the request
   */
  public mainframe.client.messages.customerEmployer.List.Item[] listEmployers(String customerId, int page, int size) throws IOException, LavsException {
    CustomerEmployerService employerService = new CustomerEmployerService(adapter);
    return employerService.listEmployers(customerId, (long) page, (long) size);
  }

  /**
   * Retrieves a customer employer via the mainframe adapter.
   * @param customerId the customer number to retrieve the employer for
   * @param employerId the ID of the employer to retrieve
   * @return a Retrieve object containing the employer details
   * @throws IOException if network or mainframe connection fails
   * @throws LavsException if the mainframe rejects the request
   */
  public mainframe.client.messages.customerEmployer.Retrieve getEmployer(String customerId, long employerId) throws IOException, LavsException {
    CustomerEmployerService employerService = new CustomerEmployerService(adapter);
    return employerService.retrieveEmployer(customerId, employerId);
  }

  /**
   * Adds an email address to an existing customer via the mainframe adapter after validating the email fields.
   * @param address the email address
   * @param customer customer number to which the email belongs
   * @param isPrimary true if the email is the primary email
   * @param otherType other type of the email
   * @param type type of the email
   * @return true if the email was added successfully
   * @throws ValidationException if a required field, the address format or email type is invalid
   * @throws IOException if network or mainframe connection fails
   * @throws LavsException if the mainframe rejects the request
   */
  public boolean addEmail(String address, String customer, boolean isPrimary, String otherType, String type) throws IOException, LavsException {
    CustomerEmailService emailService = new CustomerEmailService(adapter);
    emailService.addEmail(address, customer, isPrimary, otherType, type);
    return true;
  }

  /**
   * Updates an existing email address of a customer via the mainframe adapter after validating the email fields.
   * @param address the email address
   * @param customer customer number to which the email belongs
   * @param emailId ID of the email to update
   * @param isPrimary true if the email is the primary email
   * @param otherType other type of the email
   * @param type type of the email
   * @return true if the email was updated successfully
   * @throws ValidationException if the email id, a required field, the address format or email type is invalid
   * @throws IOException if network or mainframe connection fails
   * @throws LavsException if the mainframe rejects the request
   */
  public boolean updateEmail(String address, String customer, long emailId, boolean isPrimary, String otherType, String type) throws IOException, LavsException {
    CustomerEmailService emailService = new CustomerEmailService(adapter);
    emailService.updateEmail(address, customer, emailId, isPrimary, otherType, type);
    return true;
  }

  /**
   * Deletes an email address of a customer via the mainframe adapter.
   * @param customerId the customer number the email belongs to
   * @param emailId the ID of the email to delete
   * @return true if the email was deleted successfully
   * @throws ValidationException if either identifier is missing or invalid
   * @throws IOException if network or mainframe connection fails
   * @throws LavsException if the mainframe rejects the request
   */
  public boolean deleteEmail(String customerId, long emailId) throws IOException, LavsException {
    CustomerEmailService emailService = new CustomerEmailService(adapter);
    emailService.deleteEmail(customerId, emailId);
    return true;
  }

  /**
   * Lists customer emails via the mainframe adapter.
   * @param customerId the customer number to retrieve emails for
   * @param page the page number to retrieve
   * @param size the number of results per page
   * @return an array of customer email items
   * @throws IOException if network or mainframe connection fails
   * @throws LavsException if the mainframe rejects the request
   */
  public mainframe.client.messages.customerEmail.List.Item[] listEmails(String customerId, int page, int size) throws IOException, LavsException {
    CustomerEmailService emailService = new CustomerEmailService(adapter);
    return emailService.listEmails(customerId, (long) page, (long) size);
  }

  /**
   * Retrieves a customer email via the mainframe adapter.
   * @param customerId the customer number to retrieve the email for
   * @param emailId the ID of the email to retrieve
   * @return a Retrieve object containing the email details
   * @throws IOException if network or mainframe connection fails
   * @throws LavsException if the mainframe rejects the request
   */
  public mainframe.client.messages.customerEmail.Retrieve getEmail(String customerId, long emailId) throws IOException, LavsException {
    CustomerEmailService emailService = new CustomerEmailService(adapter);
    return emailService.retrieveEmail(customerId, emailId);
  }


  /**
   * Adds a loan to a customer via the mainframe adapter after validating all loan fields and checking for a mailing address.
   * @param customerNumber cutomer number to which the loan belongs
   * @param principal amount borrowed; must be greater than zero
   * @param annualRate annual rate as a percentage; must not be negative
   * @param term loan term in months/periods; must be greater than zero
   * @param loanType valid loan category code
   * @param paymentFrequency valid payment frequency code
   * @param compoundType valid compounding frequency code
   * @param renewalDate renewal date of the loan
   * @param startDate start date of the loan
   * @param guarantorId guarantor identifier (customer id of guarantor)
   * @return true if the loan was added successfully
   * @throws IOException if network or mainframe connection fails
   * @throws LavsException if the mainframe rejects the request or validation fails
   */
  public boolean addLoan(String customerNumber, double principal, double annualRate, int term, String loanType, String paymentFrequency, String compoundType, String renewalDate, String startDate, String guarantorId) throws IOException, LavsException {
    Loan loanService = new Loan(adapter);
    loanService.addLoan(customerNumber, principal, annualRate, term, loanType, paymentFrequency, compoundType, renewalDate, startDate, guarantorId);
    return true;
  }

  /**
   * Retrieves a list of loans for a customer via the mainframe adapter.
   * @param customerNumber customer number to retrieve loans for
   * @param page page number of loans to retrieve
   * @param size number of loans to retrieve per page
   * @return an array of loan items
   * @throws IOException if network or mainframe connection fails
   * @throws LavsException if the mainframe rejects the request
   */
  public mainframe.client.messages.loan.List.Item[] listLoans(String customerNumber, int page, int size) throws IOException, LavsException {
    Loan loanService = new Loan(adapter);
    return loanService.retrieveLoans(customerNumber, page, size);
  }

  /**
   * Retrieves a loan via the mainframe adapter.
   * @param loanNumber loan number to retrieve
   * @return a Retrieve object containing the loan details
   * @throws IOException if network or mainframe connection fails
   * @throws LavsException if the mainframe rejects the request
   */
  public mainframe.client.messages.loan.Retrieve getLoan() throws IOException, LavsException {
    Loan loanService = new Loan(adapter);
    return loanService.retrieveLoan(currentLoanId);
  }

  /**
   * Retrieves a repayment schedule for a loan via the mainframe adapter.
   * @param page page number of the schedule to retrieve
   * @param size number of schedule items to retrieve per page
   * @return an array of repayment schedule items
   * @throws IOException if network or mainframe connection fails
   * @throws LavsException if the mainframe rejects the request
   */
  public mainframe.client.messages.loanRepaymentSchedule.List.Item[] getLoanRepaymentSchedule(int page, int size) throws IOException, LavsException {
    Loan loanService = new Loan(adapter);
    return loanService.retrieveRepaymentSchedule(currentLoanId, page, size);
  }

  /**
   * Retrieves a summary of the current loan via the mainframe adapter.
   * @return a Retrieve object containing the loan summary details
   * @throws IOException if network or mainframe connection fails
   * @throws LavsException if the mainframe rejects the request
   */
  public mainframe.client.messages.loanSummary.Retrieve getLoanSummary() throws IOException, LavsException {
    Loan loanService = new Loan(adapter);
    return loanService.retrieveLoanSummary(currentLoanId);
  }

  /**
   * Updates a loan via the mainframe adapter after validating all loan fields and checking for a mailing address.
   * @param customerNumber customer number to which the loan belongs
   * @param loanNumber loan number to update
   * @param paymentAmount payment amount of the loan; must be greater than zero
   * @param paymentFrequency payment frequency of the loan; must not be negative
   * @param principal principal of the loan; must be greater than zero
   * @param rateAmount rate amount of the loan; must not be negative
   * @param rateFrequency rate frequency of the loan; must not be negative
   * @param rateType rate type of the loan; must not be negative
   * @param renewalDate renewal date of the loan; must not be negative
   * @param startDate start date of the loan; must not be negative
   * @return true if the loan was updated successfully
   * @throws IOException if network or mainframe connection fails
   * @throws LavsException if the mainframe rejects the request
   */
  public boolean updateLoan(String customerNumber, String loanNumber, double paymentAmount, String paymentFrequency, double principal, double rateAmount, String rateFrequency, String rateType, String renewalDate, String startDate) throws IOException, LavsException {
    Loan loanService = new Loan(adapter);
    loanService.updateLoan(customerNumber, loanNumber, paymentAmount, paymentFrequency, principal, rateAmount, rateFrequency, rateType, renewalDate, startDate);
    return true;
  }

  /**
   * Updates the status of a loan via the mainframe adapter.
   * @param status new loan status
   * @return true if the loan status was updated successfully
   * @throws IOException if network or mainframe connection fails
   * @throws LavsException if the mainframe rejects the request
   */
  public boolean updateLoanStatus(String status) throws IOException, LavsException {
    Loan loanService = new Loan(adapter);
    loanService.updateLoanStatus(currentLoanId, status);
    return true;
  }

  /**
   * Adds a coborrower to a loan via the mainframe adapter after validating all coborrower fields and checking for a mailing address.
   * @param customerNumber customer number to which the coborrower belongs
   * @param type coborrower type
   * @return true if the coborrower was added successfully
   * @throws IOException if network or mainframe connection fails
   * @throws LavsException if the mainframe rejects the request
   */
  public boolean addCoborrower(String customerNumber, String type) throws IOException, LavsException {
    Coborrower coborrowerService = new Coborrower(adapter);
    coborrowerService.addCoborrower(customerNumber, currentLoanId, type);
    return true;
  }

  /**
   * Retrieves a paginated list of coborrowers for a loan via the mainframe adapter.
   * @param loanNumber loan number to retrieve coborrowers for
   * @param page page number of coborrowers to retrieve
   * @param size number of coborrowers to retrieve per page
   * @return an array of coborrower items
   * @throws IOException if network or mainframe connection fails
   * @throws LavsException if the mainframe rejects the request
   */
  public mainframe.client.messages.loanCoborrower.List.Item[] listCoborrowers(int page, int size) throws IOException, LavsException {
    Coborrower coborrowerService = new Coborrower(adapter);
    return coborrowerService.retrieveCoborrowers(currentLoanId, page, size);
  }

  /**
   * Removes a coborrower from a loan via the mainframe adapter after validating all coborrower fields and checking for a mailing address.
   * @param coborrowerId coborrower identifier to remove (get this from list of coborrowers)
   * @return true if the coborrower was removed successfully
   * @throws IOException if network or mainframe connection fails
   * @throws LavsException if the mainframe rejects the request
   */
  public boolean removeCoborrower(long coborrowerId) throws IOException, LavsException {
    Coborrower coborrowerService = new Coborrower(adapter);
    coborrowerService.deleteCoborrower(coborrowerId, currentLoanId);
    return true;
  }

  public boolean updateCoborrower(long coborrowerId, String customerNumber, String type) throws IOException, LavsException {
    removeCoborrower(coborrowerId);
    addCoborrower(customerNumber, type);
    return true;
  }

  public boolean addPayment(double amountPaid, String date) throws IOException, LavsException {
    Payment paymentService = new Payment(adapter);
    paymentService.addPayment(amountPaid, date, currentLoanId);
    return true;
  }

  public boolean deletePayment(long paymentId) throws IOException, LavsException {
    Payment paymentService = new Payment(adapter);
    paymentService.deletePayment(paymentId, currentLoanId);
    return true;
  }

  public mainframe.client.messages.loanPayment.List.Item[] listPayments(int page, int size) throws IOException, LavsException {
    Payment paymentService = new Payment(adapter);
    return paymentService.listPayments(currentLoanId, (long) page, (long) size);
  }

  /**
     * Loads key-value pairs from the .env file in the project root.
     *
     * @return a map of environment variable names to values
     * @throws IOException if the .env file cannot be read
     */
    public static Map<String, String> loadEnv() throws IOException {
        Map<String, String> envMap = new HashMap<>();
        try (BufferedReader reader = new BufferedReader(new FileReader(".env"))) {
            String line;
            while ((line = reader.readLine()) != null) {
                line = line.trim();
                if (line.isEmpty() || line.startsWith("#")) {
                    continue;
                }
                int equalsIndex = line.indexOf('=');
                if (equalsIndex > 0) {
                    String key = line.substring(0, equalsIndex).trim();
                    String value = line.substring(equalsIndex + 1).trim();
                    envMap.put(key, value);
                }
            }
        }
        return envMap;
    }
}