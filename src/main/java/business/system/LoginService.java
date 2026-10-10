package business.system;

import dataaccess.AuthAdapter;
import mainframe.client.Status;

import java.io.IOException;

/**
 * Business service responsible for user login
 */
public class LoginService {
    private final AuthAdapter authAdapter;

    public LoginService(AuthAdapter authAdapter) {
        this.authAdapter = authAdapter;
    }

    /**
     * Validates credentials and attempts login via the data access adapter.
     *
     * @param username user input username
     * @param password user input password
     * @return true if login was successful
     * @throws IllegalArgumentException if validation fails (empty/null inputs)
     * @throws IOException if network or mainframe connection fails
     */
    public boolean attemptLogin(String username, String password) throws IOException {
        validateCredentials(username, password);
        Status status = authAdapter.login(username.trim(), password);
        if (!status.getWasSuccessful()) {
            throw new RuntimeException("Login failed: " + status.getErrorMessage());
        }
        return true;
    }

    /**
     * Business validation logic for user credentials.
     */
    public void validateCredentials(String username, String password) {
        if (username == null || username.trim().isEmpty()) {
            throw new IllegalArgumentException("Username cannot be empty");
        }
        if (password == null || password.isEmpty()) {
            throw new IllegalArgumentException("Password cannot be empty");
        }
        if (username.trim().length() < 3) {
            throw new IllegalArgumentException("Username must be at least 3 characters long");
        }
    }
}
