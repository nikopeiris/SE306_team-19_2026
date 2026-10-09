package lavs.service;

import lavs.validation.ValidationException;
import lavs.validation.ValidationResult;
import mainframe.client.Connection;
import mainframe.client.Status;

import java.io.IOException;

// handles logging staff in to and out of the mainframe
public class AuthService {
    public static final String FIELD_USERNAME = "username";
    public static final String FIELD_PASSWORD = "password";

    private final Connection _connection;
    private String _currentUser;

    public AuthService(Connection connection) {
        _connection = connection;
    }

    // logs in with the given credentials; throws if they are missing or rejected
    public void login(String username, String password) throws LavsException {
        ValidationResult result = new ValidationResult()
                .requireNotBlank(FIELD_USERNAME, username, "Username is required")
                .requireNotBlank(FIELD_PASSWORD, password, "Password is required");
        if (!result.isValid()) {
            throw new ValidationException(result);
        }

        String trimmedUsername = username.trim();
        Status status;
        try {
            status = _connection.Login(trimmedUsername, password);
        } catch (IOException ex) {
            throw new LavsException("Unable to contact the mainframe", ex);
        }
        if (!status.getWasSuccessful()) {
            throw new LavsException(status);
        }
        _currentUser = trimmedUsername;
    }

    public void logout() {
        _connection.Logout();
        _currentUser = null;
    }

    public boolean isLoggedIn() {
        return _currentUser != null;
    }

    // gets the logged-in username, or null if nobody is logged in
    public String getCurrentUser() {
        return _currentUser;
    }

    // checks whether the mainframe can currently be reached
    public boolean isMainframeReachable() {
        return _connection.isConnected();
    }
}
