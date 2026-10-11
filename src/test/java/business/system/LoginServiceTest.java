package business.system;

import business.lavsexceptions.LavsException;
import business.validation.ValidationException;
import dataaccess.systemadapters.MainframeAdapter;
import mainframe.client.Status;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.IOException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class LoginServiceTest {
    @Mock
    private MainframeAdapter adapter;

    private LoginService loginService;

    @BeforeEach
    void setUp() {
        loginService = new LoginService(adapter);
    }

    @Test
    void attemptLoginTrimsUsernameAndReturnsTrueOnSuccess() throws Exception {
        when(adapter.login("jane", "secret")).thenReturn(new Status(1));

        assertTrue(loginService.attemptLogin(" jane ", "secret"));

        verify(adapter).login("jane", "secret");
    }

    @Test
    void attemptLoginRejectsBlankCredentialsBeforeCallingAdapter() {
        ValidationException exception = assertThrows(ValidationException.class, () ->
                loginService.attemptLogin(" ", "secret"));

        assertEquals("Username cannot be null or empty",
                exception.getResult().getError("username"));
        verifyNoInteractions(adapter);
    }

    @Test
    void attemptLoginRejectsShortUsername() {
        ValidationException exception = assertThrows(ValidationException.class, () ->
                loginService.attemptLogin("ab", "secret"));

        assertEquals("Username must be at least 3 characters long",
                exception.getResult().getError("username"));
        verifyNoInteractions(adapter);
    }

    @Test
    void attemptLoginThrowsLavsExceptionWhenMainframeRejectsLogin() {
        when(adapter.login(anyString(), anyString()))
                .thenReturn(new Status(401, "Invalid credentials", 2));

        LavsException exception = assertThrows(LavsException.class, () ->
                loginService.attemptLogin("jane", "wrong"));

        assertEquals("Invalid credentials", exception.getMessage());
    }

    @Test
    void attemptLoginPropagatesConnectionFailure() throws IOException {
        when(adapter.login(anyString(), anyString()))
                .thenThrow(new IOException("connection unavailable"));

        IOException exception = assertThrows(IOException.class, () ->
                loginService.attemptLogin("jane", "secret"));

        assertEquals("connection unavailable", exception.getMessage());
    }
}
