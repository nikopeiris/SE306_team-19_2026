package business.system;

import business.lavsexceptions.LavsException;
import business.system.dto.CustomerSummary;
import business.system.dto.CustomerDetails;
import business.system.dto.LoanSummary;
import business.system.dto.LoanListResult;
import business.system.customer.CustomerAddressService;
import business.system.customer.CustomerDocumentService;
import business.system.customer.CustomerEmailService;
import business.system.customer.CustomerEmployerService;
import business.system.customer.CustomerPhoneNumberService;
import business.system.customer.CustomerOptions;
import business.validation.ValidationException;
import dataaccess.systemadapters.MainframeAdapter;
import mainframe.client.Message;
import mainframe.client.Status;
import mainframe.client.messages.customer.Create;
import mainframe.client.messages.customer.Retrieve;
import mainframe.client.messages.customer.Search;
import mainframe.client.messages.customer.Update;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.IOException;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.function.BiFunction;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.mock;

@ExtendWith(MockitoExtension.class)
class CustomerTest {
    @Mock
    private MainframeAdapter adapter;

    private Customer customer;

    @BeforeEach
    void setUp() {
        customer = new Customer(adapter);
    }

    @Test
    void addCustomerSendsTrimmedValuesAndReturnsMessage() throws Exception {
        when(adapter.send(org.mockito.ArgumentMatchers.any(Create.class)))
                .thenReturn(new Status(1));

        Create result = customer.addCustomer(
                "  1990-01-01 ", " Smith ", " Jane ",
                " NZ ", 7L, " Ms ");

        ArgumentCaptor<Create> captor = ArgumentCaptor.forClass(Create.class);
        verify(adapter).send(captor.capture());
        assertEquals(result, captor.getValue());
        assertEquals("1990-01-01", result.getDateOfBirth());
        assertEquals("Smith", result.getFamilyName());
        assertEquals("Jane", result.getFirstName());
        assertEquals("NZ", result.getNationality());
        assertEquals("Ms", result.getTitle());
    }

    @Test
    void constructorRejectsNullAdapter() {
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                () -> new Customer(null));

