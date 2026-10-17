package business.system.customer;

import business.lavsexceptions.LavsException;
import business.validation.ValidationException;
import dataaccess.systemadapters.MainframeAdapter;
import mainframe.client.Status;
import mainframe.client.messages.customer.AddPhoneNumber;
import mainframe.client.messages.customer.DeletePhoneNumber;
import mainframe.client.messages.customer.UpdatePhoneNumber;
import mainframe.client.messages.customerPhoneNumber.List;
import mainframe.client.messages.customerPhoneNumber.Retrieve;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.IOException;

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
class CustomerPhoneNumberServiceTest {
    @Mock
    private MainframeAdapter adapter;

    private CustomerPhoneNumberService phoneService;

    @BeforeEach
    void setUp() {
        phoneService = new CustomerPhoneNumberService(adapter);
    }

    @Test
    void constructorRejectsNullAdapter() {
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                () -> new CustomerPhoneNumberService(null));

        assertEquals("Phone number adapter cannot be null", exception.getMessage());
    }

    @Test
    void addPhoneNumberSendsTrimmedValuesAndReturnsMessage() throws Exception {
        when(adapter.send(any(AddPhoneNumber.class))).thenReturn(new Status(1));

        AddPhoneNumber result = phoneService.addPhoneNumber(
                true, " +64 ", " C123 ", true, " 021 555 0101 ", " Holiday ",
                AddPhoneNumber.TYPE_OTHER);

        ArgumentCaptor<AddPhoneNumber> captor = ArgumentCaptor.forClass(AddPhoneNumber.class);
        verify(adapter).send(captor.capture());
        assertEquals(result, captor.getValue());
        assertEquals("+64", result.getCountryCode());
        assertEquals("C123", result.getCustomer());
        assertEquals("021 555 0101", result.getNumber());
        assertEquals("Holiday", result.getOtherType());
        assertEquals(AddPhoneNumber.TYPE_OTHER, result.getType());
        assertTrue(result.getCanTxt());
        assertTrue(result.getIsPrimary());
    }

    @Test
    void addPhoneNumberAllowsMissingOtherTypeForStandardTypes() throws Exception {
        when(adapter.send(any(AddPhoneNumber.class))).thenReturn(new Status(1));

        AddPhoneNumber result = phoneService.addPhoneNumber(
                false, "+64", "C123", false, "09 555 0101", null, AddPhoneNumber.TYPE_HOME);

        assertNull(result.getOtherType());
        assertFalse(result.getCanTxt());
        assertFalse(result.getIsPrimary());
    }

    @Test
    void addPhoneNumberReportsAllInvalidFieldsTogether() {
        ValidationException exception = assertThrows(ValidationException.class,
                () -> phoneService.addPhoneNumber(false, " ", null, false, "", null, "Pager"));

        assertEquals("Country code cannot be empty", exception.getResult().getError("countryCode"));
        assertEquals("Customer cannot be empty", exception.getResult().getError("customer"));
        assertEquals("Phone number cannot be empty", exception.getResult().getError("number"));
        assertEquals("Phone type is invalid", exception.getResult().getError("type"));
        verifyNoInteractions(adapter);
    }

    @Test
    void addPhoneNumberThrowsLavsExceptionWhenMainframeRejectsRequest() throws IOException {
        when(adapter.send(any(AddPhoneNumber.class)))
                .thenReturn(new Status(400, "Phone number rejected", 1));

        LavsException exception = assertThrows(LavsException.class,
                () -> phoneService.addPhoneNumber(
                        true, "+64", "C123", true, "021 555 0101", null,
                        AddPhoneNumber.TYPE_MOBILE));

        assertEquals("Phone number rejected", exception.getMessage());
        assertEquals(400, exception.getErrorCode());
    }

    @Test
    void addPhoneNumberPropagatesConnectionFailure() throws IOException {
        when(adapter.send(any(AddPhoneNumber.class)))
                .thenThrow(new IOException("connection unavailable"));

        IOException exception = assertThrows(IOException.class,
                () -> phoneService.addPhoneNumber(
                        true, "+64", "C123", true, "021 555 0101", null,
                        AddPhoneNumber.TYPE_MOBILE));

        assertEquals("connection unavailable", exception.getMessage());
    }

    @Test
    void updatePhoneNumberSendsTrimmedValuesAndReturnsMessage() throws Exception {
        when(adapter.send(any(UpdatePhoneNumber.class))).thenReturn(new Status(2));

        UpdatePhoneNumber result = phoneService.updatePhoneNumber(
                false, " +61 ", " C123 ", 9L, false, " 0400 000 000 ", null,
                UpdatePhoneNumber.TYPE_WORK);

        ArgumentCaptor<UpdatePhoneNumber> captor = ArgumentCaptor.forClass(UpdatePhoneNumber.class);
        verify(adapter).send(captor.capture());
        assertEquals(result, captor.getValue());
        assertEquals("+61", result.getCountryCode());
        assertEquals("C123", result.getCustomer());
        assertEquals(9L, result.getId());
        assertEquals("0400 000 000", result.getNumber());
        assertNull(result.getOtherType());
        assertEquals(UpdatePhoneNumber.TYPE_WORK, result.getType());
        assertFalse(result.getCanTxt());
        assertFalse(result.getIsPrimary());
    }

    @Test
    void updatePhoneNumberRejectsMissingOrInvalidId() {
        ValidationException missingIdException = assertThrows(ValidationException.class,
                () -> phoneService.updatePhoneNumber(
                        true, "+64", "C123", null, true, "021", null, AddPhoneNumber.TYPE_MOBILE));
        ValidationException invalidIdException = assertThrows(ValidationException.class,
                () -> phoneService.updatePhoneNumber(
                        true, "+64", "C123", 0L, true, "021", null, AddPhoneNumber.TYPE_MOBILE));

        assertEquals("Phone number id cannot be empty",
                missingIdException.getResult().getError("phoneNumberId"));
        assertEquals("Phone number id must be a positive integer",
                invalidIdException.getResult().getError("phoneNumberId"));
        verifyNoInteractions(adapter);
    }

    @Test
    void updatePhoneNumberRequiresOtherTypeWhenTypeIsOther() {
        ValidationException exception = assertThrows(ValidationException.class,
                () -> phoneService.updatePhoneNumber(
                        true, "+64", "C123", 9L, true, "021", " ", AddPhoneNumber.TYPE_OTHER));

        assertEquals("Other phone type cannot be empty",
                exception.getResult().getError("otherType"));
        verifyNoInteractions(adapter);
    }

    @Test
    void updatePhoneNumberThrowsLavsExceptionWhenMainframeRejectsRequest() throws IOException {
        when(adapter.send(any(UpdatePhoneNumber.class)))
                .thenReturn(new Status(404, "Phone number not found", 2));

        LavsException exception = assertThrows(LavsException.class,
                () -> phoneService.updatePhoneNumber(
                        true, "+64", "C123", 9L, true, "021", null, AddPhoneNumber.TYPE_MOBILE));

        assertEquals("Phone number not found", exception.getMessage());
        assertEquals(404, exception.getErrorCode());
    }

    @Test
    void deletePhoneNumberSendsTrimmedValuesAndReturnsMessage() throws Exception {
        when(adapter.send(any(DeletePhoneNumber.class))).thenReturn(new Status(3));

        DeletePhoneNumber result = phoneService.deletePhoneNumber(" C123 ", 9L);

        ArgumentCaptor<DeletePhoneNumber> captor = ArgumentCaptor.forClass(DeletePhoneNumber.class);
        verify(adapter).send(captor.capture());
        assertEquals(result, captor.getValue());
        assertEquals("C123", result.getCustomer());
        assertEquals(9L, result.getId());
    }

    @Test
    void deletePhoneNumberRejectsMissingOrInvalidIdentifiers() {
        ValidationException missingCustomerException = assertThrows(ValidationException.class,
                () -> phoneService.deletePhoneNumber(null, 9L));
        ValidationException blankCustomerException = assertThrows(ValidationException.class,
                () -> phoneService.deletePhoneNumber(" ", 9L));
        ValidationException missingIdException = assertThrows(ValidationException.class,
                () -> phoneService.deletePhoneNumber("C123", null));
        ValidationException invalidIdException = assertThrows(ValidationException.class,
                () -> phoneService.deletePhoneNumber("C123", -1L));

        assertEquals("Customer number cannot be null or empty",
                missingCustomerException.getResult().getError("customerNumber"));
        assertEquals("Customer number cannot be null or empty",
                blankCustomerException.getResult().getError("customerNumber"));
        assertEquals("Phone number id cannot be empty",
                missingIdException.getResult().getError("phoneNumberId"));
        assertEquals("Phone number id must be a positive integer",
                invalidIdException.getResult().getError("phoneNumberId"));
        verifyNoInteractions(adapter);
    }

    @Test
    void deletePhoneNumberThrowsLavsExceptionWhenMainframeRejectsRequest() throws IOException {
        when(adapter.send(any(DeletePhoneNumber.class)))
                .thenReturn(new Status(404, "Phone number not found", 3));

        LavsException exception = assertThrows(LavsException.class,
                () -> phoneService.deletePhoneNumber("C123", 9L));

        assertEquals("Phone number not found", exception.getMessage());
    }

    @Test
    void listPhoneNumbersSendsListMessageAndReturnsItems() throws Exception {
        when(adapter.send(any(List.class))).thenReturn(new Status(1));

        List.Item[] result = phoneService.listPhoneNumbers(" C123 ", 1L, 25L);

        ArgumentCaptor<List> captor = ArgumentCaptor.forClass(List.class);
        verify(adapter).send(captor.capture());
        assertEquals(1L, captor.getValue().getPage());
        assertEquals(0, result.length);
    }

    @Test
    void listPhoneNumbersRejectsMissingCustomerNumberAndInvalidPaging() {
        ValidationException customerException = assertThrows(ValidationException.class,
                () -> phoneService.listPhoneNumbers(" ", 1L, 25L));
        ValidationException invalidException = assertThrows(ValidationException.class,
                () -> phoneService.listPhoneNumbers("C123", 0L, 0L));
        ValidationException missingException = assertThrows(ValidationException.class,
                () -> phoneService.listPhoneNumbers("C123", null, null));

        assertEquals("Customer number cannot be null or empty",
                customerException.getResult().getError("customerNumber"));
        assertEquals("Page must be a positive integer",
                invalidException.getResult().getError("page"));
        assertEquals("Size must be a positive integer",
                invalidException.getResult().getError("size"));
        assertEquals("Page cannot be empty", missingException.getResult().getError("page"));
        assertEquals("Size cannot be empty", missingException.getResult().getError("size"));
        verifyNoInteractions(adapter);
    }

    @Test
    void listPhoneNumbersThrowsLavsExceptionWhenMainframeRejectsRequest() throws IOException {
        when(adapter.send(any(List.class)))
                .thenReturn(new Status(500, "Phone list rejected", 1));

        LavsException exception = assertThrows(LavsException.class,
                () -> phoneService.listPhoneNumbers("C123", 1L, 25L));

        assertEquals("Phone list rejected", exception.getMessage());
    }

    @Test
    void retrievePhoneNumberSendsRetrieveMessageAndReturnsIt() throws Exception {
        when(adapter.send(any(Retrieve.class))).thenReturn(new Status(1));

        Retrieve result = phoneService.retrievePhoneNumber(" C123 ", 9L);

        assertEquals(9L, result.getId());
        verify(adapter).send(same(result));
    }

    @Test
    void retrievePhoneNumberRejectsMissingOrInvalidIdentifiers() {
        ValidationException customerException = assertThrows(ValidationException.class,
                () -> phoneService.retrievePhoneNumber(null, 9L));
        ValidationException idException = assertThrows(ValidationException.class,
                () -> phoneService.retrievePhoneNumber("C123", null));

        assertEquals("Customer number cannot be null or empty",
                customerException.getResult().getError("customerNumber"));
        assertEquals("Phone number id cannot be empty",
                idException.getResult().getError("phoneNumberId"));
        verifyNoInteractions(adapter);
    }

    @Test
    void retrievePhoneNumberPropagatesNetworkFailure() throws IOException {
        when(adapter.send(any(Retrieve.class)))
                .thenThrow(new IOException("connection unavailable"));

        IOException exception = assertThrows(IOException.class,
                () -> phoneService.retrievePhoneNumber("C123", 9L));

        assertEquals("connection unavailable", exception.getMessage());
    }

    @Test
    void validatePhoneNumberDetailsAcceptsEachStandardType() {
        for (String type : new String[] {AddPhoneNumber.TYPE_HOME, AddPhoneNumber.TYPE_MOBILE,
                AddPhoneNumber.TYPE_WORK}) {
            assertDoesNotThrow(() -> CustomerPhoneNumberService.validatePhoneNumberDetails(
                    "+64", "C123", "021 555 0101", null, type), type);
        }
        assertDoesNotThrow(() -> CustomerPhoneNumberService.validatePhoneNumberDetails(
                "+64", "C123", "021 555 0101", "Holiday", AddPhoneNumber.TYPE_OTHER));
    }

    @Test
    void validatePhoneNumberDetailsRequiresType() {
        ValidationException exception = assertThrows(ValidationException.class,
                () -> CustomerPhoneNumberService.validatePhoneNumberDetails(
                        "+64", "C123", "021 555 0101", null, " "));

        assertEquals("Phone type cannot be empty", exception.getResult().getError("type"));
    }
}
