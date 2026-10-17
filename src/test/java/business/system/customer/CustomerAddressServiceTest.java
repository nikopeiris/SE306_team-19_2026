package business.system.customer;

import business.lavsexceptions.LavsException;
import business.validation.ValidationException;
import dataaccess.systemadapters.MainframeAdapter;
import mainframe.client.Status;
import mainframe.client.messages.customer.AddAddress;
import mainframe.client.messages.customer.DeleteAddress;
import mainframe.client.messages.customer.UpdateAddress;
import mainframe.client.messages.customerAddress.List;
import mainframe.client.messages.customerAddress.Retrieve;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.IOException;
import java.lang.reflect.Field;
import java.util.ArrayList;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.same;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CustomerAddressServiceTest {
    @Mock
    private MainframeAdapter adapter;

    private CustomerAddressService addressService;

    @BeforeEach
    void setUp() {
        addressService = new CustomerAddressService(adapter);
    }

    // makes the stubbed list request return addresses with the given isMailing flags
    private void stubAddressList(String... isMailingFlags) throws Exception {
        when(adapter.send(any(List.class))).thenAnswer(invocation -> {
            List listMessage = invocation.getArgument(0);
            Field dataField = List.class.getDeclaredField("_data");
            dataField.setAccessible(true);
            @SuppressWarnings("unchecked")
            ArrayList<List.Item> items = (ArrayList<List.Item>) dataField.get(listMessage);
            long id = 1L;
            for (String isMailing : isMailingFlags) {
                items.add(listMessage.new Item("Auckland", "NZ", id++, isMailing, "n", "Home"));
            }
            return new Status(0, null, 1L);
        });
    }

    @Test
    void constructorRejectsNullAdapter() {
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                () -> new CustomerAddressService(null));

        assertEquals("Address adapter cannot be null", exception.getMessage());
    }

    @Test
    void addAddressSendsAddressDetailsAndReturnsMessage() throws Exception {
        when(adapter.send(any(AddAddress.class))).thenReturn(new Status(2));

        AddAddress result = addressService.addAddress(
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
    void addAddressRequiresOtherTypeWhenTypeIsOther() {
        assertThrows(ValidationException.class, () -> addressService.addAddress(
                "Auckland", "New Zealand", "C123",
                false, false, "1 Main Street", null,
                null, "1010", "Ponsonby", AddAddress.TYPE_OTHER));

        verifyNoInteractions(adapter);
    }

    @Test
    void addAddressRejectsBlankRequiredFields() {
        ValidationException exception = assertThrows(ValidationException.class, () ->
                addressService.addAddress(
                        " ", "New Zealand", "C123",
                        false, false, "1 Main Street", null,
                        null, "1010", "Ponsonby", AddAddress.TYPE_POSTAL));

        assertTrue(exception.getResult().getErrors().containsKey("city"));
        verifyNoInteractions(adapter);
    }

    @Test
    void addAddressRejectsUnsupportedType() {
        ValidationException exception = assertThrows(ValidationException.class, () ->
                addressService.addAddress(
                        "Auckland", "New Zealand", "C123",
                        false, false, "1 Main Street", null,
                        null, "1010", "Ponsonby", "Unknown"));

        assertEquals("Address type is invalid", exception.getResult().getError("type"));
        verifyNoInteractions(adapter);
    }

    @Test
    void addAddressThrowsLavsExceptionWhenMainframeRejectsRequest() throws IOException {
        when(adapter.send(any(AddAddress.class)))
                .thenReturn(new Status(400, "Address rejected", 4));

        LavsException exception = assertThrows(LavsException.class, () ->
                addressService.addAddress(
                        "Auckland", "New Zealand", "C123",
                        false, false, "1 Main Street", null,
                        null, "1010", "Ponsonby", AddAddress.TYPE_POSTAL));

        assertEquals("Address rejected", exception.getMessage());
    }

    @Test
    void addAddressPropagatesConnectionFailure() throws IOException {
        when(adapter.send(any(AddAddress.class)))
                .thenThrow(new IOException("connection unavailable"));

        IOException exception = assertThrows(IOException.class, () -> addressService.addAddress(
                "Auckland", "New Zealand", "C123",
                false, false, "1 Main Street", null,
                null, "1010", "Ponsonby", AddAddress.TYPE_POSTAL));

        assertEquals("connection unavailable", exception.getMessage());
    }

    @Test
    void updateAddressSendsTrimmedValuesAndReturnsMessage() throws Exception {
        when(adapter.send(any(UpdateAddress.class))).thenReturn(new Status(3));

        UpdateAddress result = addressService.updateAddress(
                " Wellington ", " New Zealand ", " C123 ", 17L,
                false, true, " 5 Queen Street ", " Unit 9 ",
                " Holiday home ", " 6011 ", " Te Aro ", UpdateAddress.TYPE_OTHER);

        ArgumentCaptor<UpdateAddress> captor = ArgumentCaptor.forClass(UpdateAddress.class);
        verify(adapter).send(captor.capture());
        assertEquals(result, captor.getValue());
        assertEquals("Wellington", result.getCity());
        assertEquals("New Zealand", result.getCountry());
        assertEquals("C123", result.getCustomer());
        assertEquals(17L, result.getId());
        assertEquals("5 Queen Street", result.getLine1());
        assertEquals("Unit 9", result.getLine2());
        assertEquals("Holiday home", result.getOtherType());
        assertEquals("6011", result.getPostCode());
        assertEquals("Te Aro", result.getSuburb());
        assertEquals(UpdateAddress.TYPE_OTHER, result.getType());
        assertTrue(result.getIsPrimary());
        assertFalse(result.getIsMailing());
    }

    @Test
    void updateAddressAllowsMissingOptionalFields() throws Exception {
        when(adapter.send(any(UpdateAddress.class))).thenReturn(new Status(3));

        UpdateAddress result = addressService.updateAddress(
                "Auckland", "New Zealand", "C123", 17L,
                true, false, "1 Main Street", null,
                null, "1010", "Ponsonby", UpdateAddress.TYPE_RESIDENTIAL);

        assertNull(result.getLine2());
        assertNull(result.getOtherType());
    }

    @Test
    void updateAddressRejectsMissingOrInvalidId() {
        ValidationException missingIdException = assertThrows(ValidationException.class,
                () -> addressService.updateAddress("Auckland", "New Zealand", "C123", null,
                        true, false, "1 Main Street", null, null, "1010", "Ponsonby",
                        UpdateAddress.TYPE_POSTAL));
        ValidationException invalidIdException = assertThrows(ValidationException.class,
                () -> addressService.updateAddress("Auckland", "New Zealand", "C123", 0L,
                        true, false, "1 Main Street", null, null, "1010", "Ponsonby",
                        UpdateAddress.TYPE_POSTAL));

        assertEquals("Address id cannot be empty",
                missingIdException.getResult().getError("addressId"));
        assertEquals("Address id must be a positive integer",
                invalidIdException.getResult().getError("addressId"));
        verifyNoInteractions(adapter);
    }

    @Test
    void updateAddressReportsAllInvalidFieldsTogether() {
        ValidationException exception = assertThrows(ValidationException.class,
                () -> addressService.updateAddress(" ", null, "", -1L,
                        true, false, null, null, null, " ", null, "Holiday"));

        assertEquals("City cannot be empty", exception.getResult().getError("city"));
        assertEquals("Country cannot be empty", exception.getResult().getError("country"));
        assertEquals("Customer cannot be empty", exception.getResult().getError("customer"));
        assertEquals("Address line 1 cannot be empty", exception.getResult().getError("line1"));
        assertEquals("Post code cannot be empty", exception.getResult().getError("postCode"));
        assertEquals("Suburb cannot be empty", exception.getResult().getError("suburb"));
        assertEquals("Address type is invalid", exception.getResult().getError("type"));
        assertEquals("Address id must be a positive integer",
                exception.getResult().getError("addressId"));
        verifyNoInteractions(adapter);
    }

    @Test
    void updateAddressRequiresOtherTypeWhenTypeIsOther() {
        ValidationException exception = assertThrows(ValidationException.class,
                () -> addressService.updateAddress("Auckland", "New Zealand", "C123", 17L,
                        true, false, "1 Main Street", null, " ", "1010", "Ponsonby",
                        UpdateAddress.TYPE_OTHER));

        assertEquals("Other address type cannot be empty",
                exception.getResult().getError("otherType"));
        verifyNoInteractions(adapter);
    }

    @Test
    void updateAddressThrowsLavsExceptionWhenMainframeRejectsRequest() throws IOException {
        when(adapter.send(any(UpdateAddress.class)))
                .thenReturn(new Status(404, "Address not found", 3));

        LavsException exception = assertThrows(LavsException.class,
                () -> addressService.updateAddress("Auckland", "New Zealand", "C123", 17L,
                        true, false, "1 Main Street", null, null, "1010", "Ponsonby",
                        UpdateAddress.TYPE_POSTAL));

        assertEquals("Address not found", exception.getMessage());
        assertEquals(404, exception.getErrorCode());
    }

    @Test
    void deleteAddressSendsTrimmedValuesAndReturnsMessage() throws Exception {
        when(adapter.send(any(DeleteAddress.class))).thenReturn(new Status(4));

        DeleteAddress result = addressService.deleteAddress(" C123 ", 17L);

        ArgumentCaptor<DeleteAddress> captor = ArgumentCaptor.forClass(DeleteAddress.class);
        verify(adapter).send(captor.capture());
        assertEquals(result, captor.getValue());
        assertEquals("C123", result.getCustomer());
        assertEquals(17L, result.getId());
    }

    @Test
    void deleteAddressRejectsMissingOrInvalidIdentifiers() {
        ValidationException missingCustomerException = assertThrows(ValidationException.class,
                () -> addressService.deleteAddress(null, 17L));
        ValidationException blankCustomerException = assertThrows(ValidationException.class,
                () -> addressService.deleteAddress(" ", 17L));
        ValidationException missingIdException = assertThrows(ValidationException.class,
                () -> addressService.deleteAddress("C123", null));
        ValidationException invalidIdException = assertThrows(ValidationException.class,
                () -> addressService.deleteAddress("C123", 0L));

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
    void deleteAddressThrowsLavsExceptionWhenMainframeRejectsRequest() throws IOException {
        when(adapter.send(any(DeleteAddress.class)))
                .thenReturn(new Status(404, "Address not found", 4));

        LavsException exception = assertThrows(LavsException.class,
                () -> addressService.deleteAddress("C123", 17L));

        assertEquals("Address not found", exception.getMessage());
        assertEquals(404, exception.getErrorCode());
    }

    @Test
    void listAddressesSendsListMessageAndReturnsItems() throws Exception {
        when(adapter.send(any(List.class))).thenReturn(new Status(1));

        List.Item[] result = addressService.listAddresses(" C123 ", 1L, 25L);

        ArgumentCaptor<List> captor = ArgumentCaptor.forClass(List.class);
        verify(adapter).send(captor.capture());
        assertEquals(1L, captor.getValue().getPage());
        assertEquals(0, result.length);
    }

    @Test
    void listAddressesRejectsMissingCustomerNumber() {
        ValidationException nullException = assertThrows(ValidationException.class,
                () -> addressService.listAddresses(null, 1L, 25L));
        ValidationException blankException = assertThrows(ValidationException.class,
                () -> addressService.listAddresses(" ", 1L, 25L));

        assertEquals("Customer number cannot be null or empty",
                nullException.getResult().getError("customerNumber"));
        assertEquals("Customer number cannot be null or empty",
                blankException.getResult().getError("customerNumber"));
        verifyNoInteractions(adapter);
    }

    @Test
    void listAddressesRejectsInvalidPageAndSize() {
        ValidationException invalidException = assertThrows(ValidationException.class,
                () -> addressService.listAddresses("C123", 0L, 0L));
        ValidationException missingException = assertThrows(ValidationException.class,
                () -> addressService.listAddresses("C123", null, null));

        assertEquals("Page must be a positive integer",
                invalidException.getResult().getError("page"));
        assertEquals("Size must be a positive integer",
                invalidException.getResult().getError("size"));
        assertEquals("Page cannot be empty", missingException.getResult().getError("page"));
        assertEquals("Size cannot be empty", missingException.getResult().getError("size"));
        verifyNoInteractions(adapter);
    }

    @Test
    void listAddressesPropagatesNetworkFailure() throws IOException {
        when(adapter.send(any(List.class)))
                .thenThrow(new IOException("connection unavailable"));

        IOException exception = assertThrows(IOException.class,
                () -> addressService.listAddresses("C123", 1L, 25L));

        assertEquals("connection unavailable", exception.getMessage());
    }

    @Test
    void listAddressesThrowsLavsExceptionWhenMainframeRejectsRequest() throws IOException {
        when(adapter.send(any(List.class)))
                .thenReturn(new Status(500, "Address list rejected", 1));

        LavsException exception = assertThrows(LavsException.class,
                () -> addressService.listAddresses("C123", 1L, 25L));

        assertEquals("Address list rejected", exception.getMessage());
    }

    @Test
    void retrieveAddressSendsRetrieveMessageAndReturnsIt() throws Exception {
        when(adapter.send(any(Retrieve.class))).thenReturn(new Status(1));

        Retrieve result = addressService.retrieveAddress(" C123 ", 17L);

        assertEquals(17L, result.getId());
        verify(adapter).send(same(result));
    }

    @Test
    void retrieveAddressRejectsMissingOrInvalidIdentifiers() {
        ValidationException missingCustomerException = assertThrows(ValidationException.class,
                () -> addressService.retrieveAddress(null, 17L));
        ValidationException blankCustomerException = assertThrows(ValidationException.class,
                () -> addressService.retrieveAddress(" ", 17L));
        ValidationException missingIdException = assertThrows(ValidationException.class,
                () -> addressService.retrieveAddress("C123", null));
        ValidationException invalidIdException = assertThrows(ValidationException.class,
                () -> addressService.retrieveAddress("C123", 0L));

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
        when(adapter.send(any(Retrieve.class)))
                .thenThrow(new IOException("connection unavailable"));

        IOException exception = assertThrows(IOException.class,
                () -> addressService.retrieveAddress("C123", 17L));

        assertEquals("connection unavailable", exception.getMessage());
    }

    @Test
    void retrieveAddressThrowsLavsExceptionWhenMainframeRejectsRequest() throws IOException {
        when(adapter.send(any(Retrieve.class)))
                .thenReturn(new Status(404, "Address not found", 1));

        LavsException exception = assertThrows(LavsException.class,
                () -> addressService.retrieveAddress("C123", 17L));

        assertEquals("Address not found", exception.getMessage());
    }

    @Test
    void findMailingAddressReturnsTheFirstMailingAddress() throws Exception {
        stubAddressList("n", "y", "y");

        List.Item result = addressService.findMailingAddress("C123");

        assertEquals(2L, result.getId());
    }

    @Test
    void findMailingAddressReturnsNullWhenNoAddressIsMailing() throws Exception {
        stubAddressList("n", null);

        assertNull(addressService.findMailingAddress("C123"));
    }

    @Test
    void findMailingAddressReturnsNullWhenCustomerHasNoAddresses() throws Exception {
        when(adapter.send(any(List.class))).thenReturn(new Status(1));

        assertNull(addressService.findMailingAddress("C123"));
    }

    @Test
    void validateAddressDetailsAcceptsOtherTypeWithValue() {
        assertDoesNotThrow(() -> CustomerAddressService.validateAddressDetails(
                "Auckland", "New Zealand", "C123",
                "1 Main Street", "Apartment", "1010",
                "Ponsonby", AddAddress.TYPE_OTHER));
    }

    @Test
    void validateAddressDetailsAcceptsNonOtherTypeWithoutOtherType() {
        assertDoesNotThrow(() -> CustomerAddressService.validateAddressDetails(
                "Auckland", "New Zealand", "C123",
                "1 Main Street", null, "1010",
                "Ponsonby", AddAddress.TYPE_POSTAL));
    }

    @Test
    void validateAddressDetailsRejectsOtherTypeWhenOtherTypeIsBlank() {
        ValidationException exception = assertThrows(ValidationException.class, () ->
                CustomerAddressService.validateAddressDetails(
                        "Auckland", "New Zealand", "C123",
                        "1 Main Street", " ", "1010",
                        "Ponsonby", AddAddress.TYPE_OTHER));

        assertEquals("Other address type cannot be empty",
                exception.getResult().getError("otherType"));
    }
}
