package dataaccess;

import mainframe.client.Connection;
import mainframe.client.Status;
import java.io.IOException;

/**
 * Adapter interface defining data access operations for user authentication.
 */
public interface AuthAdapter {
    Status login(String username, String password) throws IOException;
    Connection getConnection();
}
