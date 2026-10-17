package business.system.customer;

import business.lavsexceptions.LavsException;
import business.validation.ValidationException;
import dataaccess.systemadapters.MainframeAdapter;
import mainframe.client.Status;
import mainframe.client.messages.customer.AddEmployer;
import mainframe.client.messages.customer.DeleteEmployer;
import mainframe.client.messages.customer.UpdateEmployer;
import mainframe.client.messages.customerEmployer.List;
import mainframe.client.messages.customerEmployer.Retrieve;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.IOException;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.same;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CustomerEmployerServiceTest {
    @Mock
    private MainframeAdapter adapter;

    private CustomerEmployerService employerService;

    @BeforeEach
    void setUp() {
        employerService = new CustomerEmployerService(adapter);
    }

    @Test
    void constructorRejectsNullAdapter() {
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                () -> new CustomerEmployerService(null));

        assertEquals("Employer adapter cannot be null", exception.getMessage());
    }

    @Test
    void typeConstantsMatchTheMainframeValues() {
        assertEquals(AddEmployer.TYPE_PRIMARY, CustomerEmployerService.TYPE_PRIMARY);
        assertEquals(AddEmployer.TYPE_SECONDARY, CustomerEmployerService.TYPE_SECONDARY);
    }

    @Test
    void addEmployerSendsTrimmedValuesAndReturnsMessage() throws Exception {
        when(adapter.send(any(AddEmployer.class))).thenReturn(new Status(1));

        AddEmployer result = employerService.addEmployer(
                " C123 ", " Acme Ltd ", " Engineer ", 85000.0, AddEmployer.TYPE_PRIMARY);

        ArgumentCaptor<AddEmployer> captor = ArgumentCaptor.forClass(AddEmployer.class);
        verify(adapter).send(captor.capture());
        assertEquals(result, captor.getValue());
        assertEquals("C123", result.getCustomer());
        assertEquals("Acme Ltd", result.getName());
        assertEquals("Engineer", result.getPosition());
        assertEquals(85000.0, result.getSalary());
        assertEquals(AddEmployer.TYPE_PRIMARY, result.getType());
    }

    @Test
    void addEmployerAcceptsZeroSalary() throws Exception {
        when(adapter.send(any(AddEmployer.class))).thenReturn(new Status(1));

        AddEmployer result = employerService.addEmployer(
                "C123", "Food Bank", "Volunteer", 0.0, AddEmployer.TYPE_SECONDARY);

        assertEquals(0.0, result.getSalary());
        assertEquals(AddEmployer.TYPE_SECONDARY, result.getType());
    }

    @Test
    void addEmployerReportsAllInvalidFieldsTogether() {
        ValidationException exception = assertThrows(ValidationException.class,
                () -> employerService.addEmployer(" ", null, "", null, "Tertiary"));

        assertEquals("Customer cannot be empty", exception.getResult().getError("customer"));
        assertEquals("Employer name cannot be empty", exception.getResult().getError("name"));
        assertEquals("Position cannot be empty", exception.getResult().getError("position"));
        assertEquals("Salary cannot be empty", exception.getResult().getError("salary"));
        assertEquals("Employer type is invalid", exception.getResult().getError("type"));
        verifyNoInteractions(adapter);
    }

    @Test
    void addEmployerThrowsLavsExceptionWhenMainframeRejectsRequest() throws IOException {
        when(adapter.send(any(AddEmployer.class)))
                .thenReturn(new Status(400, "Employer rejected", 1));

        LavsException exception = assertThrows(LavsException.class,
                () -> employerService.addEmployer(
                        "C123", "Acme Ltd", "Engineer", 85000.0, AddEmployer.TYPE_PRIMARY));

        assertEquals("Employer rejected", exception.getMessage());
        assertEquals(400, exception.getErrorCode());
    }

    @Test
    void addEmployerPropagatesConnectionFailure() throws IOException {
        when(adapter.send(any(AddEmployer.class)))
                .thenThrow(new IOException("connection unavailable"));

        IOException exception = assertThrows(IOException.class,
                () -> employerService.addEmployer(
                        "C123", "Acme Ltd", "Engineer", 85000.0, AddEmployer.TYPE_PRIMARY));

        assertEquals("connection unavailable", exception.getMessage());
    }

    @Test
    void updateEmployerSendsTrimmedValuesAndReturnsMessage() throws Exception {
        when(adapter.send(any(UpdateEmployer.class))).thenReturn(new Status(2));

        UpdateEmployer result = employerService.updateEmployer(
                " C123 ", 4L, " Acme Ltd ", " Manager ", 95000.5, UpdateEmployer.TYPE_SECONDARY);

        ArgumentCaptor<UpdateEmployer> captor = ArgumentCaptor.forClass(UpdateEmployer.class);
        verify(adapter).send(captor.capture());
        assertEquals(result, captor.getValue());
        assertEquals("C123", result.getCustomer());
        assertEquals(4L, result.getId());
        assertEquals("Acme Ltd", result.getName());
        assertEquals("Manager", result.getPosition());
        assertEquals(95000.5, result.getSalary());
        assertEquals(UpdateEmployer.TYPE_SECONDARY, result.getType());
    }

    @Test
    void updateEmployerRejectsMissingOrInvalidId() {
        ValidationException missingIdException = assertThrows(ValidationException.class,
                () -> employerService.updateEmployer(
                        "C123", null, "Acme Ltd", "Engineer", 85000.0, AddEmployer.TYPE_PRIMARY));
        ValidationException invalidIdException = assertThrows(ValidationException.class,
                () -> employerService.updateEmployer(
                        "C123", 0L, "Acme Ltd", "Engineer", 85000.0, AddEmployer.TYPE_PRIMARY));

        assertEquals("Employer id cannot be empty",
                missingIdException.getResult().getError("employerId"));
        assertEquals("Employer id must be a positive integer",
                invalidIdException.getResult().getError("employerId"));
        verifyNoInteractions(adapter);
    }

    @Test
    void updateEmployerRejectsNegativeSalary() {
        ValidationException exception = assertThrows(ValidationException.class,
                () -> employerService.updateEmployer(
                        "C123", 4L, "Acme Ltd", "Engineer", -1.0, AddEmployer.TYPE_PRIMARY));

        assertEquals("Salary must be a non-negative number",
                exception.getResult().getError("salary"));
        verifyNoInteractions(adapter);
    }

    @Test
    void updateEmployerThrowsLavsExceptionWhenMainframeRejectsRequest() throws IOException {
        when(adapter.send(any(UpdateEmployer.class)))
                .thenReturn(new Status(404, "Employer not found", 2));

        LavsException exception = assertThrows(LavsException.class,
                () -> employerService.updateEmployer(
                        "C123", 4L, "Acme Ltd", "Engineer", 85000.0, AddEmployer.TYPE_PRIMARY));

        assertEquals("Employer not found", exception.getMessage());
        assertEquals(404, exception.getErrorCode());
    }

    @Test
    void deleteEmployerSendsTrimmedValuesAndReturnsMessage() throws Exception {
        when(adapter.send(any(DeleteEmployer.class))).thenReturn(new Status(3));

        DeleteEmployer result = employerService.deleteEmployer(" C123 ", 4L);

        ArgumentCaptor<DeleteEmployer> captor = ArgumentCaptor.forClass(DeleteEmployer.class);
        verify(adapter).send(captor.capture());
        assertEquals(result, captor.getValue());
        assertEquals("C123", result.getCustomer());
        assertEquals(4L, result.getId());
    }

    @Test
    void deleteEmployerRejectsMissingOrInvalidIdentifiers() {
        ValidationException missingCustomerException = assertThrows(ValidationException.class,
                () -> employerService.deleteEmployer(null, 4L));
        ValidationException blankCustomerException = assertThrows(ValidationException.class,
                () -> employerService.deleteEmployer(" ", 4L));
        ValidationException missingIdException = assertThrows(ValidationException.class,
                () -> employerService.deleteEmployer("C123", null));
        ValidationException invalidIdException = assertThrows(ValidationException.class,
                () -> employerService.deleteEmployer("C123", -3L));

        assertEquals("Customer number cannot be null or empty",
                missingCustomerException.getResult().getError("customerNumber"));
        assertEquals("Customer number cannot be null or empty",
                blankCustomerException.getResult().getError("customerNumber"));
        assertEquals("Employer id cannot be empty",
                missingIdException.getResult().getError("employerId"));
        assertEquals("Employer id must be a positive integer",
                invalidIdException.getResult().getError("employerId"));
        verifyNoInteractions(adapter);
    }

    @Test
    void deleteEmployerThrowsLavsExceptionWhenMainframeRejectsRequest() throws IOException {
        when(adapter.send(any(DeleteEmployer.class)))
                .thenReturn(new Status(404, "Employer not found", 3));

        LavsException exception = assertThrows(LavsException.class,
                () -> employerService.deleteEmployer("C123", 4L));

        assertEquals("Employer not found", exception.getMessage());
    }

    @Test
    void listEmployersSendsListMessageAndReturnsItems() throws Exception {
        when(adapter.send(any(List.class))).thenReturn(new Status(1));

        List.Item[] result = employerService.listEmployers(" C123 ", 1L, 25L);

        ArgumentCaptor<List> captor = ArgumentCaptor.forClass(List.class);
        verify(adapter).send(captor.capture());
        assertEquals(1L, captor.getValue().getPage());
        assertEquals(0, result.length);
    }

    @Test
    void listEmployersRejectsMissingCustomerNumberAndInvalidPaging() {
        ValidationException customerException = assertThrows(ValidationException.class,
                () -> employerService.listEmployers(null, 1L, 25L));
        ValidationException invalidException = assertThrows(ValidationException.class,
                () -> employerService.listEmployers("C123", 0L, -1L));
        ValidationException missingException = assertThrows(ValidationException.class,
                () -> employerService.listEmployers("C123", null, null));

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
    void listEmployersThrowsLavsExceptionWhenMainframeRejectsRequest() throws IOException {
        when(adapter.send(any(List.class)))
                .thenReturn(new Status(500, "Employer list rejected", 1));

        LavsException exception = assertThrows(LavsException.class,
                () -> employerService.listEmployers("C123", 1L, 25L));

        assertEquals("Employer list rejected", exception.getMessage());
    }

    @Test
    void retrieveEmployerSendsRetrieveMessageAndReturnsIt() throws Exception {
        when(adapter.send(any(Retrieve.class))).thenReturn(new Status(1));

        Retrieve result = employerService.retrieveEmployer(" C123 ", 4L);

        assertEquals(4L, result.getId());
        verify(adapter).send(same(result));
    }

    @Test
    void retrieveEmployerRejectsMissingOrInvalidIdentifiers() {
        ValidationException customerException = assertThrows(ValidationException.class,
                () -> employerService.retrieveEmployer(" ", 4L));
        ValidationException idException = assertThrows(ValidationException.class,
                () -> employerService.retrieveEmployer("C123", 0L));

        assertEquals("Customer number cannot be null or empty",
                customerException.getResult().getError("customerNumber"));
        assertEquals("Employer id must be a positive integer",
                idException.getResult().getError("employerId"));
        verifyNoInteractions(adapter);
    }

    @Test
    void retrieveEmployerPropagatesNetworkFailure() throws IOException {
        when(adapter.send(any(Retrieve.class)))
                .thenThrow(new IOException("connection unavailable"));

        IOException exception = assertThrows(IOException.class,
                () -> employerService.retrieveEmployer("C123", 4L));

        assertEquals("connection unavailable", exception.getMessage());
    }

    @Test
    void validateEmployerDetailsAcceptsBothTypes() {
        assertDoesNotThrow(() -> CustomerEmployerService.validateEmployerDetails(
                "C123", "Acme Ltd", "Engineer", 85000.0, CustomerEmployerService.TYPE_PRIMARY));
        assertDoesNotThrow(() -> CustomerEmployerService.validateEmployerDetails(
                "C123", "Cafe", "Barista", 20000.0, CustomerEmployerService.TYPE_SECONDARY));
    }

    @Test
    void validateEmployerDetailsRejectsNonFiniteSalaries() {
        for (double salary : new double[] {Double.NaN, Double.POSITIVE_INFINITY}) {
            ValidationException exception = assertThrows(ValidationException.class,
                    () -> CustomerEmployerService.validateEmployerDetails(
                            "C123", "Acme Ltd", "Engineer", salary,
                            CustomerEmployerService.TYPE_PRIMARY));

            assertEquals("Salary must be a non-negative number",
                    exception.getResult().getError("salary"));
        }
    }

    @Test
    void validateEmployerDetailsRequiresType() {
        ValidationException exception = assertThrows(ValidationException.class,
                () -> CustomerEmployerService.validateEmployerDetails(
                        "C123", "Acme Ltd", "Engineer", 85000.0, null));

        assertEquals("Employer type cannot be empty", exception.getResult().getError("type"));
    }
}
