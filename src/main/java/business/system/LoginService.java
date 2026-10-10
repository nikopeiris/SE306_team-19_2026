package business.system;

import business.lavsexceptions.LavsException;
import business.validation.ValidationException;
import business.validation.ValidationResult;
import dataaccess.systemadapters.MainframeAdapter;
import mainframe.client.Status;

import java.io.IOException;

/**
 * Business service responsible for user login
 */
public class LoginService {
    private final MainframeAdapter authAdapter;

    public LoginService(MainframeAdapter authAdapter) {
        this.authAdapter = authAdapter;
    }

    /**
     * Validates credentials and attempts login via the data access adapter.
     *
     * @param username user input username
     * @param password user input password
     * @return true if login was successful
     * @throws ValidationException if validation fails (empty/null inputs)
     * @throws LavsException if the mainframe rejects the login
     * @throws IOException if network or mainframe connection fails
     */
    public boolean attemptLogin(String username, String password)
            throws IOException, LavsException {
        validateCredentials(username, password);
        Status status = authAdapter.login(username.trim(), password);
        if (!status.getWasSuccessful()) {
            throw new LavsException(status);
        }
        return true;
    }

    /**
     * Business validation logic for user credentials.
     */
    public void validateCredentials(String username, String password)
            throws ValidationException {
        ValidationResult result = new ValidationResult()
                .requireNotBlank("username", username,
                        "Username cannot be null or empty")
                .requireNotBlank("password", password,
                        "Password cannot be null or empty");

        if (username != null && !username.trim().isEmpty()
                && username.trim().length() < 3) {
            result.addError("username", "Username must be at least 3 characters long");
        }
        if (!result.isValid()) {
            throw new ValidationException(result);
        }
    }
}
