package business.system.customer;

import business.lavsexceptions.LavsException;
import business.validation.ValidationException;
import dataaccess.systemadapters.MainframeAdapter;
import mainframe.client.Status;
import mainframe.client.messages.customer.AddRelationship;
import mainframe.client.messages.customer.DeleteRelationship;
import mainframe.client.messages.customer.UpdateRelationship;
import mainframe.client.messages.customerRelationship.List;
import mainframe.client.messages.customerRelationship.Retrieve;
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
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.same;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CustomerRelationshipServiceTest {
    @Mock
    private MainframeAdapter adapter;

    private CustomerRelationshipService relationshipService;

    @BeforeEach
    void setUp() {
        relationshipService = new CustomerRelationshipService(adapter);
    }

    // makes the stubbed list request return one relationship per type given
    private void stubRelationshipList(String... types) throws Exception {
        when(adapter.send(any(List.class))).thenAnswer(invocation -> {
            List listMessage = invocation.getArgument(0);
            Field dataField = List.class.getDeclaredField("_data");
            dataField.setAccessible(true);
            @SuppressWarnings("unchecked")
            ArrayList<List.Item> items = (ArrayList<List.Item>) dataField.get(listMessage);
            long id = 1L;
            for (String type : types) {
                items.add(listMessage.new Item(id, "C20" + id, type));
                id++;
            }
            return new Status(0, null, 1L);
        });
    }

    @Test
    void constructorRejectsNullAdapter() {
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                () -> new CustomerRelationshipService(null));

        assertEquals("Relationship adapter cannot be null", exception.getMessage());
    }

    @Test
    void addRelationshipSendsTrimmedValuesAndDropsOtherTypeForStandardType() throws Exception {
        when(adapter.send(any(AddRelationship.class))).thenReturn(new Status(1));

        AddRelationship result = relationshipService.addRelationship(
                " C123 ", " stale text ", " C456 ", AddRelationship.TYPE_SPOUSE);

        ArgumentCaptor<AddRelationship> captor = ArgumentCaptor.forClass(AddRelationship.class);
        verify(adapter).send(captor.capture());
        assertEquals(result, captor.getValue());
        assertEquals("C123", result.getCustomer());
        assertNull(result.getOtherType());
        assertEquals("C456", result.getSecondaryCustomerNumber());
        assertEquals(AddRelationship.TYPE_SPOUSE, result.getType());
    }

    @Test
    void addRelationshipKeepsTrimmedOtherTypeWhenTypeIsOther() throws Exception {
        when(adapter.send(any(AddRelationship.class))).thenReturn(new Status(1));

        AddRelationship result = relationshipService.addRelationship(
                "C123", " Guardian ", "C456", " Other ");

        assertEquals("Guardian", result.getOtherType());
        assertEquals(AddRelationship.TYPE_OTHER, result.getType());
    }

    @Test
    void addRelationshipAllowsNullOtherTypeForStandardType() throws Exception {
        when(adapter.send(any(AddRelationship.class))).thenReturn(new Status(1));

        AddRelationship result = relationshipService.addRelationship(
                "C123", null, "C456", AddRelationship.TYPE_LEGALAUTHORITY);

        assertNull(result.getOtherType());
        assertEquals(AddRelationship.TYPE_LEGALAUTHORITY, result.getType());
    }

    @Test
    void addRelationshipRejectsMissingDetailsWithoutContactingMainframe() {
        ValidationException exception = assertThrows(ValidationException.class,
                () -> relationshipService.addRelationship(null, null, " ", ""));

        assertEquals("Customer cannot be empty", exception.getResult().getError("customer"));
        assertEquals("Related customer cannot be empty",
                exception.getResult().getError("secondaryCustomerNumber"));
        assertEquals("Relationship type cannot be empty", exception.getResult().getError("type"));
        verifyNoInteractions(adapter);
    }

    @Test
    void addRelationshipRejectsInvalidType() {
        ValidationException exception = assertThrows(ValidationException.class,
                () -> relationshipService.addRelationship("C123", null, "C456", "Cousin"));

        assertEquals("Relationship type is invalid", exception.getResult().getError("type"));
        verifyNoInteractions(adapter);
    }

    @Test
    void addRelationshipRejectsOtherTypeWithoutDescription() {
        ValidationException nullException = assertThrows(ValidationException.class,
                () -> relationshipService.addRelationship("C123", null, "C456", "Other"));
        ValidationException blankException = assertThrows(ValidationException.class,
                () -> relationshipService.addRelationship("C123", "  ", "C456", "Other"));

        assertEquals("Other relationship type cannot be empty",
                nullException.getResult().getError("otherType"));
        assertEquals("Other relationship type cannot be empty",
                blankException.getResult().getError("otherType"));
        verifyNoInteractions(adapter);
    }

    @Test
    void addRelationshipRejectsSelfRelationship() {
        ValidationException exception = assertThrows(ValidationException.class,
                () -> relationshipService.addRelationship(" c123 ", null, "C123", "Parent"));

        assertEquals("A customer cannot be related to themselves",
                exception.getResult().getError("secondaryCustomerNumber"));
        verifyNoInteractions(adapter);
    }

    @Test
    void addRelationshipThrowsLavsExceptionWhenMainframeRejectsRequest() throws IOException {
        when(adapter.send(any(AddRelationship.class)))
                .thenReturn(new Status(400, "Relationship rejected", 1));

        LavsException exception = assertThrows(LavsException.class,
                () -> relationshipService.addRelationship("C123", null, "C456", "Parent"));

        assertEquals("Relationship rejected", exception.getMessage());
        assertEquals(400, exception.getErrorCode());
    }

    @Test
    void addRelationshipPropagatesNetworkFailure() throws IOException {
        when(adapter.send(any(AddRelationship.class)))
                .thenThrow(new IOException("connection unavailable"));

        IOException exception = assertThrows(IOException.class,
                () -> relationshipService.addRelationship("C123", null, "C456", "Parent"));

        assertEquals("connection unavailable", exception.getMessage());
    }

    @Test
    void updateRelationshipSendsTrimmedValuesAndReturnsMessage() throws Exception {
        when(adapter.send(any(UpdateRelationship.class))).thenReturn(new Status(1));

        UpdateRelationship result = relationshipService.updateRelationship(
                " C123 ", 7L, " Carer ", " C456 ", "Other");

        verify(adapter).send(same(result));
        assertEquals("C123", result.getCustomer());
        assertEquals(7L, result.getId());
        assertEquals("Carer", result.getOtherType());
        assertEquals("C456", result.getSecondaryCustomerNumber());
        assertEquals(UpdateRelationship.TYPE_OTHER, result.getType());
    }

    @Test
    void updateRelationshipDropsOtherTypeForStandardType() throws Exception {
        when(adapter.send(any(UpdateRelationship.class))).thenReturn(new Status(1));

        UpdateRelationship result = relationshipService.updateRelationship(
                "C123", 7L, "Carer", "C456", "Parent");

        assertNull(result.getOtherType());
        assertEquals(UpdateRelationship.TYPE_PARENT, result.getType());
    }

    @Test
    void updateRelationshipRejectsMissingOrInvalidId() {
        ValidationException missingException = assertThrows(ValidationException.class,
                () -> relationshipService.updateRelationship("C123", null, null, "C456", "Parent"));
        ValidationException invalidException = assertThrows(ValidationException.class,
                () -> relationshipService.updateRelationship("C123", 0L, null, "C456", "Parent"));

        assertEquals("Relationship id cannot be empty",
                missingException.getResult().getError("relationshipId"));
        assertEquals("Relationship id must be a positive integer",
                invalidException.getResult().getError("relationshipId"));
        verifyNoInteractions(adapter);
    }

    @Test
    void updateRelationshipRejectsInvalidDetailsWithoutContactingMainframe() {
        ValidationException exception = assertThrows(ValidationException.class,
                () -> relationshipService.updateRelationship(" ", -1L, null, null, "Other"));

        assertEquals("Customer cannot be empty", exception.getResult().getError("customer"));
        assertEquals("Relationship id must be a positive integer",
                exception.getResult().getError("relationshipId"));
        assertEquals("Related customer cannot be empty",
                exception.getResult().getError("secondaryCustomerNumber"));
        assertEquals("Other relationship type cannot be empty",
                exception.getResult().getError("otherType"));
        verifyNoInteractions(adapter);
    }

    @Test
    void updateRelationshipThrowsLavsExceptionWhenMainframeRejectsRequest() throws IOException {
        when(adapter.send(any(UpdateRelationship.class)))
                .thenReturn(new Status(404, "Relationship not found", 1));

        LavsException exception = assertThrows(LavsException.class,
                () -> relationshipService.updateRelationship("C123", 7L, null, "C456", "Spouse"));

        assertEquals("Relationship not found", exception.getMessage());
        assertEquals(404, exception.getErrorCode());
    }

    @Test
    void deleteRelationshipSendsTrimmedCustomerNumber() throws Exception {
        when(adapter.send(any(DeleteRelationship.class))).thenReturn(new Status(1));

        DeleteRelationship result = relationshipService.deleteRelationship(" C123 ", 7L);

        verify(adapter).send(same(result));
        assertEquals("C123", result.getCustomer());
        assertEquals(7L, result.getId());
    }

    @Test
    void deleteRelationshipRejectsMissingOrInvalidIdentifiers() {
        ValidationException nullCustomer = assertThrows(ValidationException.class,
                () -> relationshipService.deleteRelationship(null, 7L));
        ValidationException blankCustomer = assertThrows(ValidationException.class,
                () -> relationshipService.deleteRelationship(" ", 7L));
        ValidationException nullId = assertThrows(ValidationException.class,
                () -> relationshipService.deleteRelationship("C123", null));
        ValidationException zeroId = assertThrows(ValidationException.class,
                () -> relationshipService.deleteRelationship("C123", 0L));

        assertEquals("Customer number cannot be null or empty",
                nullCustomer.getResult().getError("customerNumber"));
        assertEquals("Customer number cannot be null or empty",
                blankCustomer.getResult().getError("customerNumber"));
        assertEquals("Relationship id cannot be empty",
                nullId.getResult().getError("relationshipId"));
        assertEquals("Relationship id must be a positive integer",
                zeroId.getResult().getError("relationshipId"));
        verifyNoInteractions(adapter);
    }

    @Test
    void deleteRelationshipThrowsLavsExceptionWhenMainframeRejectsRequest() throws IOException {
        when(adapter.send(any(DeleteRelationship.class)))
                .thenReturn(new Status(404, "Relationship not found", 1));

        LavsException exception = assertThrows(LavsException.class,
                () -> relationshipService.deleteRelationship("C123", 7L));

        assertEquals("Relationship not found", exception.getMessage());
    }

    @Test
    void listRelationshipsSendsListMessageAndReturnsItems() throws Exception {
        stubRelationshipList("Parent", "Spouse");

        List.Item[] result = relationshipService.listRelationships(" C123 ", 1L, 25L);

        ArgumentCaptor<List> captor = ArgumentCaptor.forClass(List.class);
        verify(adapter).send(captor.capture());
        assertEquals(1L, captor.getValue().getPage());
        assertEquals(2, result.length);
        assertEquals("Parent", result[0].getType());
        assertEquals("C201", result[0].getSecondaryCustomerNumber());
        assertEquals("Spouse", result[1].getType());
        assertEquals(2L, result[1].getId());
    }

    @Test
    void listRelationshipsReturnsEmptyArrayWhenCustomerHasNoRelationships() throws Exception {
        when(adapter.send(any(List.class))).thenReturn(new Status(1));

        assertEquals(0, relationshipService.listRelationships("C123", 1L, 25L).length);
    }

    @Test
    void listRelationshipsRejectsMissingCustomerNumberAndInvalidPaging() {
        ValidationException customerException = assertThrows(ValidationException.class,
                () -> relationshipService.listRelationships(" ", 1L, 25L));
        ValidationException invalidException = assertThrows(ValidationException.class,
                () -> relationshipService.listRelationships("C123", 0L, 0L));
        ValidationException missingException = assertThrows(ValidationException.class,
                () -> relationshipService.listRelationships("C123", null, null));

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
    void listRelationshipsThrowsLavsExceptionWhenMainframeRejectsRequest() throws IOException {
        when(adapter.send(any(List.class)))
                .thenReturn(new Status(500, "Relationship list rejected", 1));

        LavsException exception = assertThrows(LavsException.class,
                () -> relationshipService.listRelationships("C123", 1L, 25L));

        assertEquals("Relationship list rejected", exception.getMessage());
    }

    @Test
    void retrieveRelationshipSendsRetrieveMessageAndReturnsIt() throws Exception {
        when(adapter.send(any(Retrieve.class))).thenReturn(new Status(1));

        Retrieve result = relationshipService.retrieveRelationship(" C123 ", 7L);

        assertEquals(7L, result.getId());
        verify(adapter).send(same(result));
    }

    @Test
    void retrieveRelationshipRejectsMissingOrInvalidIdentifiers() {
        ValidationException customerException = assertThrows(ValidationException.class,
                () -> relationshipService.retrieveRelationship(null, 7L));
        ValidationException idException = assertThrows(ValidationException.class,
                () -> relationshipService.retrieveRelationship("C123", -2L));

        assertEquals("Customer number cannot be null or empty",
                customerException.getResult().getError("customerNumber"));
        assertEquals("Relationship id must be a positive integer",
                idException.getResult().getError("relationshipId"));
        verifyNoInteractions(adapter);
    }

    @Test
    void retrieveRelationshipPropagatesNetworkFailure() throws IOException {
        when(adapter.send(any(Retrieve.class)))
                .thenThrow(new IOException("connection unavailable"));

        IOException exception = assertThrows(IOException.class,
                () -> relationshipService.retrieveRelationship("C123", 7L));

        assertEquals("connection unavailable", exception.getMessage());
    }

    @Test
    void validateRelationshipDetailsAcceptsValidRelationships() {
        assertDoesNotThrow(() -> CustomerRelationshipService.validateRelationshipDetails(
                "C123", null, "C456", "Parent"));
        assertDoesNotThrow(() -> CustomerRelationshipService.validateRelationshipDetails(
                "C123", "Guardian", "C456", "Other"));
        // the customer is optional here so a new customer's form can be checked before it has a number
        assertDoesNotThrow(() -> CustomerRelationshipService.validateRelationshipDetails(
                null, null, "C456", "Spouse"));
    }

    @Test
    void validateRelationshipDetailsRejectsInvalidFields() {
        ValidationException missingException = assertThrows(ValidationException.class,
                () -> CustomerRelationshipService.validateRelationshipDetails(
                        "C123", null, null, null));
        ValidationException invalidException = assertThrows(ValidationException.class,
                () -> CustomerRelationshipService.validateRelationshipDetails(
                        "C123", null, "c123", "Friend"));

        assertEquals("Related customer cannot be empty",
                missingException.getResult().getError("secondaryCustomerNumber"));
        assertEquals("Relationship type cannot be empty",
                missingException.getResult().getError("type"));
        assertEquals("Relationship type is invalid", invalidException.getResult().getError("type"));
        assertEquals("A customer cannot be related to themselves",
                invalidException.getResult().getError("secondaryCustomerNumber"));
    }
}
