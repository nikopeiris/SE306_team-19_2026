package business.system;

import business.lavsexceptions.LavsException;
import business.system.dto.CustomerSummary;
import business.validation.ValidationException;
import dataaccess.systemadapters.MainframeAdapter;
import mainframe.client.Message;
import mainframe.client.Status;
import mainframe.client.messages.customer.Create;
import mainframe.client.messages.customer.Retrieve;
import mainframe.client.messages.customer.Search;
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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

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
        verify(adapter, times(1)).send(org.mockito.ArgumentMatchers.any(Search.class));
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
        verify(adapter, times(2)).send(org.mockito.ArgumentMatchers.any(Search.class));
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
        verify(adapter, times(37)).send(org.mockito.ArgumentMatchers.any(Search.class));
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
