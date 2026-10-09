package lavs.service;

import lavs.testutil.QueueStubConnection;
import lavs.validation.ValidationException;
import mainframe.client.Connection;
import mainframe.client.MessageRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AuthServiceTest {
    private QueueStubConnection _connection;
    private AuthService _service;

    @BeforeEach
    void setUp() {
        _connection = new QueueStubConnection();
        _service = new AuthService(_connection);
    }

    @Test
    void loginWithValidCredentialsLogsUserIn() throws LavsException {
        _connection.respondWithLogin("mmm");

        _service.login("mmm", "secret");

        MessageRequest request = _connection.getLastRequest();
        assertAll(
                () -> assertTrue(_service.isLoggedIn()),
                () -> assertEquals("mmm", _service.getCurrentUser()),
                () -> assertEquals(MessageRequest.LOGIN, request.getMessageType()),
                () -> assertEquals("mmm", request.getValue("userName")),
                () -> assertEquals("secret", request.getValue("password"))
        );
    }

    @Test
    void loginTrimsUsername() throws LavsException {
        _connection.respondWithLogin("mmm");

        _service.login("  mmm ", "secret");

        assertAll(
                () -> assertEquals("mmm", _service.getCurrentUser()),
                () -> assertEquals("mmm", _connection.getLastRequest().getValue("userName"))
        );
    }

    @Test
    void loginWithBlankFieldsFailsValidationWithoutContactingMainframe() {
        ValidationException ex = assertThrows(ValidationException.class, () -> _service.login(" ", null));

        assertAll(
                () -> assertNotNull(ex.getResult().getError(AuthService.FIELD_USERNAME)),
                () -> assertNotNull(ex.getResult().getError(AuthService.FIELD_PASSWORD)),
                () -> assertTrue(_connection.getSentRequests().isEmpty()),
                () -> assertFalse(_service.isLoggedIn())
        );
    }

    @Test
    void loginRejectedByMainframeThrowsWithMainframeMessage() {
        _connection.respondWithError(3, "Invalid username or password");

        LavsException ex = assertThrows(LavsException.class, () -> _service.login("mmm", "wrong"));

        assertAll(
                () -> assertEquals("Invalid username or password", ex.getMessage()),
                () -> assertEquals(3, ex.getErrorCode()),
                () -> assertFalse(ex.isNetworkFailure()),
                () -> assertFalse(_service.isLoggedIn())
        );
    }

    @Test
    void loginDuringNetworkDropoutReportsNetworkFailure() {
        _connection.respondWithNetworkFailure();

        LavsException ex = assertThrows(LavsException.class, () -> _service.login("mmm", "secret"));

        assertTrue(ex.isNetworkFailure());
    }

    @Test
    void loginWrapsIoException() throws IOException {
        Connection connection = mock(Connection.class);
        IOException cause = new IOException("boom");
        when(connection.Login(anyString(), anyString())).thenThrow(cause);
        AuthService service = new AuthService(connection);

        LavsException ex = assertThrows(LavsException.class, () -> service.login("mmm", "secret"));

        assertAll(
                () -> assertSame(cause, ex.getCause()),
                () -> assertNull(ex.getErrorCode()),
                () -> assertFalse(ex.isNetworkFailure())
        );
    }

    @Test
    void logoutClearsCurrentUser() throws LavsException {
        _connection.respondWithLogin("mmm");
        _service.login("mmm", "secret");

        _service.logout();

        assertAll(
                () -> assertFalse(_service.isLoggedIn()),
                () -> assertNull(_service.getCurrentUser()),
                () -> assertTrue(_connection.isLoggedOut())
        );
    }

    @Test
    void reportsWhetherMainframeIsReachable() {
        _connection.setIsConnected(false);
        assertFalse(_service.isMainframeReachable());

        _connection.setIsConnected(true);
        assertTrue(_service.isMainframeReachable());
    }
}
