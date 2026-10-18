package business.system;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import business.lavsexceptions.LavsException;
import business.validation.ValidationException;
import dataaccess.systemadapters.MainframeAdapter;
import mainframe.client.Status;
import mainframe.client.messages.loan.AddPayment;
import mainframe.client.messages.loan.DeletePayment;

@ExtendWith(MockitoExtension.class)
class PaymentTest {
    @Mock
    private MainframeAdapter adapter;

    private Payment payment;

    @BeforeEach
    void setUp() {
        payment = new Payment(adapter);
    }

    @Test
    void constructorRejectsNullAdapter() {
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                () -> new Payment(null));

        assertEquals("Payment adapter cannot be null", exception.getMessage());
    }

    @Test
    void addPaymentSendsTrimmedMessage() throws Exception {
        when(adapter.send(any(AddPayment.class))).thenReturn(new Status(1L));

        AddPayment result = payment.addPayment(100.0, " 2025-12-24 ", " L123 ");

        assertEquals(100.0, result.getAmountPaid());
        assertEquals("2025-12-24", result.getDate());
        assertEquals("L123", result.getLoanNumber());
        verify(adapter).send(result);
    }

    @Test
    void deletePaymentSendsTrimmedMessage() throws Exception {
        when(adapter.send(any(DeletePayment.class))).thenReturn(new Status(1L));

        DeletePayment result = payment.deletePayment(4L, " L123 ");

        assertEquals(4L, result.getId());
        assertEquals("L123", result.getLoanNumber());
        verify(adapter).send(result);
    }

    @Test
    void addPaymentRejectsInvalidFields() {
        ValidationException exception = assertThrows(ValidationException.class,
                () -> payment.addPayment(Double.NaN, "not-a-date", " "));

        assertEquals("Payment amount must be finite and greater than zero",
                exception.getResult().getError("amountPaid"));
        assertEquals("Payment date must be in yyyy-MM-dd format",
                exception.getResult().getError("date"));
        assertEquals("Loan number cannot be null or empty",
                exception.getResult().getError("loanNumber"));
        verifyNoInteractions(adapter);
    }

    @Test
    void deletePaymentRejectsInvalidFields() {
        ValidationException exception = assertThrows(ValidationException.class,
                () -> payment.deletePayment(0L, ""));

        assertEquals("Payment id must be a positive integer",
                exception.getResult().getError("id"));
        assertEquals("Loan number cannot be null or empty",
                exception.getResult().getError("loanNumber"));
        verifyNoInteractions(adapter);
    }

    @Test
    void addPaymentRejectsMainframeFailure() throws Exception {
        when(adapter.send(any(AddPayment.class)))
                .thenReturn(new Status(500, "Payment rejected", 1L));

        assertThrows(LavsException.class,
                () -> payment.addPayment(100.0, "2025-12-24", "L123"));
    }
}
