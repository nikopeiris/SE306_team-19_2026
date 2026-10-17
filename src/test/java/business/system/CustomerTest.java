package business.system;

import business.lavsexceptions.LavsException;
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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
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

    @Test
    void listPhoneNumbersSendsListMessageAndReturnsItems() throws Exception {
        when(adapter.send(org.mockito.ArgumentMatchers.any(
                mainframe.client.messages.customerPhoneNumber.List.class)))
                .thenReturn(new Status(1));

        mainframe.client.messages.customerPhoneNumber.List.Item[] result =
                customer.listPhoneNumbers(" C123 ", 1L, 25L);

        verify(adapter).send(org.mockito.ArgumentMatchers.any(
                mainframe.client.messages.customerPhoneNumber.List.class));
        assertEquals(0, result.length);
    }

    @Test
    void retrievePhoneNumberSendsRetrieveMessageAndReturnsIt() throws Exception {
        when(adapter.send(org.mockito.ArgumentMatchers.any(
                mainframe.client.messages.customerPhoneNumber.Retrieve.class)))
                .thenReturn(new Status(1));

        mainframe.client.messages.customerPhoneNumber.Retrieve result =
                customer.retrievePhoneNumber("C123", 17L);

        assertEquals(17L, result.getId());
        verify(adapter).send(org.mockito.ArgumentMatchers.same(result));
    }

    @Test
    void phoneMethodsRejectInvalidParametersWithValidationException() {
        ValidationException phoneException = assertThrows(ValidationException.class,
                () -> customer.retrievePhoneNumber(null, 0L));
        ValidationException missingIdException = assertThrows(ValidationException.class,
                () -> customer.retrievePhoneNumber("C123", null));
        ValidationException phoneListException = assertThrows(ValidationException.class,
                () -> customer.listPhoneNumbers("C123", 0L, 0L));

        assertEquals("Customer number cannot be null or empty",
                phoneException.getResult().getError("customerNumber"));
        assertEquals("Phone number id must be a positive integer",
                phoneException.getResult().getError("phoneNumberId"));
        assertEquals("Phone number id cannot be empty",
                missingIdException.getResult().getError("phoneNumberId"));
        assertEquals("Page must be a positive integer",
                phoneListException.getResult().getError("page"));
        assertEquals("Size must be a positive integer",
                phoneListException.getResult().getError("size"));
        verifyNoInteractions(adapter);
    }

    @Test
    void phoneListRejectsMissingCustomerNumberPageAndSize() {
        ValidationException customerException = assertThrows(ValidationException.class,
                () -> customer.listPhoneNumbers(" ", 1L, 25L));
        ValidationException pagingException = assertThrows(ValidationException.class,
                () -> customer.listPhoneNumbers("C123", null, null));

        assertEquals("Customer number cannot be null or empty",
                customerException.getResult().getError("customerNumber"));
        assertEquals("Page cannot be empty", pagingException.getResult().getError("page"));
        assertEquals("Size cannot be empty", pagingException.getResult().getError("size"));
        verifyNoInteractions(adapter);
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
