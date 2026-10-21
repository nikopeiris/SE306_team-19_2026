package business.system;

import business.lavsexceptions.LavsException;
import business.validation.ValidationException;
import dataaccess.systemadapters.MainframeAdapter;
import mainframe.client.Status;
import mainframe.client.messages.loan.AddDocument;
import mainframe.client.messages.loan.DeleteDocument;
import mainframe.client.messages.loanDocument.List;
import mainframe.client.messages.loanDocument.Retrieve;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.IOException;
import java.lang.reflect.Field;
import java.util.ArrayList;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.same;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class LoanDocumentServiceTest {
    @Mock
    private MainframeAdapter adapter;

    private LoanDocumentService documentService;

    @BeforeEach
    void setUp() {
        documentService = new LoanDocumentService(adapter);
    }

    // makes the stubbed list request return one document per file name given
    private void stubDocumentList(String... fileNames) throws Exception {
        when(adapter.send(any(List.class))).thenAnswer(invocation -> {
            List listMessage = invocation.getArgument(0);
            ArrayList<List.Item> items = field(listMessage, "_data");
            long id = 1L;
            for (String fileName : fileNames) {
                items.add(listMessage.new Item(fileName, id++, "Contract"));
            }
            return new Status(0, null, 1L);
        });
    }

    // makes the stubbed retrieve request fill in the stored file type and size
    private void stubRetrieve(String fileType, long size) throws Exception {
        when(adapter.send(any(Retrieve.class))).thenAnswer(invocation -> {
            Retrieve retrieveMessage = invocation.getArgument(0);
            setField(retrieveMessage, "_fileType", fileType);
            setField(retrieveMessage, "_size", size);
            return new Status(0, null, 1L);
        });
    }

    @SuppressWarnings("unchecked")
    private static <T> T field(Object target, String name) throws Exception {
        Field f = target.getClass().getDeclaredField(name);
        f.setAccessible(true);
        return (T) f.get(target);
    }

    private static void setField(Object target, String name, Object value) throws Exception {
        Field f = target.getClass().getDeclaredField(name);
        f.setAccessible(true);
        f.set(target, value);
    }

    @Test
    void constructorRejectsNullAdapter() {
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                () -> new LoanDocumentService(null));

        assertEquals("Loan document adapter cannot be null", exception.getMessage());
    }

    @Test
    void addDocumentSendsTrimmedValuesAndReturnsMessage() throws Exception {
        when(adapter.send(any(AddDocument.class))).thenReturn(new Status(1));

        AddDocument result = documentService.addDocument(
                " L100 ", " contract.pdf ", " pdf ", 2048L, " Contract ");

        ArgumentCaptor<AddDocument> captor = ArgumentCaptor.forClass(AddDocument.class);
        verify(adapter).send(captor.capture());
        assertEquals(result, captor.getValue());
        assertEquals("L100", result.getLoanNumber());
        assertEquals("contract.pdf", result.getFileName());
        assertEquals("pdf", result.getFileType());
        assertEquals(2048L, result.getSize());
        assertEquals("Contract", result.getType());
    }

    @Test
    void addDocumentAllowsMissingFileType() throws Exception {
        when(adapter.send(any(AddDocument.class))).thenReturn(new Status(1));

        AddDocument result = documentService.addDocument("L100", "notes", null, 10L, "Other");

        assertNull(result.getFileType());
    }

    @Test
    void addDocumentRejectsMissingDetailsWithoutContactingMainframe() {
        ValidationException exception = assertThrows(ValidationException.class,
                () -> documentService.addDocument(" ", null, null, null, ""));

        assertEquals("Loan number cannot be null or empty",
                exception.getResult().getError("loanNumber"));
        assertEquals("File Name cannot be empty", exception.getResult().getError("filename"));
        assertEquals("Size cannot be empty", exception.getResult().getError("size"));
        assertEquals("Type cannot be empty", exception.getResult().getError("type"));
        verifyNoInteractions(adapter);
    }

    @Test
    void addDocumentRejectsNonPositiveSizeAndOverLongFields() {
        ValidationException sizeException = assertThrows(ValidationException.class,
                () -> documentService.addDocument("L100", "a.pdf", "pdf", 0L, "Contract"));
        ValidationException lengthException = assertThrows(ValidationException.class,
                () -> documentService.addDocument("L100", "f".repeat(51), "t".repeat(51),
                        1L, "x".repeat(21)));

        assertEquals("Size must be a positive integer",
                sizeException.getResult().getError("size"));
        assertEquals("Filename must not exceed 50 characters",
                lengthException.getResult().getError("filename"));
        assertEquals("File type must not exceed 50 characters",
                lengthException.getResult().getError("fileType"));
        assertEquals("Type must not exceed 20 characters",
                lengthException.getResult().getError("type"));
        verifyNoInteractions(adapter);
    }

    @Test
    void addDocumentThrowsLavsExceptionWhenMainframeRejectsRequest() throws IOException {
        when(adapter.send(any(AddDocument.class)))
                .thenReturn(new Status(400, "Document rejected", 1));

        LavsException exception = assertThrows(LavsException.class,
                () -> documentService.addDocument("L100", "a.pdf", "pdf", 1L, "Contract"));

        assertEquals("Document rejected", exception.getMessage());
        assertEquals(400, exception.getErrorCode());
    }

    @Test
    void addDocumentPropagatesNetworkFailure() throws IOException {
        when(adapter.send(any(AddDocument.class)))
                .thenThrow(new IOException("connection unavailable"));

        IOException exception = assertThrows(IOException.class,
                () -> documentService.addDocument("L100", "a.pdf", "pdf", 1L, "Contract"));

        assertEquals("connection unavailable", exception.getMessage());
    }

    @Test
    void updateDocumentAddsNewDetailsThenDeletesOldDocument() throws Exception {
        stubRetrieve("pdf", 2048L);
        when(adapter.send(any(AddDocument.class))).thenReturn(new Status(1));
        when(adapter.send(any(DeleteDocument.class))).thenReturn(new Status(1));

        AddDocument result = documentService.updateDocument(" L100 ", 7L, " Valuation ", " house.pdf ");

        assertEquals("L100", result.getLoanNumber());
        assertEquals("house.pdf", result.getFileName());
        assertEquals("Valuation", result.getType());
        assertEquals("pdf", result.getFileType());
        assertEquals(2048L, result.getSize());

        InOrder order = inOrder(adapter);
        order.verify(adapter).send(any(Retrieve.class));
        order.verify(adapter).send(same(result));
        ArgumentCaptor<DeleteDocument> deleteCaptor = ArgumentCaptor.forClass(DeleteDocument.class);
        order.verify(adapter).send(deleteCaptor.capture());
        assertEquals(7L, deleteCaptor.getValue().getId());
        assertEquals("L100", deleteCaptor.getValue().getLoanNumber());
    }

    @Test
    void updateDocumentKeepsOldDocumentWhenNewDetailsAreInvalid() throws Exception {
        stubRetrieve("pdf", 2048L);

        ValidationException exception = assertThrows(ValidationException.class,
                () -> documentService.updateDocument("L100", 7L, " ", "house.pdf"));

        assertEquals("Type cannot be empty", exception.getResult().getError("type"));
        verify(adapter, never()).send(any(AddDocument.class));
        verify(adapter, never()).send(any(DeleteDocument.class));
    }

    @Test
    void updateDocumentKeepsOldDocumentWhenAddIsRejected() throws Exception {
        stubRetrieve("pdf", 2048L);
        when(adapter.send(any(AddDocument.class)))
                .thenReturn(new Status(400, "Document rejected", 1));

        assertThrows(LavsException.class,
                () -> documentService.updateDocument("L100", 7L, "Valuation", "house.pdf"));

        verify(adapter, never()).send(any(DeleteDocument.class));
    }

    @Test
    void updateDocumentRejectsInvalidIdentifiersWithoutContactingMainframe() {
        ValidationException exception = assertThrows(ValidationException.class,
                () -> documentService.updateDocument(null, 0L, "Valuation", "house.pdf"));

        assertEquals("Loan number cannot be null or empty",
                exception.getResult().getError("loanNumber"));
        assertEquals("Document id must be a positive integer",
                exception.getResult().getError("documentId"));
        verifyNoInteractions(adapter);
    }

    @Test
    void deleteDocumentSendsTrimmedLoanNumber() throws Exception {
        when(adapter.send(any(DeleteDocument.class))).thenReturn(new Status(1));

        DeleteDocument result = documentService.deleteDocument(" L100 ", 7L);

        verify(adapter).send(same(result));
        assertEquals("L100", result.getLoanNumber());
        assertEquals(7L, result.getId());
    }

    @Test
    void deleteDocumentRejectsMissingOrInvalidIdentifiers() {
        ValidationException nullLoan = assertThrows(ValidationException.class,
                () -> documentService.deleteDocument(null, 7L));
        ValidationException blankLoan = assertThrows(ValidationException.class,
                () -> documentService.deleteDocument(" ", 7L));
        ValidationException nullId = assertThrows(ValidationException.class,
                () -> documentService.deleteDocument("L100", null));
        ValidationException zeroId = assertThrows(ValidationException.class,
                () -> documentService.deleteDocument("L100", 0L));

        assertEquals("Loan number cannot be null or empty",
                nullLoan.getResult().getError("loanNumber"));
        assertEquals("Loan number cannot be null or empty",
                blankLoan.getResult().getError("loanNumber"));
        assertEquals("Document id cannot be empty", nullId.getResult().getError("documentId"));
        assertEquals("Document id must be a positive integer",
                zeroId.getResult().getError("documentId"));
        verifyNoInteractions(adapter);
    }

    @Test
    void deleteDocumentThrowsLavsExceptionWhenMainframeRejectsRequest() throws IOException {
        when(adapter.send(any(DeleteDocument.class)))
                .thenReturn(new Status(404, "Document not found", 1));

        LavsException exception = assertThrows(LavsException.class,
                () -> documentService.deleteDocument("L100", 7L));

        assertEquals("Document not found", exception.getMessage());
        assertEquals(404, exception.getErrorCode());
    }

    @Test
    void listDocumentsSendsListMessageAndReturnsItems() throws Exception {
        stubDocumentList("contract.pdf", "valuation.pdf");

        List.Item[] result = documentService.listDocuments(" L100 ", 1L, 25L);

        ArgumentCaptor<List> captor = ArgumentCaptor.forClass(List.class);
        verify(adapter).send(captor.capture());
        assertEquals(1L, captor.getValue().getPage());
        assertEquals("L100", field(captor.getValue(), "_number"));
        assertEquals(2, result.length);
        assertEquals("contract.pdf", result[0].getFileName());
        assertEquals("Contract", result[0].getType());
        assertEquals("valuation.pdf", result[1].getFileName());
        assertEquals(2L, result[1].getId());
    }

    @Test
    void listDocumentsReturnsEmptyArrayWhenLoanHasNoDocuments() throws Exception {
        when(adapter.send(any(List.class))).thenReturn(new Status(1));

        assertEquals(0, documentService.listDocuments("L100", 1L, 25L).length);
    }

    @Test
    void listDocumentsRejectsMissingLoanNumberAndInvalidPaging() {
        ValidationException loanException = assertThrows(ValidationException.class,
                () -> documentService.listDocuments(" ", 1L, 25L));
        ValidationException invalidException = assertThrows(ValidationException.class,
                () -> documentService.listDocuments("L100", 0L, 0L));
        ValidationException missingException = assertThrows(ValidationException.class,
                () -> documentService.listDocuments("L100", null, null));

        assertEquals("Loan number cannot be null or empty",
                loanException.getResult().getError("loanNumber"));
        assertEquals("Page must be a positive integer",
                invalidException.getResult().getError("page"));
        assertEquals("Size must be a positive integer",
                invalidException.getResult().getError("size"));
        assertEquals("Page cannot be empty", missingException.getResult().getError("page"));
        assertEquals("Size cannot be empty", missingException.getResult().getError("size"));
        verifyNoInteractions(adapter);
    }

    @Test
    void listDocumentsThrowsLavsExceptionWhenMainframeRejectsRequest() throws IOException {
        when(adapter.send(any(List.class)))
                .thenReturn(new Status(500, "Document list rejected", 1));

        LavsException exception = assertThrows(LavsException.class,
                () -> documentService.listDocuments("L100", 1L, 25L));

        assertEquals("Document list rejected", exception.getMessage());
    }

    @Test
    void retrieveDocumentSendsRetrieveMessageAndReturnsIt() throws Exception {
        when(adapter.send(any(Retrieve.class))).thenReturn(new Status(1));

        Retrieve result = documentService.retrieveDocument(" L100 ", 7L);

        verify(adapter).send(same(result));
        assertEquals(7L, result.getId());
        assertEquals("L100", field(result, "_number"));
    }

    @Test
    void retrieveDocumentRejectsMissingOrInvalidIdentifiers() {
        ValidationException loanException = assertThrows(ValidationException.class,
                () -> documentService.retrieveDocument(null, 7L));
        ValidationException idException = assertThrows(ValidationException.class,
                () -> documentService.retrieveDocument("L100", -2L));

        assertEquals("Loan number cannot be null or empty",
                loanException.getResult().getError("loanNumber"));
        assertEquals("Document id must be a positive integer",
                idException.getResult().getError("documentId"));
        verifyNoInteractions(adapter);
    }

    @Test
    void retrieveDocumentPropagatesNetworkFailure() throws IOException {
        when(adapter.send(any(Retrieve.class)))
                .thenThrow(new IOException("connection unavailable"));

        IOException exception = assertThrows(IOException.class,
                () -> documentService.retrieveDocument("L100", 7L));

        assertEquals("connection unavailable", exception.getMessage());
    }
}
