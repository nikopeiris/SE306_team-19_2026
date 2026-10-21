package business.system.customer;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import java.io.IOException;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import business.lavsexceptions.LavsException;
import business.validation.ValidationException;
import dataaccess.systemadapters.MainframeAdapter;
import mainframe.client.Status;
import mainframe.client.messages.customer.AddDocument;
import mainframe.client.messages.customer.DeleteDocument;
import mainframe.client.messages.customerDocument.Retrieve;
import mainframe.client.messages.customerDocument.List;

@ExtendWith(MockitoExtension.class)
class CustomerDocumentServiceTest {
    @Mock private MainframeAdapter adapter;
    private CustomerDocumentService service;

    @BeforeEach
    void setUp() {
        service = new CustomerDocumentService(adapter);
    }

    @Test
    void constructorRejectsNullAdapter() {
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                () -> new CustomerDocumentService(null));
        assertEquals("Document adpater cannot be null", exception.getMessage());
    }

    @Test
    void addDocumentTrimsValuesAndAllowsOptionalFileType() throws Exception {
        when(adapter.send(any(AddDocument.class))).thenReturn(new Status(1));

        AddDocument result = service.addDocument(" C1 ", " passport.pdf ",
                " pdf ", 12L, " Passport ");

        assertEquals("C1", result.getCustomer());
        assertEquals("passport.pdf", result.getFileName());
        assertEquals("pdf", result.getFileType());
        assertEquals(12L, result.getSize());
        assertEquals("Passport", result.getType());
        verify(adapter).send(result);
    }

    @Test
    void addDocumentReportsAllInvalidFieldsAndDoesNotSend() {
        ValidationException exception = assertThrows(ValidationException.class,
                () -> service.addDocument(" ", null, null, 0L, ""));

        assertEquals("Customer cannot be empty", exception.getResult().getError("customer"));
        assertEquals("File Name cannot be empty", exception.getResult().getError("filename"));
        assertEquals("Type cannot be empty", exception.getResult().getError("type"));
        assertEquals("Size must be a positive integer", exception.getResult().getError("size"));
        verifyNoInteractions(adapter);
    }

    @Test
    void documentValidationChecksMaximumLengthsAndOptionalType() {
        ValidationException exception = assertThrows(ValidationException.class,
                () -> CustomerDocumentService.validateDocumentIdDetails(
                        "x".repeat(21), "x".repeat(51), "x".repeat(51),
                        1234567890123456L));

        assertEquals("Type must not exceed 20 characters",
                exception.getResult().getError("type"));
        assertEquals("Filename must not exceed 50 characters",
                exception.getResult().getError("filename"));
        assertEquals("File type must not exceed 50 characters",
                exception.getResult().getError("fileType"));
        assertEquals("Size must not exceed 15 characters",
                exception.getResult().getError("size"));
        assertDoesNotThrow(() -> CustomerDocumentService
                .validateDocumentIdDetails("Passport", "a.pdf", null, 1L));
    }

    @Test
    void deleteAndRetrieveValidateIdentifiersAndSendMessages() throws Exception {
        when(adapter.send(any(DeleteDocument.class))).thenReturn(new Status(1));
        when(adapter.send(any(Retrieve.class))).thenReturn(new Status(1));

        DeleteDocument deleted = service.deleteDocument(" C1 ", 4L);
        Retrieve retrieved = service.retrieveCustomerDocument(" C1 ", 4L);

        assertEquals("C1", deleted.getCustomer());
        assertEquals(4L, deleted.getId());
        verify(adapter).send(deleted);
        verify(adapter).send(retrieved);
    }

    @Test
    void listDocumentsReturnsMappedDocuments() throws Exception {
        when(adapter.send(any(List.class))).thenAnswer(invocation -> {
            List message = invocation.getArgument(0);
            java.lang.reflect.Field data = List.class.getDeclaredField("_data");
            data.setAccessible(true);
            @SuppressWarnings("unchecked")
            java.util.ArrayList<List.Item> items = (java.util.ArrayList<List.Item>) data.get(message);
            items.add(message.new Item("passport.pdf", 7L, "Passport"));
            return new Status(1);
        });

        List.Item[] items = service.listDocuments(" C1 ", 1L, 100L);

        assertEquals(1, items.length);
        assertEquals("passport.pdf", items[0].getFileName());
        assertEquals(7L, items[0].getId());
        assertEquals("Passport", items[0].getType());
    }

    @Test
    void listDocumentsPropagatesRejectedStatus() throws Exception {
        when(adapter.send(any(List.class)))
                .thenReturn(new Status(500, "Documents unavailable", 1));

        LavsException exception = assertThrows(LavsException.class,
                () -> service.listDocuments("C1", 1L, 100L));

        assertEquals("Documents unavailable", exception.getMessage());
    }

    @Test
    void documentOperationsRejectInvalidIdentifiers() {
        assertThrows(ValidationException.class, () -> service.deleteDocument(" ", null));
        assertThrows(ValidationException.class, () -> service.retrieveCustomerDocument(null, 0L));
        assertThrows(ValidationException.class, () -> service.listDocuments("C1", 0L, 1L));
        assertThrows(ValidationException.class, () -> service.listDocuments("C1", 1L, null));
        verifyNoInteractions(adapter);
    }

    @Test
    void addDocumentPropagatesServerAndConnectionFailures() throws Exception {
        when(adapter.send(any(AddDocument.class)))
                .thenReturn(new Status(400, "Document rejected", 1));
        LavsException rejected = assertThrows(LavsException.class,
                () -> service.addDocument("C1", "a.pdf", null, 1L, "Passport"));
        assertEquals("Document rejected", rejected.getMessage());

        when(adapter.send(any(AddDocument.class)))
                .thenThrow(new IOException("offline"));
        IOException offline = assertThrows(IOException.class,
                () -> service.addDocument("C1", "a.pdf", null, 1L, "Passport"));
        assertEquals("offline", offline.getMessage());
    }
}
