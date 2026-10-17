package business.system;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.Map;

import business.Main;
import business.lavsexceptions.LavsException;
import business.system.customer.CustomerAddressService;
import business.system.customer.CustomerEmailService;
import business.system.customer.CustomerEmployerService;
import business.system.customer.CustomerPhoneNumberService;
import business.system.dto.CustomerSummary;
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
}