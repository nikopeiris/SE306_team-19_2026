package business.system.customer;

import business.lavsexceptions.LavsException;
import business.validation.ValidationException;
import dataaccess.systemadapters.MainframeAdapter;
import mainframe.client.Status;
import mainframe.client.messages.customer.AddNote;
import mainframe.client.messages.customer.DeleteNote;
import mainframe.client.messages.customer.UpdateNote;
import mainframe.client.messages.customerNote.List;
import mainframe.client.messages.customerNote.Retrieve;
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
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.same;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CustomerNoteServiceTest {
    @Mock
    private MainframeAdapter adapter;

    private CustomerNoteService noteService;

    @BeforeEach
    void setUp() {
        noteService = new CustomerNoteService(adapter);
    }

    // makes the stubbed list request return one note per type given
    private void stubNoteList(String... types) throws Exception {
        when(adapter.send(any(List.class))).thenAnswer(invocation -> {
            List listMessage = invocation.getArgument(0);
            Field dataField = List.class.getDeclaredField("_data");
            dataField.setAccessible(true);
            @SuppressWarnings("unchecked")
            ArrayList<List.Item> items = (ArrayList<List.Item>) dataField.get(listMessage);
            long id = 1L;
            for (String type : types) {
                items.add(listMessage.new Item("test", "2026-10-20", id++, "2", type));
            }
            return new Status(0, null, 1L);
        });
    }

    @Test
    void constructorRejectsNullAdapter() {
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                () -> new CustomerNoteService(null));

        assertEquals("Note adapter cannot be null", exception.getMessage());
    }

    @Test
    void addNoteSendsTrimmedValuesAndKeepsLineBreaks() throws Exception {
        when(adapter.send(any(AddNote.class))).thenReturn(new Status(1));

        AddNote result = noteService.addNote(" C123 ", "  Line 1\nLine 2\n ", " General ");

        ArgumentCaptor<AddNote> captor = ArgumentCaptor.forClass(AddNote.class);
        verify(adapter).send(captor.capture());
        assertEquals(result, captor.getValue());
        assertEquals("C123", result.getCustomer());
        assertEquals("Line 1\nLine 2", result.getLines());
        assertEquals("General", result.getType());
    }

    @Test
    void addNoteRejectsInvalidDetailsWithoutContactingMainframe() {
        ValidationException exception = assertThrows(ValidationException.class,
                () -> noteService.addNote(null, " \n ", ""));

        assertEquals("Customer cannot be empty", exception.getResult().getError("customer"));
        assertEquals("Note cannot be empty", exception.getResult().getError("lines"));
        assertEquals("Note type cannot be empty", exception.getResult().getError("type"));
        verifyNoInteractions(adapter);
    }

    @Test
    void addNoteThrowsLavsExceptionWhenMainframeRejectsRequest() throws IOException {
        when(adapter.send(any(AddNote.class)))
                .thenReturn(new Status(400, "Note rejected", 1));

        LavsException exception = assertThrows(LavsException.class,
                () -> noteService.addNote("C123", "Line 1", "General"));

        assertEquals("Note rejected", exception.getMessage());
        assertEquals(400, exception.getErrorCode());
    }

    @Test
    void addNotePropagatesNetworkFailure() throws IOException {
        when(adapter.send(any(AddNote.class)))
                .thenThrow(new IOException("connection unavailable"));

        IOException exception = assertThrows(IOException.class,
                () -> noteService.addNote("C123", "Line 1", "General"));

        assertEquals("connection unavailable", exception.getMessage());
    }

    @Test
    void updateNoteSendsTrimmedValuesAndReturnsMessage() throws Exception {
        when(adapter.send(any(UpdateNote.class))).thenReturn(new Status(1));

        UpdateNote result = noteService.updateNote(
                " C123 ", 7L, " First Line\nSecond Line ", " Follow up ");

        verify(adapter).send(same(result));
        assertEquals("C123", result.getCustomer());
        assertEquals(7L, result.getId());
        assertEquals("First Line\nSecond Line", result.getLines());
        assertEquals("Follow up", result.getType());
    }

    @Test
    void updateNoteRejectsMissingOrInvalidId() {
        ValidationException missingException = assertThrows(ValidationException.class,
                () -> noteService.updateNote("C123", null, "Line 1", "General"));
        ValidationException invalidException = assertThrows(ValidationException.class,
                () -> noteService.updateNote("C123", 0L, "Line 1", "General"));

        assertEquals("Note id cannot be empty", missingException.getResult().getError("noteId"));
        assertEquals("Note id must be a positive integer",
                invalidException.getResult().getError("noteId"));
        verifyNoInteractions(adapter);
    }

    @Test
    void updateNoteRejectsInvalidDetailsWithoutContactingMainframe() {
        ValidationException exception = assertThrows(ValidationException.class,
                () -> noteService.updateNote(" ", -1L, null, null));

        assertEquals("Customer cannot be empty", exception.getResult().getError("customer"));
        assertEquals("Note id must be a positive integer", exception.getResult().getError("noteId"));
        assertEquals("Note cannot be empty", exception.getResult().getError("lines"));
        assertEquals("Note type cannot be empty", exception.getResult().getError("type"));
        verifyNoInteractions(adapter);
    }

    @Test
    void updateNoteThrowsLavsExceptionWhenMainframeRejectsRequest() throws IOException {
        when(adapter.send(any(UpdateNote.class)))
                .thenReturn(new Status(404, "Note not found", 1));

        LavsException exception = assertThrows(LavsException.class,
                () -> noteService.updateNote("C123", 7L, "Line 1", "General"));

        assertEquals("Note not found", exception.getMessage());
        assertEquals(404, exception.getErrorCode());
    }

    @Test
    void deleteNoteSendsTrimmedCustomerNumber() throws Exception {
        when(adapter.send(any(DeleteNote.class))).thenReturn(new Status(1));

        DeleteNote result = noteService.deleteNote(" C123 ", 7L);

        verify(adapter).send(same(result));
        assertEquals("C123", result.getCustomer());
        assertEquals(7L, result.getId());
    }

    @Test
    void deleteNoteRejectsMissingOrInvalidIdentifiers() {
        ValidationException nullCustomer = assertThrows(ValidationException.class,
                () -> noteService.deleteNote(null, 7L));
        ValidationException blankCustomer = assertThrows(ValidationException.class,
                () -> noteService.deleteNote(" ", 7L));
        ValidationException nullId = assertThrows(ValidationException.class,
                () -> noteService.deleteNote("C123", null));
        ValidationException zeroId = assertThrows(ValidationException.class,
                () -> noteService.deleteNote("C123", 0L));

        assertEquals("Customer number cannot be null or empty",
                nullCustomer.getResult().getError("customerNumber"));
        assertEquals("Customer number cannot be null or empty",
                blankCustomer.getResult().getError("customerNumber"));
        assertEquals("Note id cannot be empty", nullId.getResult().getError("noteId"));
        assertEquals("Note id must be a positive integer", zeroId.getResult().getError("noteId"));
        verifyNoInteractions(adapter);
    }

    @Test
    void deleteNoteThrowsLavsExceptionWhenMainframeRejectsRequest() throws IOException {
        when(adapter.send(any(DeleteNote.class)))
                .thenReturn(new Status(404, "Note not found", 1));

        LavsException exception = assertThrows(LavsException.class,
                () -> noteService.deleteNote("C123", 7L));

        assertEquals("Note not found", exception.getMessage());
    }

    @Test
    void listNotesSendsListMessageAndReturnsItems() throws Exception {
        stubNoteList("General", "Follow up");

        List.Item[] result = noteService.listNotes(" C123 ", 1L, 25L);

        ArgumentCaptor<List> captor = ArgumentCaptor.forClass(List.class);
        verify(adapter).send(captor.capture());
        assertEquals(1L, captor.getValue().getPage());
        assertEquals(2, result.length);
        assertEquals("General", result[0].getType());
        assertEquals("test", result[0].getAuthor());
        assertEquals("Follow up", result[1].getType());
        assertEquals(2L, result[1].getId());
    }

    @Test
    void listNotesReturnsEmptyArrayWhenCustomerHasNoNotes() throws Exception {
        when(adapter.send(any(List.class))).thenReturn(new Status(1));

        assertEquals(0, noteService.listNotes("C123", 1L, 25L).length);
    }

    @Test
    void listNotesRejectsMissingCustomerNumberAndInvalidPaging() {
        ValidationException customerException = assertThrows(ValidationException.class,
                () -> noteService.listNotes(" ", 1L, 25L));
        ValidationException invalidException = assertThrows(ValidationException.class,
                () -> noteService.listNotes("C123", 0L, 0L));
        ValidationException missingException = assertThrows(ValidationException.class,
                () -> noteService.listNotes("C123", null, null));

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
    void listNotesThrowsLavsExceptionWhenMainframeRejectsRequest() throws IOException {
        when(adapter.send(any(List.class)))
                .thenReturn(new Status(500, "Note list rejected", 1));

        LavsException exception = assertThrows(LavsException.class,
                () -> noteService.listNotes("C123", 1L, 25L));

        assertEquals("Note list rejected", exception.getMessage());
    }

    @Test
    void retrieveNoteSendsRetrieveMessageAndReturnsIt() throws Exception {
        when(adapter.send(any(Retrieve.class))).thenReturn(new Status(1));

        Retrieve result = noteService.retrieveNote(" C123 ", 7L);

        assertEquals(7L, result.getId());
        verify(adapter).send(same(result));
    }

    @Test
    void retrieveNoteRejectsMissingOrInvalidIdentifiers() {
        ValidationException customerException = assertThrows(ValidationException.class,
                () -> noteService.retrieveNote(null, 7L));
        ValidationException idException = assertThrows(ValidationException.class,
                () -> noteService.retrieveNote("C123", -2L));

        assertEquals("Customer number cannot be null or empty",
                customerException.getResult().getError("customerNumber"));
        assertEquals("Note id must be a positive integer",
                idException.getResult().getError("noteId"));
        verifyNoInteractions(adapter);
    }

    @Test
    void retrieveNotePropagatesNetworkFailure() throws IOException {
        when(adapter.send(any(Retrieve.class)))
                .thenThrow(new IOException("connection unavailable"));

        IOException exception = assertThrows(IOException.class,
                () -> noteService.retrieveNote("C123", 7L));

        assertEquals("connection unavailable", exception.getMessage());
    }

    @Test
    void validateNoteDetailsAcceptsValidNote() {
        assertDoesNotThrow(() -> CustomerNoteService.validateNoteDetails("Line 1\nLine 2", "General"));
    }

    @Test
    void validateNoteDetailsRejectsMissingFields() {
        ValidationException exception = assertThrows(ValidationException.class,
                () -> CustomerNoteService.validateNoteDetails(" ", null));

        assertEquals("Note cannot be empty", exception.getResult().getError("lines"));
        assertEquals("Note type cannot be empty", exception.getResult().getError("type"));
    }
}
