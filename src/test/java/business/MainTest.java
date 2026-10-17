package business;

import java.lang.reflect.Field;
import java.util.HashMap;
import java.util.Map;

import org.junit.jupiter.api.AfterEach;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import static org.mockito.Answers.CALLS_REAL_METHODS;
import static org.mockito.ArgumentMatchers.any;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedStatic;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import business.lavsexceptions.LavsException;
import dataaccess.systemadapters.MainframeAdapter;
import mainframe.client.Message;
import mainframe.client.Status;
import mainframe.client.messages.customer.Retrieve;
import mainframe.client.messages.customer.Search;
import mainframe.client.messages.customerAddress.List;

class MainTest {
    private Main main;
    private MainframeAdapter adapter;
    private MockedStatic<Main> mockedMain;

    @BeforeEach
    void setUp() throws Exception {
        Map<String, String> env = new HashMap<>();
        env.put("ADMIN_USERNAME", "admin");
        env.put("ADMIN_PASSWORD", "secret");

        mockedMain = mockStatic(Main.class, CALLS_REAL_METHODS);
        mockedMain.when(Main::loadEnv).thenReturn(env);

        main = new Main();
        adapter = mock(MainframeAdapter.class);
        injectField(main, "adapter", adapter);
    }

    @AfterEach
    void tearDown() {
        mockedMain.close();
    }

    @Test
    void loginSetsUsernameOnSuccessfulAuthentication() throws Exception {
        when(adapter.login("alice", "secret")).thenReturn(new Status(1));

        assertTrue(main.login("alice", "secret"));
        assertEquals("alice", main.getUserName());
    }

    @Test
    void loginThrowsWhenAuthenticationFails() throws Exception {
        when(adapter.login("alice", "wrong")).thenReturn(new Status(401, "Invalid credentials", 99));

        LavsException exception = assertThrows(LavsException.class,
                () -> main.login("alice", "wrong"));

        assertEquals("Invalid credentials", exception.getMessage());
        assertNull(main.getUserName());
    }

    @Test
    void signupCreatesUserAndLogsInAsNewUser() throws Exception {

        when(adapter.login("admin", "secret")).thenReturn(new Status(1));
        when(adapter.send(any(Message.class))).thenReturn(new Status(2));
        when(adapter.login("newuser", "s3cr3t")).thenReturn(new Status(1));

        assertTrue(main.signup("newuser", "New User", "s3cr3t", 42L, 2L));
        assertEquals("newuser", main.getUserName());

        verify(adapter).login("admin", "secret");
        verify(adapter).send(any(Message.class));
        verify(adapter).login("newuser", "s3cr3t");
    }

    @Test
    void logOutClearsTheCurrentUserAndDelegatesToAdapter() throws Exception {
        injectField(main, "userName", "current-user");

        assertTrue(main.logOut());
        assertNull(main.getUserName());
        verify(adapter).logOut();
    }

    @Test
    void addCustomerDelegatesToTheCustomerService() throws Exception {
        when(adapter.send(any(Message.class))).thenReturn(new Status(1));

        assertTrue(main.addCustomer(
                "2001-01-01",
                "Doe",
                "Jane",
                "NZ",
                7L,
                "Ms"));

        verify(adapter).send(any(Message.class));
    }

    @Test
    void addAddressDelegatesToTheCustomerService() throws Exception {
        when(adapter.send(any(Message.class))).thenReturn(new Status(1));

        assertTrue(main.addAddress(
                "Auckland",
                "New Zealand",
                "C123",
                true,
                true,
                "1 Main St",
                "Unit 2",
                null,
                "1010",
                "CBD",
                "Residential"));

        verify(adapter).send(any(Message.class));
    }

    @Test
    void updateAddressDelegatesToTheCustomerService() throws Exception {
        when(adapter.send(any(Message.class))).thenReturn(new Status(1));

        assertTrue(main.updateAddress(
                "Auckland",
                "New Zealand",
                "C123",
                17L,
                true,
                true,
                "1 Main St",
                "Unit 2",
                null,
                "1010",
                "CBD",
                "Residential"));

        verify(adapter).send(any(Message.class));
    }

    @Test
    void deleteAddressDelegatesToTheCustomerService() throws Exception {
        when(adapter.send(any(Message.class))).thenReturn(new Status(1));

        assertTrue(main.deleteAddress("C123", 17L));

        verify(adapter).send(any(Message.class));
    }

    @Test
    void setLoanIdSetsCurrentLoanId() throws Exception {
        main.setLoanId("L123");

        Field field = Main.class.getDeclaredField("currentLoanId");
        field.setAccessible(true);
        assertEquals("L123", field.get(main));
    }

    @Test
    void listLoansDelegatesToLoanService() throws Exception {
        when(adapter.send(any(Message.class))).thenReturn(new Status(1));

        mainframe.client.messages.loan.List.Item[] items = main.listLoans("C123", 1, 20);

        verify(adapter).send(any(mainframe.client.messages.loan.List.class));
        assertEquals(0, items.length);
    }

    @Test
    void getLoanDelegatesToLoanServiceUsingCurrentLoanId() throws Exception {
        when(adapter.send(any(Message.class))).thenReturn(new Status(1));
        main.setLoanId("L123");

        mainframe.client.messages.loan.Retrieve result = main.getLoan();

        ArgumentCaptor<mainframe.client.messages.loan.Retrieve> captor =
                ArgumentCaptor.forClass(mainframe.client.messages.loan.Retrieve.class);
        verify(adapter).send(captor.capture());
        assertEquals(result, captor.getValue());
    }

