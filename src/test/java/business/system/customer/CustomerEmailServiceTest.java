package business.system.customer;

import business.lavsexceptions.LavsException;
import business.validation.ValidationException;
import dataaccess.systemadapters.MainframeAdapter;
import mainframe.client.Status;
import mainframe.client.messages.customer.AddEmail;
import mainframe.client.messages.customer.DeleteEmail;
import mainframe.client.messages.customer.UpdateEmail;
import mainframe.client.messages.customerEmail.List;
import mainframe.client.messages.customerEmail.Retrieve;
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
class CustomerEmailServiceTest {
    @Mock
    private MainframeAdapter adapter;

    private CustomerEmailService emailService;

    @BeforeEach
    void setUp() {
        emailService = new CustomerEmailService(adapter);
    }

    // makes the stubbed list request return one email per {address, isPrimary} pair
    private void stubEmailList(String[]... emails) throws Exception {
        when(adapter.send(any(List.class))).thenAnswer(invocation -> {
            List listMessage = invocation.getArgument(0);
            Field dataField = List.class.getDeclaredField("_data");
            dataField.setAccessible(true);
            @SuppressWarnings("unchecked")
            ArrayList<List.Item> items = (ArrayList<List.Item>) dataField.get(listMessage);
            long id = 1L;
            for (String[] email : emails) {
                items.add(listMessage.new Item(email[0], id++, email[1], AddEmail.TYPE_PERSONAL));
            }
            return new Status(0, null, 1L);
        });
    }

    @Test
    void constructorRejectsNullAdapter() {
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                () -> new CustomerEmailService(null));

