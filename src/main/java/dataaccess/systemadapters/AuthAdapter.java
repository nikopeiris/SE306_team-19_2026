package dataaccess;

import mainframe.client.Connection;
import mainframe.client.Status;
import mainframe.client.messages.user.Create;
import java.io.IOException;

/**
 * Adapter interface defining data access operations for user authentication.
 */
public interface AuthAdapter {
    Status login(String username, String password) throws IOException;
    Connection getConnection();
    void logOut();
    Status signUp(Create createMessage) throws IOException;
}
