package dataaccess;

import mainframe.client.Connection;
import mainframe.client.HttpConnection;
import mainframe.client.Status;

import java.io.IOException;

/**
 * Adapter implementation wrapping Mainframe HttpConnection for authentication.
 */
public class MainframeAuthAdapter implements AuthAdapter {
    private Connection connection;

    public MainframeAuthAdapter(String baseUrl) {
        this.connection = new HttpConnection(baseUrl);
    }

    public MainframeAuthAdapter(Connection connection) {
        this.connection = connection;
    }

    @Override
    public Status login(String username, String password) throws IOException {
        return connection.Login(username, password);
    }

    @Override
    public Connection getConnection() {
        return connection;
    }
}