        assertEquals("Customer adapter cannot be null", exception.getMessage());
    }

    @Test
    void retrieveCustomerSendsRetrieveMessageAndReturnsIt() throws Exception {
        when(adapter.send(org.mockito.ArgumentMatchers.any(Retrieve.class)))
                .thenReturn(new Status(1));

        Retrieve result = customer.retrieveCustomer("C123");

        assertEquals("C123", result.getNumber());
        verify(adapter).send(org.mockito.ArgumentMatchers.same(result));
    }

    @Test
    void retrieveCustomerRejectsMissingCustomerNumber() {
        assertThrows(IllegalArgumentException.class,
                () -> customer.retrieveCustomer(null));
        assertThrows(IllegalArgumentException.class,
                () -> customer.retrieveCustomer(" "));
        verifyNoInteractions(adapter);
    }

    @Test
    void retrieveCustomerThrowsLavsExceptionWhenMainframeRejectsRequest() throws IOException {
        when(adapter.send(org.mockito.ArgumentMatchers.any(Retrieve.class)))
                .thenReturn(new Status(404, "Customer not found", 1));

        LavsException exception = assertThrows(LavsException.class,
                () -> customer.retrieveCustomer("C123"));

        assertEquals("Customer not found", exception.getMessage());
    }

    @Test
    void updateCustomerSendsTrimmedValues() throws Exception {
        when(adapter.send(any(Update.class))).thenReturn(new Status(1));

        Update result = customer.updateCustomer(" C1 ", " 2000-01-01 ",
                " Smith ", " Jane ", " NZ ", " Ms ");

        assertEquals("C1", result.getNumber());
        assertEquals("Jane", result.getFirstName());
        assertEquals("Smith", result.getFamilyName());
        assertEquals("Ms", result.getTitle());
        verify(adapter).send(result);
    }

    @Test
    void updateCustomerRejectsMissingNumberOrFirstName() {
        assertThrows(IllegalArgumentException.class,
                () -> customer.updateCustomer(" ", "", "", "", "", ""));
        assertThrows(ValidationException.class,
                () -> customer.updateCustomer("C1", "", "", " ", "", ""));
        verifyNoInteractions(adapter);
    }

    @Test
    void getCustomerDetailsDelegatesToRetrieve() throws Exception {
        when(adapter.send(any(Retrieve.class))).thenReturn(new Status(1));

        Retrieve result = customer.getCustomerDetails("C1");

        assertEquals("C1", result.getNumber());
        verify(adapter).send(result);
    }

    @Test
    void customerFacadeDelegatesContactLoanAndDocumentOperations() throws Exception {
        CustomerDocumentService documentService = mock(CustomerDocumentService.class);
        CustomerDetailsService detailsService = mock(CustomerDetailsService.class);
        LoanSearchService loanSearchService = mock(LoanSearchService.class);
        LoanStatusService loanStatusService = mock(LoanStatusService.class);
        LoanDetailsService loanDetailsService = mock(LoanDetailsService.class);
        replace(customer, "documentService", documentService);
        replace(customer, "detailsService", detailsService);
        replace(customer, "loanSearchService", loanSearchService);
        replace(customer, "loanStatusService", loanStatusService);
        replace(customer, "loanDetailsService", loanDetailsService);

        when(loanSearchService.filterByLoanNumber(any(), eq("L"))).thenReturn(List.of());
        when(loanSearchService.listLoansFor(any(), isNull())).thenReturn(new LoanListResult(List.of(), 0, null));
        when(loanStatusService.allowedStatuses("New")).thenReturn(List.of("Pending"));
        CustomerDetails.SaveResult saveResult = new CustomerDetails.SaveResult("C1", List.of());
        when(detailsService.save(any(), any())).thenReturn(saveResult);
        when(detailsService.load("C1")).thenReturn(CustomerDetails.empty());

        assertTrue(customer.listLoansFor(List.of(), null).getLoans().isEmpty());
        assertEquals(List.of("Pending"), customer.allowedLoanStatuses("New"));
        assertEquals(List.of(), customer.filterLoansByNumber(List.of(), "L"));
        assertSame(loanDetailsService, customer.loanDetails());
        assertEquals(List.of(), customer.listCustomerDocuments("C1"));
        customer.addCustomerDocument("C1", "Passport", "a.pdf", "pdf", 1L);
        customer.deleteCustomerDocument("C1", 2L);
        assertEquals(CustomerDetails.empty(), customer.loadCustomerDetails("C1"));
        assertEquals(saveResult, customer.saveCustomer(CustomerDetails.empty(), CustomerDetails.empty()));
        customer.validatePersonalDetails(CustomerDetails.empty().personal());
        customer.validateAddresses(List.of());
        customer.validatePhones(List.of());
        customer.validateEmails(List.of());
        customer.validateEmployers(List.of());

        verify(documentService).addDocument("C1", "a.pdf", "pdf", 1L, "Passport");
        verify(documentService).deleteDocument("C1", 2L);
    }

    @Test
    void customerOptionsExposeSupportedChoices() {
        assertTrue(CustomerOptions.TITLES.contains("Mr"));
        assertTrue(CustomerOptions.ADDRESS_TYPES.contains(CustomerOptions.ADDRESS_TYPE_OTHER));
        assertTrue(CustomerOptions.PHONE_TYPES.contains(CustomerOptions.PHONE_TYPE_OTHER));
        assertTrue(CustomerOptions.EMAIL_TYPES.contains(CustomerOptions.EMAIL_TYPE_OTHER));
        assertEquals(2, List.of(CustomerOptions.EMPLOYER_PRIMARY,
                CustomerOptions.EMPLOYER_SECONDARY).size());
    }

    private static void replace(Object target, String name, Object value) throws Exception {
        Field field = target.getClass().getDeclaredField(name);
        field.setAccessible(true);
        field.set(target, value);
    }

    // answers every customer search with the {number, firstName, familyName} rows
    // that the given function returns for that search text and page
    private void stubSearchResults(BiFunction<String, Long, String[][]> results) throws Exception {
        when(adapter.send(org.mockito.ArgumentMatchers.any(Search.class))).thenAnswer(invocation -> {
            Search search = invocation.getArgument(0);
            String text = (String) readField(search, "_search");
            long page = (Long) readField(search, "_page");
            @SuppressWarnings("unchecked")
            ArrayList<Search.Item> items = (ArrayList<Search.Item>) readField(search, "_data");
            for (String[] row : results.apply(text, page)) {
                items.add(search.new Item("1990-01-01", row[2], row[1], row[0], "Active", "Ms"));
            }
            return new Status(1);
        });
    }

    private static Object readField(Object target, String name) throws ReflectiveOperationException {
        Field field = target.getClass().getDeclaredField(name);
        field.setAccessible(true);
        return field.get(target);
    }

    private static java.util.List<String> numbersOf(java.util.List<CustomerSummary> customers) {
        return customers.stream().map(CustomerSummary::getNumber).toList();
    }

    @Test
    void listAllCustomersSortsByFirstNameThenFamilyNameIgnoringCase() throws Exception {
        stubSearchResults((text, page) -> new String[][] {
                {"C1", "bob", "Smith"},
                {"C2", "Alice", "Zed"},
                {"C3", "alice", "adams"}});

        java.util.List<CustomerSummary> result = customer.listAllCustomers();

        assertEquals(java.util.List.of("C3", "C2", "C1"), numbersOf(result));
        verify(adapter, times(2)).send(org.mockito.ArgumentMatchers.any(Search.class));
    }

    @Test
    void listAllCustomersFollowsFullPagesAndDropsDuplicateNumbers() throws Exception {
        stubSearchResults((text, page) -> {
            if (page == 1) {
                String[][] fullPage = new String[100][];
                for (int i = 0; i < 100; i++) {
                    fullPage[i] = new String[] {"C" + i, String.format("Name%03d", i), "Last"};
                }
                return fullPage;
            }
            return new String[][] {{"C5", "Duplicate", "Ignored"}, {"C100", "Aaron", "Last"}};
        });

        java.util.List<CustomerSummary> result = customer.listAllCustomers();

        assertEquals(101, result.size());
        assertEquals("C100", result.get(0).getNumber());
        assertEquals("Name005", result.stream()
                .filter(c -> c.getNumber().equals("C5")).findFirst().orElseThrow().getFirstName());
        verify(adapter, times(3)).send(org.mockito.ArgumentMatchers.any(Search.class));
    }

    @Test
    void listAllCustomersFallsBackToSingleCharacterSearchesWhenEmptySearchFindsNothing()
            throws Exception {
        stubSearchResults((text, page) -> switch (text) {
            case "a" -> new String[][] {{"C1", "Anna", "Lee"}};
            case "n" -> new String[][] {{"C1", "Anna", "Lee"}, {"C2", "Ben", "Ng"}};
            default -> new String[0][];
        });

        java.util.List<CustomerSummary> result = customer.listAllCustomers();

        assertEquals(java.util.List.of("C1", "C2"), numbersOf(result));
        // one empty search plus one search per letter and digit
        verify(adapter, times(39)).send(org.mockito.ArgumentMatchers.any(Search.class));
    }

    @Test
    void listAllCustomersReturnsEmptyListWhenNoSearchFindsCustomers() throws Exception {
        when(adapter.send(org.mockito.ArgumentMatchers.any(Search.class)))
                .thenReturn(new Status(1));

        assertTrue(customer.listAllCustomers().isEmpty());
    }

    @Test
    void listAllCustomersThrowsLavsExceptionWhenMainframeRejectsSearch() throws IOException {
        when(adapter.send(org.mockito.ArgumentMatchers.any(Search.class)))
                .thenReturn(new Status(500, "Search rejected", 1));

        LavsException exception = assertThrows(LavsException.class,
                () -> customer.listAllCustomers());

        assertEquals("Search rejected", exception.getMessage());
    }

    @Test
    void searchCustomersReturnsSortedMatchesAndBlankSearchIsEmpty() throws Exception {
        stubSearchResults((text, page) -> new String[][] {
                {"C1", "Zoe", "Smith"},
                {"C2", "amy", "Adams"}});

        assertEquals(java.util.List.of("C2", "C1"),
                numbersOf(customer.searchCustomers("  a ")));
        assertTrue(customer.searchCustomers(" ").isEmpty());
    }

    @Test
    void findCustomerSummaryMapsRetrievedCustomer() throws Exception {
        when(adapter.send(any(Retrieve.class))).thenReturn(new Status(1));

        CustomerSummary result = customer.findCustomerSummary(" C1 ");

        assertEquals("C1", result.getNumber());
        verify(adapter).send(any(Retrieve.class));
    }

    @Test
    void customerFacadeDelegatesSearchDocumentsAndDetailsOperations() throws Exception {
        CustomerDocumentService documents = mock(CustomerDocumentService.class);
        CustomerDetailsService details = mock(CustomerDetailsService.class);
        replace(customer, "documentService", documents);
        replace(customer, "detailsService", details);
        when(adapter.send(any(Message.class))).thenReturn(new Status(1));

        when(documents.listDocuments("C1", 1L, 100L)).thenReturn(null);
        CustomerDetails.SaveResult saveResult = new CustomerDetails.SaveResult("C1", List.of());
        when(details.load("C1")).thenReturn(CustomerDetails.empty());
        when(details.save(any(), any())).thenReturn(saveResult);

        assertTrue(customer.listCustomerAddresses("C1") != null);
        assertTrue(customer.listCustomerPhoneNumbers("C1") != null);
        assertTrue(customer.listCustomerEmails("C1") != null);
        assertTrue(customer.listCustomerEmployers("C1") != null);
        assertTrue(customer.listCustomerDocuments("C1").isEmpty());
        customer.addCustomerDocument("C1", "Passport", "id.pdf", "pdf", 10L);
        customer.deleteCustomerDocument("C1", 4L);
        customer.updateCustomerDocument("C1", 4L, "Driver licence", "licence.pdf");
        assertEquals(CustomerDetails.empty(), customer.loadCustomerDetails("C1"));
        assertEquals(saveResult, customer.saveCustomer(CustomerDetails.empty(), CustomerDetails.empty()));

        verify(documents).listDocuments("C1", 1L, 100L);
        verify(documents).addDocument("C1", "id.pdf", "pdf", 10L, "Passport");
        verify(documents).deleteDocument("C1", 4L);
        verify(documents).updateDocument("C1", 4L, "Driver licence", "licence.pdf");
    }

    @Test
    void customerFacadeDelegatesLoanAndValidationOperations() throws Exception {
        LoanSearchService search = mock(LoanSearchService.class);
        LoanStatusService statuses = mock(LoanStatusService.class);
        LoanDetailsService details = mock(LoanDetailsService.class);
        CustomerDetailsService customerDetails = mock(CustomerDetailsService.class);
        replace(customer, "loanSearchService", search);
        replace(customer, "loanStatusService", statuses);
        replace(customer, "loanDetailsService", details);
        replace(customer, "detailsService", customerDetails);

        List<CustomerSummary> customers = List.of(new CustomerSummary("C1", "Jane", "Doe"));
        List<LoanSummary> loans = List.of(
                new LoanSummary("L1", "C1", "Jane Doe", 100.0, "2026-01-01", "Pending"));
        List<CustomerSummary> failed = new ArrayList<>();
        LoanListResult result = new LoanListResult(loans, 0, null);
        when(search.listLoansFor(customers, null)).thenReturn(result);
        when(search.listLoansFor(customers, null, failed)).thenReturn(result);
        when(search.loansOf(customers.get(0))).thenReturn(loans);
        when(search.filterByLoanNumber(loans, "L1")).thenReturn(loans);
        when(statuses.allowedStatuses("New")).thenReturn(List.of("Pending"));

        assertEquals(result, customer.listLoansFor(customers, null));
        assertEquals(result, customer.listLoansFor(customers, null, failed));
        assertEquals(loans, customer.loansOf(customers.get(0)));
        assertEquals(List.of("Pending"), customer.allowedLoanStatuses("New"));
        customer.changeLoanStatus("L1", "Pending");
        assertEquals(loans, customer.filterLoansByNumber(loans, "L1"));
        assertEquals(List.of("Pending"), customer.loanStatusesIn(loans));
        assertEquals(1, customer.summariseLoans(loans).getTotal());
        assertTrue(customer.isAwaitingVerification(" pending "));
        assertSame(details, customer.loanDetails());

        customer.validatePersonalDetails(CustomerDetails.empty().personal());
        customer.validateAddresses(List.of());
        customer.validatePhones(List.of());
        customer.validateEmails(List.of());
        customer.validateEmployers(List.of());

        verify(search).listLoansFor(customers, null);
        verify(search).listLoansFor(customers, null, failed);
        verify(search).loansOf(customers.get(0));
        verify(statuses).allowedStatuses("New");
        verify(statuses).changeStatus("L1", "Pending");
    }

    @Test
    void listCustomersSendsSearchAndReturnsItems() throws Exception {
        when(adapter.send(org.mockito.ArgumentMatchers.any(Search.class)))
                .thenReturn(new Status(2));

        Search.Item[] result = customer.listCustomers(1L, " Jane ", 25L);

        ArgumentCaptor<Search> captor = ArgumentCaptor.forClass(Search.class);
        verify(adapter).send(captor.capture());
        assertEquals(1L, captor.getValue().getPage());
        assertEquals(0, result.length);
    }

    @Test
    void listCustomersAllowsBlankSearchToReturnAllCustomers() throws Exception {
        when(adapter.send(org.mockito.ArgumentMatchers.any(Search.class)))
                .thenReturn(new Status(1));

        assertEquals(0, customer.listCustomers(1L, "", 25L).length);
        verify(adapter).send(org.mockito.ArgumentMatchers.any(Search.class));
    }

    @Test
    void listCustomersRejectsMissingRequiredParameters() {
        ValidationException exception = assertThrows(ValidationException.class, () ->
                customer.listCustomers(null, null, null));

        assertEquals("Page cannot be empty", exception.getResult().getError("page"));
        assertEquals("Search cannot be null", exception.getResult().getError("search"));
        assertEquals("Size cannot be empty", exception.getResult().getError("size"));
        verifyNoInteractions(adapter);
    }

    @Test
    void listCustomersRejectsNonPositivePageAndSize() {
        ValidationException exception = assertThrows(ValidationException.class, () ->
                customer.listCustomers(0L, "Jane", 0L));

        assertEquals("Page must be a positive integer", exception.getResult().getError("page"));
        assertEquals("Size must be a positive integer", exception.getResult().getError("size"));
        verifyNoInteractions(adapter);
    }

    @Test
    void listCustomersPropagatesNetworkFailure() throws IOException {
        when(adapter.send(org.mockito.ArgumentMatchers.any(Search.class)))
                .thenThrow(new IOException("connection unavailable"));

        IOException exception = assertThrows(IOException.class, () ->
                customer.listCustomers(1L, "", 25L));

        assertEquals("connection unavailable", exception.getMessage());
    }

    @Test
    void listCustomersThrowsLavsExceptionWhenMainframeRejectsRequest() throws IOException {
        when(adapter.send(org.mockito.ArgumentMatchers.any(Search.class)))
                .thenReturn(new Status(500, "Search rejected", 3));

        LavsException exception = assertThrows(LavsException.class, () ->
                customer.listCustomers(1L, "", 25L));

        assertEquals("Search rejected", exception.getMessage());
    }

    @Test
    void validateCustomerDetailsRejectsMissingOrganisation() {
        ValidationException exception = assertThrows(ValidationException.class, () ->
                customer.validateCustomerDetails(
                        "1990-01-01", "Smith", "Jane",
                        "NZ", null, "Ms"));

        assertEquals("Organisation cannot be empty",
                exception.getResult().getError("organisation"));
    }

    @Test
    void validateCustomerDetailsRejectsNonPositiveOrganisation() {
        ValidationException exception = assertThrows(ValidationException.class, () ->
                customer.validateCustomerDetails(
                        "1990-01-01", "Smith", "Jane",
                        "NZ", 0L, "Ms"));

        assertEquals("Organisation must be a positive integer",
                exception.getResult().getError("organisation"));
    }

    @Test
    void validateCustomerDetailsAcceptsPositiveOrganisation() {
        assertDoesNotThrow(() -> customer.validateCustomerDetails(
                "1990-01-01", "Smith", "Jane",
                "NZ", 7L, "Ms"));
    }

    @Test
    void addCustomerThrowsLavsExceptionWhenMainframeRejectsRequest() throws IOException {
        Status failure = new Status(500, "Customer rejected", 3);
        when(adapter.send(org.mockito.ArgumentMatchers.any(Create.class))).thenReturn(failure);

        LavsException exception = assertThrows(LavsException.class, () ->
                customer.addCustomer(
                        "1990-01-01", "Smith", "Jane", "NZ", 7L, "Ms"));

        assertEquals("Customer rejected", exception.getMessage());
    }
}