        assertEquals("Email adapter cannot be null", exception.getMessage());
    }

    @Test
    void addEmailSendsTrimmedValuesAndReturnsMessage() throws Exception {
        when(adapter.send(any(AddEmail.class))).thenReturn(new Status(1));

        AddEmail result = emailService.addEmail(
                " jane@example.com ", " C123 ", true, " Backup ", AddEmail.TYPE_OTHER);

        ArgumentCaptor<AddEmail> captor = ArgumentCaptor.forClass(AddEmail.class);
        verify(adapter).send(captor.capture());
        assertEquals(result, captor.getValue());
        assertEquals("jane@example.com", result.getAddress());
        assertEquals("C123", result.getCustomer());
        assertEquals("Backup", result.getOtherType());
        assertEquals(AddEmail.TYPE_OTHER, result.getType());
        assertTrue(result.getIsPrimary());
    }

    @Test
    void addEmailAllowsMissingOtherTypeForStandardTypes() throws Exception {
        when(adapter.send(any(AddEmail.class))).thenReturn(new Status(1));

        AddEmail result = emailService.addEmail(
                "jane@work.co.nz", "C123", false, null, AddEmail.TYPE_WORK);

        assertNull(result.getOtherType());
        assertFalse(result.getIsPrimary());
    }

    @Test
    void addEmailRejectsInvalidDetailsWithoutContactingMainframe() {
        ValidationException exception = assertThrows(ValidationException.class,
                () -> emailService.addEmail(" ", null, false, null, "Holiday"));

        assertEquals("Email address cannot be empty", exception.getResult().getError("address"));
        assertEquals("Customer cannot be empty", exception.getResult().getError("customer"));
        assertEquals("Email type is invalid", exception.getResult().getError("type"));
        verifyNoInteractions(adapter);
    }

    @Test
    void addEmailThrowsLavsExceptionWhenMainframeRejectsRequest() throws IOException {
        when(adapter.send(any(AddEmail.class)))
                .thenReturn(new Status(400, "Email rejected", 1));

        LavsException exception = assertThrows(LavsException.class,
                () -> emailService.addEmail(
                        "jane@example.com", "C123", true, null, AddEmail.TYPE_PERSONAL));

        assertEquals("Email rejected", exception.getMessage());
        assertEquals(400, exception.getErrorCode());
    }

    @Test
    void addEmailPropagatesConnectionFailure() throws IOException {
        when(adapter.send(any(AddEmail.class)))
                .thenThrow(new IOException("connection unavailable"));

        IOException exception = assertThrows(IOException.class,
                () -> emailService.addEmail(
                        "jane@example.com", "C123", true, null, AddEmail.TYPE_PERSONAL));

        assertEquals("connection unavailable", exception.getMessage());
    }

    @Test
    void updateEmailSendsTrimmedValuesAndReturnsMessage() throws Exception {
        when(adapter.send(any(UpdateEmail.class))).thenReturn(new Status(2));

        UpdateEmail result = emailService.updateEmail(
                " jane@example.com ", " C123 ", 7L, false, null, UpdateEmail.TYPE_WORK);

        ArgumentCaptor<UpdateEmail> captor = ArgumentCaptor.forClass(UpdateEmail.class);
        verify(adapter).send(captor.capture());
        assertEquals(result, captor.getValue());
        assertEquals("jane@example.com", result.getAddress());
        assertEquals("C123", result.getCustomer());
        assertEquals(7L, result.getId());
        assertNull(result.getOtherType());
        assertEquals(UpdateEmail.TYPE_WORK, result.getType());
        assertFalse(result.getIsPrimary());
    }

    @Test
    void updateEmailRejectsMissingOrInvalidId() {
        ValidationException missingIdException = assertThrows(ValidationException.class,
                () -> emailService.updateEmail(
                        "jane@example.com", "C123", null, false, null, AddEmail.TYPE_WORK));
        ValidationException invalidIdException = assertThrows(ValidationException.class,
                () -> emailService.updateEmail(
                        "jane@example.com", "C123", 0L, false, null, AddEmail.TYPE_WORK));

        assertEquals("Email id cannot be empty",
                missingIdException.getResult().getError("emailId"));
        assertEquals("Email id must be a positive integer",
                invalidIdException.getResult().getError("emailId"));
        verifyNoInteractions(adapter);
    }

    @Test
    void updateEmailReportsAllInvalidFieldsTogether() {
        ValidationException exception = assertThrows(ValidationException.class,
                () -> emailService.updateEmail("not-an-email", "", -1L, false, " ",
                        AddEmail.TYPE_OTHER));

        assertEquals("Email address is invalid", exception.getResult().getError("address"));
        assertEquals("Customer cannot be empty", exception.getResult().getError("customer"));
        assertEquals("Other email type cannot be empty",
                exception.getResult().getError("otherType"));
        assertEquals("Email id must be a positive integer",
                exception.getResult().getError("emailId"));
        verifyNoInteractions(adapter);
    }

    @Test
    void updateEmailThrowsLavsExceptionWhenMainframeRejectsRequest() throws IOException {
        when(adapter.send(any(UpdateEmail.class)))
                .thenReturn(new Status(404, "Email not found", 2));

        LavsException exception = assertThrows(LavsException.class,
                () -> emailService.updateEmail(
                        "jane@example.com", "C123", 7L, false, null, AddEmail.TYPE_WORK));

        assertEquals("Email not found", exception.getMessage());
        assertEquals(404, exception.getErrorCode());
    }

    @Test
    void deleteEmailSendsTrimmedValuesAndReturnsMessage() throws Exception {
        when(adapter.send(any(DeleteEmail.class))).thenReturn(new Status(3));

        DeleteEmail result = emailService.deleteEmail(" C123 ", 7L);

        ArgumentCaptor<DeleteEmail> captor = ArgumentCaptor.forClass(DeleteEmail.class);
        verify(adapter).send(captor.capture());
        assertEquals(result, captor.getValue());
        assertEquals("C123", result.getCustomer());
        assertEquals(7L, result.getId());
    }

    @Test
    void deleteEmailRejectsMissingOrInvalidIdentifiers() {
        ValidationException missingCustomerException = assertThrows(ValidationException.class,
                () -> emailService.deleteEmail(null, 7L));
        ValidationException blankCustomerException = assertThrows(ValidationException.class,
                () -> emailService.deleteEmail(" ", 7L));
        ValidationException missingIdException = assertThrows(ValidationException.class,
                () -> emailService.deleteEmail("C123", null));
        ValidationException invalidIdException = assertThrows(ValidationException.class,
                () -> emailService.deleteEmail("C123", 0L));

        assertEquals("Customer number cannot be null or empty",
                missingCustomerException.getResult().getError("customerNumber"));
        assertEquals("Customer number cannot be null or empty",
                blankCustomerException.getResult().getError("customerNumber"));
        assertEquals("Email id cannot be empty",
                missingIdException.getResult().getError("emailId"));
        assertEquals("Email id must be a positive integer",
                invalidIdException.getResult().getError("emailId"));
        verifyNoInteractions(adapter);
    }

    @Test
    void deleteEmailThrowsLavsExceptionWhenMainframeRejectsRequest() throws IOException {
        when(adapter.send(any(DeleteEmail.class)))
                .thenReturn(new Status(404, "Email not found", 3));

        LavsException exception = assertThrows(LavsException.class,
                () -> emailService.deleteEmail("C123", 7L));

        assertEquals("Email not found", exception.getMessage());
    }

    @Test
    void listEmailsSendsListMessageAndReturnsItems() throws Exception {
        when(adapter.send(any(List.class))).thenReturn(new Status(1));

        List.Item[] result = emailService.listEmails(" C123 ", 1L, 25L);

        ArgumentCaptor<List> captor = ArgumentCaptor.forClass(List.class);
        verify(adapter).send(captor.capture());
        assertEquals(1L, captor.getValue().getPage());
        assertEquals(0, result.length);
    }

    @Test
    void listEmailsRejectsMissingCustomerNumberAndInvalidPaging() {
        ValidationException customerException = assertThrows(ValidationException.class,
                () -> emailService.listEmails(" ", 1L, 25L));
        ValidationException invalidException = assertThrows(ValidationException.class,
                () -> emailService.listEmails("C123", 0L, 0L));
        ValidationException missingException = assertThrows(ValidationException.class,
                () -> emailService.listEmails("C123", null, null));

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
    void listEmailsThrowsLavsExceptionWhenMainframeRejectsRequest() throws IOException {
        when(adapter.send(any(List.class)))
                .thenReturn(new Status(500, "Email list rejected", 1));

        LavsException exception = assertThrows(LavsException.class,
                () -> emailService.listEmails("C123", 1L, 25L));

        assertEquals("Email list rejected", exception.getMessage());
    }

    @Test
    void retrieveEmailSendsRetrieveMessageAndReturnsIt() throws Exception {
        when(adapter.send(any(Retrieve.class))).thenReturn(new Status(1));

        Retrieve result = emailService.retrieveEmail(" C123 ", 7L);

        assertEquals(7L, result.getId());
        verify(adapter).send(same(result));
    }

    @Test
    void retrieveEmailRejectsMissingOrInvalidIdentifiers() {
        ValidationException customerException = assertThrows(ValidationException.class,
                () -> emailService.retrieveEmail(null, 7L));
        ValidationException idException = assertThrows(ValidationException.class,
                () -> emailService.retrieveEmail("C123", -2L));

        assertEquals("Customer number cannot be null or empty",
                customerException.getResult().getError("customerNumber"));
        assertEquals("Email id must be a positive integer",
                idException.getResult().getError("emailId"));
        verifyNoInteractions(adapter);
    }

    @Test
    void retrieveEmailPropagatesNetworkFailure() throws IOException {
        when(adapter.send(any(Retrieve.class)))
                .thenThrow(new IOException("connection unavailable"));

        IOException exception = assertThrows(IOException.class,
                () -> emailService.retrieveEmail("C123", 7L));

        assertEquals("connection unavailable", exception.getMessage());
    }

    @Test
    void findPrimaryEmailReturnsTheFirstPrimaryAddress() throws Exception {
        stubEmailList(
                new String[] {"old@example.com", "n"},
                new String[] {"main@example.com", "y"},
                new String[] {"other@example.com", "y"});

        assertEquals("main@example.com", emailService.findPrimaryEmail("C123"));
    }

    @Test
    void findPrimaryEmailReturnsEmptyStringWhenPrimaryHasNoAddress() throws Exception {
        stubEmailList(new String[] {null, "y"});

        assertEquals("", emailService.findPrimaryEmail("C123"));
    }

    @Test
    void findPrimaryEmailReturnsEmptyStringWhenNoEmailIsPrimary() throws Exception {
        stubEmailList(new String[] {"old@example.com", "n"}, new String[] {"x@example.com", null});

        assertEquals("", emailService.findPrimaryEmail("C123"));
    }

    @Test
    void findPrimaryEmailReturnsEmptyStringWhenCustomerHasNoEmails() throws Exception {
        when(adapter.send(any(List.class))).thenReturn(new Status(1));

        assertEquals("", emailService.findPrimaryEmail("C123"));
    }

    @Test
    void validateEmailDetailsAcceptsEachStandardType() {
        assertDoesNotThrow(() -> CustomerEmailService.validateEmailDetails(
                "jane@example.com", "C123", null, AddEmail.TYPE_PERSONAL));
        assertDoesNotThrow(() -> CustomerEmailService.validateEmailDetails(
                "jane@example.com", "C123", null, AddEmail.TYPE_WORK));
        assertDoesNotThrow(() -> CustomerEmailService.validateEmailDetails(
                "jane@example.com", "C123", "Backup", AddEmail.TYPE_OTHER));
    }

    @Test
    void validateEmailDetailsRejectsBadlyFormattedAddresses() {
        for (String address : new String[] {"jane", "jane@", "@example.com",
                "jane@example", "jane @example.com", "jane@@example.com"}) {
            ValidationException exception = assertThrows(ValidationException.class,
                    () -> CustomerEmailService.validateEmailDetails(
                            address, "C123", null, AddEmail.TYPE_PERSONAL),
                    address);

            assertEquals("Email address is invalid",
                    exception.getResult().getError("address"), address);
        }
    }

    @Test
    void validateEmailDetailsRequiresType() {
        ValidationException exception = assertThrows(ValidationException.class,
                () -> CustomerEmailService.validateEmailDetails(
                        "jane@example.com", "C123", null, null));

        assertEquals("Email type cannot be empty", exception.getResult().getError("type"));
    }
}
