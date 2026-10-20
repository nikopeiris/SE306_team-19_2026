package business.system;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import business.Main;
import business.lavsexceptions.LavsException;
import business.system.customer.CustomerAddressService;
import business.system.customer.CustomerDocumentService;
import business.system.customer.CustomerEmailService;
import business.system.customer.CustomerEmployerService;
import business.system.customer.CustomerPhoneNumberService;
import business.system.dto.CustomerDetails;
import business.system.dto.CustomerDocument;
import business.system.dto.CustomerSummary;
import business.system.dto.LoanListResult;
import business.system.dto.LoanStats;
import business.system.dto.LoanSummary;
import business.validation.ValidationException;
import business.validation.ValidationResult;
import dataaccess.systemadapters.MainframeAdapter;
import mainframe.client.Status;
import mainframe.client.messages.customer.Create;
import mainframe.client.messages.customer.Retrieve;
import mainframe.client.messages.customer.Search;
import mainframe.client.messages.customer.Update;

/**
 * Business service for adding customers through the mainframe adapter.
 */
public class Customer {
    private final MainframeAdapter customerAdapter;
    private final CustomerDetailsService detailsService;
    private final LoanSearchService loanSearchService;
    private final LoanStatusService loanStatusService;
    private final LoanDetailsService loanDetailsService;
    private final CustomerDocumentService documentService;
    private final LoanStatsService loanStatsService;
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
        this.detailsService = new CustomerDetailsService(this, customerAdapter);
        this.loanSearchService = new LoanSearchService(new Loan(customerAdapter));
        this.loanStatusService = new LoanStatusService(customerAdapter);
        this.loanDetailsService = new LoanDetailsService(customerAdapter);
        this.documentService = new CustomerDocumentService(customerAdapter);
        this.loanStatsService = new LoanStatsService();
    }

    /**
     * Adds a customer after validating all customer fields. Only the first name is required.
     *
     * @return the mainframe message containing the created customer's details
     * @throws ValidationException if the first name is null or blank
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
                trimOrNull(dateOfBirth),
                trimOrNull(familyName),
                firstName.trim(),
                trimOrNull(nationality),
                organisation,
                trimOrNull(title));
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

    /** Validates the customer's required personal and organisation fields. */
    public void validateCustomerDetails(
            String dateOfBirth,
            String familyName,
            String firstName,
            String nationality,
            Long organisation,
            String title) throws ValidationException {
        new ValidationResult()
                .requireNotBlank("firstName", firstName, "First name cannot be empty")
                .requirePositive("organisation", organisation, "Organisation")
                .throwIfInvalid();
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

    /** Copies the fields we need out of a mainframe search result. */
    private CustomerSummary toSummary(Search.Item item) {
        return new CustomerSummary(
                item.getNumber(),
                item.getFirstName(),
                item.getFamilyName());
    }

    public mainframe.client.messages.customerAddress.List.Item[] listCustomerAddresses(String number) throws IOException, LavsException {
        return new CustomerAddressService(customerAdapter).listAddresses(number, 1L, 100L);
    }

    public mainframe.client.messages.customerPhoneNumber.List.Item[] listCustomerPhoneNumbers(String number) throws IOException, LavsException {
        return new CustomerPhoneNumberService(customerAdapter).listPhoneNumbers(number, 1L, 100L);
    }

    public mainframe.client.messages.customerEmail.List.Item[] listCustomerEmails(String number) throws IOException, LavsException {
        return new CustomerEmailService(customerAdapter).listEmails(number, 1L, 100L);
    }

    public mainframe.client.messages.customerEmployer.List.Item[] listCustomerEmployers(String number) throws IOException, LavsException {
        return new CustomerEmployerService(customerAdapter).listEmployers(number, 1L, 100L);
    }

    /**
     * Retrieves full customer details including personal and contact info.
     */
    public Retrieve getCustomerDetails(String number) throws IOException, LavsException {
        return retrieveCustomer(number);
    }

    /**
     * Updates an existing customer's personal details after validating them.
     * Only the first name is required.
     *
     * @return the mainframe message containing the updated customer's details
     * @throws ValidationException if the first name is null or blank
     * @throws LavsException if the mainframe rejects the request
     * @throws IOException if the mainframe cannot be reached
     */
    public Update updateCustomer(
            String number,
            String dateOfBirth,
            String familyName,
            String firstName,
            String nationality,
            String title) throws IOException, LavsException {
        if (number == null || number.isBlank()) {
            throw new IllegalArgumentException("Customer number cannot be null or empty");
        }
        // The organisation is only set when a customer is created, so any valid value will do here.
        validateCustomerDetails(
                dateOfBirth, familyName, firstName, nationality, 1L, title);

        Update updateMessage = new Update(
                trimOrNull(dateOfBirth),
                trimOrNull(familyName),
                firstName.trim(),
                trimOrNull(nationality),
                number.trim(),
                trimOrNull(title));
        Status status = customerAdapter.send(updateMessage);
        Main.isValidStatus(status);
        return updateMessage;
    }

    private static String trimOrNull(String value) {
        return value == null ? null : value.trim();
    }

    /**
     * Reads the loans of the given customers (the mainframe only lists loans per customer,
     * so this is at least one request per customer). The listener, if given, hears about
     * each customer's loans as they arrive and can return false to stop early.
     *
     * @throws IOException if the mainframe cannot be reached
     */
    public LoanListResult listLoansFor(List<CustomerSummary> customers,
            LoanSearchService.ProgressListener listener) throws IOException {
        return loanSearchService.listLoansFor(customers, listener);
    }

    /** The statuses a loan with this status can be changed to (empty if none). */
    public List<String> allowedLoanStatuses(String currentStatus) {
        return loanStatusService.allowedStatuses(currentStatus);
    }

    /**
     * Changes a loan's status. Sending a loan for checking (Pending) first makes sure the
     * loan and all its borrowers are ready; see {@link LoanStatusService#changeStatus}.
     *
     * @throws ValidationException if the change is not allowed or a borrower is not ready
     * @throws LavsException if the mainframe rejects the request
     * @throws IOException if the mainframe cannot be reached
     */
    public void changeLoanStatus(String loanNumber, String newStatus)
            throws IOException, LavsException {
        loanStatusService.changeStatus(loanNumber, newStatus);
    }

    /** Narrows a loan list by loan number; a blank query returns the whole list. */
    public List<LoanSummary> filterLoansByNumber(List<LoanSummary> loans, String query) {
        return loanSearchService.filterByLoanNumber(loans, query);
    }

    /**
     * Narrows a loan list by loan number and by status. A blank number query and a
     * null/blank status each mean "no filter".
     */
    public List<LoanSummary> filterLoans(List<LoanSummary> loans, String numberQuery, String status) {
        return loanStatsService.filterByStatus(filterLoansByNumber(loans, numberQuery), status);
    }

    /** The distinct statuses present in the given loans, for the dashboard's status filter. */
    public List<String> loanStatusesIn(List<LoanSummary> loans) {
        return loanStatsService.statusesIn(loans);
    }

    /** Totals, awaiting-verification and incomplete counts, and counts per status. */
    public LoanStats summariseLoans(List<LoanSummary> loans) {
        return loanStatsService.summarise(loans);
    }

    /** The loan tab's business service: form choices, adding a loan, and reading loan details. */
    public LoanDetailsService loanDetails() {
        return loanDetailsService;
    }

    /** Lists a customer's documents (the first 100). */
    public List<CustomerDocument> listCustomerDocuments(String number)
            throws IOException, LavsException {
        mainframe.client.messages.customerDocument.List.Item[] items =
                documentService.listDocuments(number, 1L, 100L);
        List<CustomerDocument> documents = new ArrayList<>();
        if (items != null) {
            for (mainframe.client.messages.customerDocument.List.Item item : items) {
                documents.add(new CustomerDocument(
                        item.getId(),
                        item.getType(),
                        item.getFileName()));
            }
        }
        return documents;
    }

    /**
     * Adds a document to an existing customer. The type, file name and size are required;
     * the file type is optional.
     *
     * @throws ValidationException if a required field is missing or a field is too long
     */
    public void addCustomerDocument(String number, String type, String fileName,
            String fileType, Long size) throws IOException, LavsException {
        documentService.addDocument(number, fileName, fileType, size, type);
    }

    /** Removes a document from a customer. */
    public void deleteCustomerDocument(String number, Long documentId)
            throws IOException, LavsException {
        documentService.deleteDocument(number, documentId);
    }

    /** Loads a customer's personal details, addresses, phones, emails and employers. */
    public CustomerDetails loadCustomerDetails(String number) throws IOException, LavsException {
        return detailsService.load(number);
    }

    /**
     * Creates the customer (if {@code original} is null) or updates it, and saves all
     * contact and employer changes. See {@link CustomerDetailsService#save}.
     */
    public CustomerDetails.SaveResult saveCustomer(CustomerDetails original, CustomerDetails edited)
            throws IOException, LavsException {
        return detailsService.save(original, edited);
    }

    public void validatePersonalDetails(CustomerDetails.Personal personal) throws ValidationException {
        detailsService.validatePersonal(personal);
    }

    public void validateAddresses(List<CustomerDetails.Address> addresses) throws ValidationException {
        detailsService.validateAddresses(addresses);
    }

    public void validatePhones(List<CustomerDetails.Phone> phones) throws ValidationException {
        detailsService.validatePhones(phones);
    }

    public void validateEmails(List<CustomerDetails.Email> emails) throws ValidationException {
        detailsService.validateEmails(emails);
    }

    public void validateEmployers(List<CustomerDetails.Employer> employers) throws ValidationException {
        detailsService.validateEmployers(employers);
    }
}