package business.system;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import static org.mockito.ArgumentMatchers.any;
import org.mockito.Mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import org.mockito.junit.jupiter.MockitoExtension;

import business.lavsexceptions.LavsException;
import business.validation.ValidationException;
import dataaccess.systemadapters.MainframeAdapter;
import mainframe.client.Status;
import mainframe.client.messages.loan.AddCoborrower;
import mainframe.client.messages.loan.DeleteCoborrower;

@ExtendWith(MockitoExtension.class)
class CoborrowerTest {
    @Mock
    private MainframeAdapter adapter;

    private Coborrower coborrower;

    @BeforeEach
    void setUp() {
        coborrower = new Coborrower(adapter);
    }

    @Test
    void addCoborrowerSendsTrimmedValuesAndReturnsMessage() throws Exception {
        when(adapter.send(any(AddCoborrower.class))).thenReturn(new Status(1));

        AddCoborrower result = coborrower.addCoborrower(
                "  C123  ",
                "  L456  ",
                "  Coborrower  ");

        ArgumentCaptor<AddCoborrower> captor = ArgumentCaptor.forClass(AddCoborrower.class);
        verify(adapter).send(captor.capture());
        assertEquals("C123", captor.getValue().getCoborrowerNumber());
        assertEquals("L456", captor.getValue().getLoanNumber());
        assertEquals("Coborrower", captor.getValue().getType());
        assertEquals(result, captor.getValue());
    }

    @Test
    void addCoborrowerAcceptsGuarantorType() throws Exception {
        when(adapter.send(any(AddCoborrower.class))).thenReturn(new Status(1));

        AddCoborrower result = coborrower.addCoborrower(
                "C123", "L456", AddCoborrower.TYPE_GUARANTOR);

        assertEquals(AddCoborrower.TYPE_GUARANTOR, result.getType());
    }

    @Test
    void addCoborrowerRejectsBlankOrInvalidDetails() {
        ValidationException emptyFieldsException = assertThrows(ValidationException.class, () ->
                coborrower.addCoborrower("", "  ", ""));
        assertEquals("Coborrower number cannot be null or empty",
                emptyFieldsException.getResult().getError("coborrowerNumber"));
        assertEquals("Loan number cannot be null or empty",
                emptyFieldsException.getResult().getError("loanNumber"));
        assertEquals("Coborrower type cannot be null or empty",
                emptyFieldsException.getResult().getError("type"));

        ValidationException invalidTypeException = assertThrows(ValidationException.class, () ->
                coborrower.addCoborrower("C123", "L456", "InvalidType"));
        assertEquals("Coborrower type is invalid",
                invalidTypeException.getResult().getError("type"));

        verifyNoInteractions(adapter);
    }

    @Test
    void addCoborrowerThrowsLavsExceptionWhenMainframeRejects() throws Exception {
        when(adapter.send(any(AddCoborrower.class))).thenReturn(new Status(500, "Rejected", 99L));

        LavsException exception = assertThrows(LavsException.class, () ->
                coborrower.addCoborrower("C123", "L456", "Coborrower"));

        assertEquals("Rejected", exception.getMessage());
    }

    @Test
    void deleteCoborrowerSendsValuesAndReturnsMessage() throws Exception {
        when(adapter.send(any(DeleteCoborrower.class))).thenReturn(new Status(1));

        DeleteCoborrower result = coborrower.deleteCoborrower(42L, "  L456  ");

        ArgumentCaptor<DeleteCoborrower> captor = ArgumentCaptor.forClass(DeleteCoborrower.class);
        verify(adapter).send(captor.capture());
        assertEquals(42L, captor.getValue().getId());
        assertEquals("L456", captor.getValue().getLoanNumber());
        assertEquals(result, captor.getValue());
    }

    @Test
    void deleteCoborrowerRejectsInvalidIdOrLoanNumber() {
        ValidationException nullIdException = assertThrows(ValidationException.class, () ->
                coborrower.deleteCoborrower(null, "L456"));
        assertEquals("Coborrower id cannot be empty",
                nullIdException.getResult().getError("id"));

        ValidationException negativeIdException = assertThrows(ValidationException.class, () ->
                coborrower.deleteCoborrower(-1L, "L456"));
        assertEquals("Coborrower id must be a positive integer",
                negativeIdException.getResult().getError("id"));

        ValidationException blankLoanException = assertThrows(ValidationException.class, () ->
                coborrower.deleteCoborrower(1L, "   "));
        assertEquals("Loan number cannot be null or empty",
                blankLoanException.getResult().getError("loanNumber"));

        verifyNoInteractions(adapter);
    }

    @Test
    void deleteCoborrowerThrowsLavsExceptionWhenMainframeRejects() throws Exception {
        when(adapter.send(any(DeleteCoborrower.class))).thenReturn(new Status(500, "Deletion failed", 99L));

        LavsException exception = assertThrows(LavsException.class, () ->
                coborrower.deleteCoborrower(1L, "L456"));

        assertEquals("Deletion failed", exception.getMessage());
    }
}