    @Test
    void getLoanRepaymentScheduleDelegatesToLoanServiceUsingCurrentLoanId() throws Exception {
        when(adapter.send(any(Message.class))).thenReturn(new Status(1));
        main.setLoanId("L123");

        mainframe.client.messages.loanRepaymentSchedule.List.Item[] result =
                main.getLoanRepaymentSchedule(1, 25);

        verify(adapter).send(any(mainframe.client.messages.loanRepaymentSchedule.List.class));
        assertEquals(0, result.length);
    }

    @Test
    void getLoanSummaryDelegatesToLoanServiceUsingCurrentLoanId() throws Exception {
        when(adapter.send(any(Message.class))).thenReturn(new Status(1));
        main.setLoanId("L123");

        mainframe.client.messages.loanSummary.Retrieve result = main.getLoanSummary();

        ArgumentCaptor<mainframe.client.messages.loanSummary.Retrieve> captor =
                ArgumentCaptor.forClass(mainframe.client.messages.loanSummary.Retrieve.class);
        verify(adapter).send(captor.capture());
        assertEquals(result, captor.getValue());
    }

    @Test
    void listCustomersDelegatesToCustomerService() throws Exception {
        when(adapter.send(any(Message.class))).thenReturn(new Status(1));

        Search.Item[] result = main.listCustomers("Smith", 1, 10);

        verify(adapter).send(any(Search.class));
        assertEquals(0, result.length);
    }

    @Test
    void getCustomerDelegatesToCustomerService() throws Exception {
        when(adapter.send(any(Message.class))).thenReturn(new Status(1));

        Retrieve result = main.getCustomer("C123");

        ArgumentCaptor<Retrieve> captor = ArgumentCaptor.forClass(Retrieve.class);
        verify(adapter).send(captor.capture());
        assertEquals(result, captor.getValue());
    }

    @Test
    void listCustomerAddressesDelegatesToCustomerService() throws Exception {
        when(adapter.send(any(Message.class))).thenReturn(new Status(1));

        List.Item[] result = main.listCustomerAddresses("C123", 1, 10);

        verify(adapter).send(any(List.class));
        assertEquals(0, result.length);
    }

    @Test
    void getAddressDelegatesToCustomerService() throws Exception {
        when(adapter.send(any(Message.class))).thenReturn(new Status(1));

        mainframe.client.messages.customerAddress.Retrieve result = main.getAddress("C123", 1L);

        ArgumentCaptor<mainframe.client.messages.customerAddress.Retrieve> captor =
                ArgumentCaptor.forClass(mainframe.client.messages.customerAddress.Retrieve.class);
        verify(adapter).send(captor.capture());
        assertEquals(result, captor.getValue());
    }

    @Test
    void listPhoneNumbersDelegatesToCustomerService() throws Exception {
        when(adapter.send(any(Message.class))).thenReturn(new Status(1));

        mainframe.client.messages.customerPhoneNumber.List.Item[] result =
                main.listPhoneNumbers("C123", 1, 10);

        verify(adapter).send(any(mainframe.client.messages.customerPhoneNumber.List.class));
        assertEquals(0, result.length);
    }

    @Test
    void getPhoneNumberDelegatesToCustomerService() throws Exception {
        when(adapter.send(any(Message.class))).thenReturn(new Status(1));

        mainframe.client.messages.customerPhoneNumber.Retrieve result =
                main.getPhoneNumber("C123", 1L);

        ArgumentCaptor<mainframe.client.messages.customerPhoneNumber.Retrieve> captor =
                ArgumentCaptor.forClass(mainframe.client.messages.customerPhoneNumber.Retrieve.class);
        verify(adapter).send(captor.capture());
        assertEquals(result, captor.getValue());
    }

    @Test
    void listEmailsDelegatesToCustomerService() throws Exception {
        when(adapter.send(any(Message.class))).thenReturn(new Status(1));

        mainframe.client.messages.customerEmail.List.Item[] result =
                main.listEmails("C123", 1, 10);

        verify(adapter).send(any(mainframe.client.messages.customerEmail.List.class));
        assertEquals(0, result.length);
    }

    @Test
    void getEmailDelegatesToCustomerService() throws Exception {
        when(adapter.send(any(Message.class))).thenReturn(new Status(1));

        mainframe.client.messages.customerEmail.Retrieve result =
                main.getEmail("C123", 1L);

        ArgumentCaptor<mainframe.client.messages.customerEmail.Retrieve> captor =
                ArgumentCaptor.forClass(mainframe.client.messages.customerEmail.Retrieve.class);
        verify(adapter).send(captor.capture());
        assertEquals(result, captor.getValue());
    }

    @Test
    void addLoanDelegatesToLoanService() throws Exception {
        when(adapter.send(any(List.class))).thenAnswer(invocation -> {
            List listMessage = invocation.getArgument(0);
            Field dataField = List.class.getDeclaredField("_data");
            dataField.setAccessible(true);
            @SuppressWarnings("unchecked")
            java.util.ArrayList<List.Item> items =
                    (java.util.ArrayList<List.Item>) dataField.get(listMessage);
            items.add(listMessage.new Item("Auckland", "NZ", 1L, "y", "n", "Home"));
            return new Status(0, null, 1L);
        });
        when(adapter.send(any(mainframe.client.messages.loan.Create.class)))
                .thenReturn(new Status(1));

        java.time.LocalDate now = java.time.LocalDate.now();
        boolean result = main.addLoan(
                "C123",
                1000.0,
                5.0,
                12,
                "Fixed",
                "Monthly",
                "Monthly",
                now.plusDays(30).toString(),
                now.plusDays(5).toString());

        assertTrue(result);
        verify(adapter).send(any(mainframe.client.messages.loan.Create.class));
    }

    private static void injectField(Object target, String fieldName, Object value)
            throws ReflectiveOperationException {
        Field field = Main.class.getDeclaredField(fieldName);
        field.setAccessible(true);
        field.set(target, value);
    }
}
