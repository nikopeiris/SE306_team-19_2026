package business.system;

import dataaccess.systemadapters.AuthAdapter;
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
    private final AuthAdapter authAdapter;

    public SignupService(AuthAdapter authAdapter) {
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
     * @throws IllegalArgumentException if validation fails
     * @throws IOException if network or mainframe connection fails
     * @throws RuntimeException if admin login or user creation fails
     */
    public boolean signup(String login, String name, String password, long organisation, long permissions) throws IOException {
        // Validate input
        validateSignupDetails(login, name, password, organisation, permissions);

        // Load admin credentials from .env
        Map<String, String> env = Main.loadEnv();
        String adminUsername = env.get("ADMIN_USERNAME");
        String adminPassword = env.get("ADMIN_PASSWORD");

        if (adminUsername == null || adminPassword == null) {
            throw new RuntimeException("Admin credentials not found in .env file");
        }

        // Login as admin
        Status loginStatus = authAdapter.login(adminUsername, adminPassword);
        if (!loginStatus.getWasSuccessful()) {
            throw new RuntimeException("Admin login failed: " + loginStatus.getErrorMessage());
        }

        // Create the new user 
        Create createMessage = new Create(login, name, organisation, password, permissions);
        Status createStatus = authAdapter.signUp(createMessage);
        if (!createStatus.getWasSuccessful()) {
            throw new RuntimeException("User creation failed: " + createStatus.getErrorMessage());
        }

        return true;
    }

    /**
     * Validates the signup details for a new user.
     *
     * @param login    the login/username for the new user
     * @param name     the display name for the new user
     * @param password the password for the new user
     * @throws IllegalArgumentException if any validation fails
     */
    public void validateSignupDetails(String login, String name, String password, long organisation, long permissions) {
        if (login == null || login.trim().isEmpty()) {
            throw new IllegalArgumentException("Login cannot be null or empty");
        }
        if (login.trim().length() < 3) {
            throw new IllegalArgumentException("Login must be at least 3 characters long");
        }
        if (name == null || name.trim().isEmpty()) {
            throw new IllegalArgumentException("Name cannot be null or empty");
        }
        if (password == null || password.isEmpty()) {
            throw new IllegalArgumentException("Password cannot be null or empty");
        }
        if (permissions < 1) {
            throw new IllegalArgumentException("Permissions must be a positive integer");
        }
        if (organisation < 1) {
            throw new IllegalArgumentException("Organisation must be a positive integer");
        }
    }

}
