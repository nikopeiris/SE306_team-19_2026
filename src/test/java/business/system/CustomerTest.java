package business.system;

import business.lavsexceptions.LavsException;
import business.validation.ValidationException;
import dataaccess.systemadapters.MainframeAdapter;
import mainframe.client.Message;
import mainframe.client.Status;
import mainframe.client.messages.customer.AddAddress;
import mainframe.client.messages.customer.Create;
import mainframe.client.messages.customer.Retrieve;
import mainframe.client.messages.customer.Search;
import mainframe.client.messages.customerAddress.List;
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
import static org.junit.jupiter.api.Assertions.assertTrue;
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
    void addAddressSendsAddressDetailsAndReturnsMessage() throws Exception {
        when(adapter.send(org.mockito.ArgumentMatchers.any(AddAddress.class)))
                .thenReturn(new Status(2));

        AddAddress result = customer.addAddress(
                " Auckland ", " New Zealand ", " C123 ",
                true, false, " 1 Main Street ", " Flat 2 ",
                null, " 1010 ", " Ponsonby ", AddAddress.TYPE_POSTAL);

        ArgumentCaptor<AddAddress> captor = ArgumentCaptor.forClass(AddAddress.class);
        verify(adapter).send(captor.capture());
        assertEquals(result, captor.getValue());
        assertEquals("Auckland", result.getCity());
        assertEquals("New Zealand", result.getCountry());
        assertEquals("C123", result.getCustomer());
        assertEquals("1 Main Street", result.getLine1());
        assertEquals("Flat 2", result.getLine2());
        assertEquals("1010", result.getPostCode());
        assertEquals("Ponsonby", result.getSuburb());
        assertEquals(AddAddress.TYPE_POSTAL, result.getType());
        assertTrue(result.getIsMailing());
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
    void listAddressesSendsListMessageAndReturnsItems() throws Exception {
        when(adapter.send(org.mockito.ArgumentMatchers.any(List.class)))
                .thenReturn(new Status(1));

        List.Item[] result = customer.listAddresses(" C123 ", 1L, 25L);

        ArgumentCaptor<List> captor = ArgumentCaptor.forClass(List.class);
        verify(adapter).send(captor.capture());
        assertEquals(1L, captor.getValue().getPage());
        assertEquals(0, result.length);
    }

    @Test
    void listAddressesRejectsMissingCustomerNumber() {
        ValidationException nullException = assertThrows(ValidationException.class,
                () -> customer.listAddresses(null, 1L, 25L));
        ValidationException blankException = assertThrows(ValidationException.class,
                () -> customer.listAddresses(" ", 1L, 25L));

        assertEquals("Customer number cannot be null or empty",
                nullException.getResult().getError("customerNumber"));
        assertEquals("Customer number cannot be null or empty",
                blankException.getResult().getError("customerNumber"));
        verifyNoInteractions(adapter);
    }

    @Test
    void listAddressesRejectsInvalidPageAndSize() {
        ValidationException exception = assertThrows(ValidationException.class,
                () -> customer.listAddresses("C123", 0L, 0L));

        assertEquals("Page must be a positive integer",
                exception.getResult().getError("page"));
        assertEquals("Size must be a positive integer",
                exception.getResult().getError("size"));
        verifyNoInteractions(adapter);
    }

    @Test
    void listAddressesPropagatesNetworkFailure() throws IOException {
        when(adapter.send(org.mockito.ArgumentMatchers.any(List.class)))
                .thenThrow(new IOException("connection unavailable"));

        IOException exception = assertThrows(IOException.class,
                () -> customer.listAddresses("C123", 1L, 25L));

        assertEquals("connection unavailable", exception.getMessage());
    }

    @Test
    void listAddressesThrowsLavsExceptionWhenMainframeRejectsRequest()
            throws IOException {
        when(adapter.send(org.mockito.ArgumentMatchers.any(List.class)))
                .thenReturn(new Status(500, "Address list rejected", 1));

        LavsException exception = assertThrows(LavsException.class,
                () -> customer.listAddresses("C123", 1L, 25L));

        assertEquals("Address list rejected", exception.getMessage());
    }

    @Test
    void retrieveAddressSendsRetrieveMessageAndReturnsIt() throws Exception {
        when(adapter.send(org.mockito.ArgumentMatchers.any(
                mainframe.client.messages.customerAddress.Retrieve.class)))
                .thenReturn(new Status(1));

        mainframe.client.messages.customerAddress.Retrieve result =
                customer.retrieveAddress(" C123 ", 17L);

        assertEquals(17L, result.getId());
        verify(adapter).send(org.mockito.ArgumentMatchers.same(result));
    }

    @Test
    void retrieveAddressRejectsMissingOrInvalidIdentifiers() {
        ValidationException missingCustomerException = assertThrows(ValidationException.class,
                () -> customer.retrieveAddress(null, 17L));
        ValidationException blankCustomerException = assertThrows(ValidationException.class,
                () -> customer.retrieveAddress(" ", 17L));
        ValidationException missingIdException = assertThrows(ValidationException.class,
                () -> customer.retrieveAddress("C123", null));
        ValidationException invalidIdException = assertThrows(ValidationException.class,
                () -> customer.retrieveAddress("C123", 0L));

        assertEquals("Customer number cannot be null or empty",
                missingCustomerException.getResult().getError("customerNumber"));
        assertEquals("Customer number cannot be null or empty",
                blankCustomerException.getResult().getError("customerNumber"));
        assertEquals("Address id cannot be empty",
                missingIdException.getResult().getError("addressId"));
        assertEquals("Address id must be a positive integer",
                invalidIdException.getResult().getError("addressId"));
        verifyNoInteractions(adapter);
    }

    @Test
    void retrieveAddressPropagatesNetworkFailure() throws IOException {
        when(adapter.send(org.mockito.ArgumentMatchers.any(
                mainframe.client.messages.customerAddress.Retrieve.class)))
                .thenThrow(new IOException("connection unavailable"));

        IOException exception = assertThrows(IOException.class,
                () -> customer.retrieveAddress("C123", 17L));

        assertEquals("connection unavailable", exception.getMessage());
    }

    @Test
    void retrieveAddressThrowsLavsExceptionWhenMainframeRejectsRequest()
            throws IOException {
        when(adapter.send(org.mockito.ArgumentMatchers.any(
                mainframe.client.messages.customerAddress.Retrieve.class)))
                .thenReturn(new Status(404, "Address not found", 1));

        LavsException exception = assertThrows(LavsException.class,
                () -> customer.retrieveAddress("C123", 17L));

        assertEquals("Address not found", exception.getMessage());
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
    void addAddressRequiresOtherTypeWhenTypeIsOther() {
        assertThrows(ValidationException.class, () -> customer.addAddress(
                "Auckland", "New Zealand", "C123",
                false, false, "1 Main Street", null,
                null, "1010", "Ponsonby", AddAddress.TYPE_OTHER));

        verifyNoInteractions(adapter);
    }

    @Test
    void addAddressThrowsLavsExceptionWhenMainframeRejectsRequest() throws IOException {
        Status failure = new Status(400, "Address rejected", 4);
        when(adapter.send(org.mockito.ArgumentMatchers.any(AddAddress.class)))
                .thenReturn(failure);

        LavsException exception = assertThrows(LavsException.class, () ->
                customer.addAddress(
                        "Auckland", "New Zealand", "C123",
                        false, false, "1 Main Street", null,
                        null, "1010", "Ponsonby", AddAddress.TYPE_POSTAL));

        assertEquals("Address rejected", exception.getMessage());
    }

    @Test
    void validateAddressDetailsAcceptsOtherTypeWithValue() {
        assertDoesNotThrow(() -> customer.validateAddressDetails(
                "Auckland", "New Zealand", "C123",
                "1 Main Street", "Apartment", "1010",
                "Ponsonby", AddAddress.TYPE_OTHER));
    }

    @Test
    void validateAddressDetailsAcceptsNonOtherTypeWithoutOtherType() {
        assertDoesNotThrow(() -> customer.validateAddressDetails(
                "Auckland", "New Zealand", "C123",
                "1 Main Street", null, "1010",
                "Ponsonby", AddAddress.TYPE_POSTAL));
    }

    @Test
    void validateAddressDetailsRejectsOtherTypeWhenOtherTypeIsBlank() {
        ValidationException exception = assertThrows(ValidationException.class, () ->
                customer.validateAddressDetails(
                        "Auckland", "New Zealand", "C123",
                        "1 Main Street", " ", "1010",
                        "Ponsonby", AddAddress.TYPE_OTHER));

        assertEquals("Other address type cannot be empty",
                exception.getResult().getError("otherType"));
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
    void addAddressRejectsBlankRequiredFields() {
        ValidationException exception = assertThrows(ValidationException.class, () ->
                customer.addAddress(
                        " ", "New Zealand", "C123",
                        false, false, "1 Main Street", null,
                        null, "1010", "Ponsonby", AddAddress.TYPE_POSTAL));

        assertTrue(exception.getResult().getErrors().containsKey("city"));
        verifyNoInteractions(adapter);
    }

    @Test
    void addAddressRejectsUnsupportedType() {
        ValidationException exception = assertThrows(ValidationException.class, () ->
                customer.addAddress(
                        "Auckland", "New Zealand", "C123",
                        false, false, "1 Main Street", null,
                        null, "1010", "Ponsonby", "Unknown"));

        assertEquals("Address type is invalid", exception.getResult().getError("type"));
        verifyNoInteractions(adapter);
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

    @Test
    void addAddressPropagatesConnectionFailure() throws IOException {
        when(adapter.send(org.mockito.ArgumentMatchers.any(AddAddress.class)))
                .thenThrow(new IOException("connection unavailable"));

        IOException exception = assertThrows(IOException.class, () -> customer.addAddress(
                "Auckland", "New Zealand", "C123",
                false, false, "1 Main Street", null,
                null, "1010", "Ponsonby", AddAddress.TYPE_POSTAL));

        assertEquals("connection unavailable", exception.getMessage());
    }
}
