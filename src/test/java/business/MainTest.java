package business;

import business.lavsexceptions.LavsException;
import business.system.SignupService;
import dataaccess.systemadapters.MainframeAdapter;
import mainframe.client.Message;
import mainframe.client.Status;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import java.lang.reflect.Field;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Answers.CALLS_REAL_METHODS;

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

    private static void injectField(Object target, String fieldName, Object value)
            throws ReflectiveOperationException {
        Field field = Main.class.getDeclaredField(fieldName);
        field.setAccessible(true);
        field.set(target, value);
    }
}
