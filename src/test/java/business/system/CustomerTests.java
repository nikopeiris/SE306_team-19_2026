package business.system;

import business.lavsexceptions.LavsException;
import business.validation.ValidationException;
import mainframe.client.Status;
import mainframe.client.messages.customer.Create;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

class CustomerTests {
    @Test
    void addCustomerValidatesFieldsBeforeCallingAdapter() {
        AtomicReference<Create> sentMessage = new AtomicReference<>();
        Customer customer = new Customer(message -> {
            sentMessage.set(message);
            return new Status(1);
        });

        ValidationException exception = assertThrows(
                ValidationException.class,
                () -> customer.addCustomer(null, "Doe", "Jane", "NZ", 1L, "Ms"));

        assertEquals("Date of birth cannot be null or empty", exception.getMessage());
        assertEquals(null, sentMessage.get());
    }

    @Test
    void addCustomerRejectsBlankFieldsAndInvalidOrganisation() {
        Customer customer = new Customer(message -> new Status(1));

        ValidationException exception = assertThrows(
                ValidationException.class,
                () -> customer.addCustomer(" ", "", "Jane", "NZ", 0L, null));

        assertEquals(
                "Date of birth cannot be null or empty\n"
                        + "Family name cannot be null or empty\n"
                        + "Title cannot be null or empty\n"
                        + "Organisation must be a positive integer",
                exception.getMessage());
    }

    @Test
    void addCustomerDelegatesAndReturnsCreatedMessage() throws IOException, LavsException {
        AtomicReference<Create> sentMessage = new AtomicReference<>();
        Customer customer = new Customer(message -> {
            sentMessage.set(message);
            return new Status(1);
        });

        Create result = customer.addCustomer(
                "2001-02-03", " Doe ", " Jane ", " New Zealand ", 1L, " Ms ");

        assertSame(result, sentMessage.get());
        assertEquals("Doe", result.getFamilyName());
        assertEquals("Jane", result.getFirstName());
    }

    @Test
    void addCustomerConvertsMainframeFailureToLavsException() {
        Customer customer = new Customer(
                message -> new Status(400, "Customer could not be created", 2));

        LavsException exception = assertThrows(
                LavsException.class,
                () -> customer.addCustomer(
                        "2001-02-03", "Doe", "Jane", "NZ", 1L, "Ms"));

        assertEquals("Customer could not be created", exception.getMessage());
        assertEquals(400, exception.getErrorCode());
    }
}
