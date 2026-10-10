package dataaccess;

import mainframe.client.Connection;

import java.io.IOException;

/**
 * Adapter interface defining data access operations for user authentication.
 */
public interface AuthAdapter {
    boolean login(String username, String password) throws IOException;
    Connection getConnection();
}
