package business.system;

import business.lavsexceptions.LavsException;
import business.validation.ValidationException;
import business.validation.ValidationResult;
import dataaccess.systemadapters.MainframeAdapter;
import mainframe.client.Status;
import mainframe.client.messages.user.Create;
import business.Main;

import java.io.IOException;
import java.util.Map;

/**
 * Logs in with admin credentials from a .env file, then creates a new user
 * on the mainframe
 */
public class SignupService {
    private final MainframeAdapter authAdapter;

    public SignupService(MainframeAdapter authAdapter) {
        this.authAdapter = authAdapter;
    }

    /**
     *
     * @param login        the login/username for the new user
     * @param name         the display name for the new user
     * @param password     the password for the new user
     * @param organisation the organisation id for the new user
     * @param permissions  the permissions level for the new user
     * @return true if the user was created successfully
     * @throws ValidationException if validation fails
     * @throws IOException if network or mainframe connection fails
     * @throws LavsException if admin login or user creation fails
     */
    public boolean signup(String login, String name, String password, long organisation, long permissions)
            throws IOException, LavsException {
        // Validate input
        validateSignupDetails(login, name, password, organisation, permissions);

        // Load admin credentials from .env
        Map<String, String> env = Main.loadEnv();
        String adminUsername = env.get("ADMIN_USERNAME");
        String adminPassword = env.get("ADMIN_PASSWORD");

        if (adminUsername == null || adminPassword == null) {
            throw new LavsException("Admin credentials not found in .env file");
        }

        // Login as admin
        Status loginStatus = authAdapter.login(adminUsername, adminPassword);
        Main.isValidStatus(loginStatus);

        // Create the new user 
        Create createMessage = new Create(login, name, organisation, password, permissions);
        Status createStatus = authAdapter.send(createMessage);
        Main.isValidStatus(createStatus);

        return true;
    }

    /**
     * Validates the signup details for a new user.
     *
     * @param login    the login/username for the new user
     * @param name     the display name for the new user
     * @param password the password for the new user
     * @throws ValidationException if any validation fails
     */
    public void validateSignupDetails(
            String login, String name, String password, long organisation, long permissions)
            throws ValidationException {
        ValidationResult result = new ValidationResult()
                .requireNotBlank("login", login, "Login cannot be null or empty")
                .requireNotBlank("name", name, "Name cannot be null or empty")
                .requireNotBlank("password", password, "Password cannot be null or empty");

        if (login != null && !login.trim().isEmpty() && login.trim().length() < 3) {
            result.addError("login", "Login must be at least 3 characters long");
        }
        if (permissions < 1) {
            result.addError("permissions", "Permissions must be a positive integer");
        }
        if (organisation < 1) {
            result.addError("organisation", "Organisation must be a positive integer");
        }
        if (!result.isValid()) {
            throw new ValidationException(result);
        }
    }

}
