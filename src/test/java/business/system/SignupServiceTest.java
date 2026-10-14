package business.system;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

import org.junit.jupiter.api.AfterEach;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import static org.mockito.Answers.CALLS_REAL_METHODS;
import org.mockito.ArgumentCaptor;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import org.mockito.junit.jupiter.MockitoExtension;

import business.Main;
import business.lavsexceptions.LavsException;
import business.validation.ValidationException;
import dataaccess.systemadapters.MainframeAdapter;
import mainframe.client.Message;
import mainframe.client.Status;
import mainframe.client.messages.user.Create;

@ExtendWith(MockitoExtension.class)
class SignupServiceTest {
    @Mock
    private MainframeAdapter adapter;

    private SignupService signupService;
    private MockedStatic<Main> mockedMain;

    @BeforeEach
    void setUp() {
        Map<String, String> env = new HashMap<>();
        env.put("ADMIN_USERNAME", "admin");
        env.put("ADMIN_PASSWORD", "secret");

        mockedMain = mockStatic(Main.class, CALLS_REAL_METHODS);
        mockedMain.when(Main::loadEnv).thenReturn(env);

        signupService = new SignupService(adapter);
    }

    @AfterEach
    void tearDown() {
        mockedMain.close();
    }

    @Test
    void signupLogsInAsAdminCreatesUserAndReturnsTrue() throws Exception {
        when(adapter.login(anyString(), anyString())).thenReturn(new Status(1));
        when(adapter.send(any(Create.class))).thenReturn(new Status(2));

        assertTrue(signupService.signup("jane", "Jane Smith", "secret", 7, 3));

        ArgumentCaptor<Create> captor = ArgumentCaptor.forClass(Create.class);
        verify(adapter).send(captor.capture());
        Create createMessage = captor.getValue();
        assertEquals("jane", createMessage.getLogin());
        assertEquals("Jane Smith", createMessage.getName());
        assertEquals(3L, createMessage.getPermissions());
        inOrder(adapter).verify(adapter).login(anyString(), anyString());
        inOrder(adapter).verify(adapter).send(any(Create.class));
    }

    @Test
    void signupRejectsInvalidDetailsBeforeCallingAdapter() {
        ValidationException exception = assertThrows(ValidationException.class, () ->
                signupService.signup("ab", "", "secret", 0, 0));

        assertTrue(exception.getResult().getErrors().containsKey("login"));
        assertTrue(exception.getResult().getErrors().containsKey("name"));
        assertTrue(exception.getResult().getErrors().containsKey("organisation"));
        assertTrue(exception.getResult().getErrors().containsKey("permissions"));
        verifyNoInteractions(adapter);
    }

    @Test
    void signupThrowsLavsExceptionWhenAdminLoginFails() throws IOException {
        when(adapter.login(anyString(), anyString()))
                .thenReturn(new Status(401, "Admin login failed", 1));

        LavsException exception = assertThrows(LavsException.class, () ->
                signupService.signup("jane", "Jane Smith", "secret", 7, 3));

        assertEquals("Admin login failed", exception.getMessage());
        verify(adapter, org.mockito.Mockito.never()).send(any(Message.class));
    }

    @Test
    void signupThrowsLavsExceptionWhenUserCreationFails() throws IOException {
        when(adapter.login(anyString(), anyString())).thenReturn(new Status(1));
        when(adapter.send(any(Create.class)))
                .thenReturn(new Status(400, "User creation failed", 2));

        LavsException exception = assertThrows(LavsException.class, () ->
                signupService.signup("jane", "Jane Smith", "secret", 7, 3));

        assertEquals("User creation failed", exception.getMessage());
    }

    @Test
    void signupPropagatesConnectionFailureDuringAdminLogin() throws IOException {
        when(adapter.login(anyString(), anyString()))
                .thenThrow(new IOException("connection unavailable"));

        IOException exception = assertThrows(IOException.class, () ->
                signupService.signup("jane", "Jane Smith", "secret", 7, 3));

        assertEquals("connection unavailable", exception.getMessage());
    }
}
