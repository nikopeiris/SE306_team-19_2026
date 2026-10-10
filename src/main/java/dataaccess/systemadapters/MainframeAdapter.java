package dataaccess.systemadapters;

import mainframe.client.Connection;
import mainframe.client.HttpConnection;
import mainframe.client.Status;
import mainframe.client.messages.user.Create;

import java.io.IOException;

/**
 * Adapter implementation wrapping Mainframe HttpConnection for authentication.
 */
public class MainframeAdapter {
    private Connection connection;

    public MainframeAdapter(String baseUrl) {
        this.connection = new HttpConnection(baseUrl);
    }

    public MainframeAdapter(Connection connection) {
        this.connection = connection;
    }

    public Status login(String username, String password) throws IOException {
        return connection.Login(username, password);
    }

    public Connection getConnection() {
        return connection;
    }

    public void logOut(){
        connection.Logout();
    }

    public Status signUp(Create createMessage) throws IOException{
      return createMessage.send(connection);
    }

    public Status addCustomer(mainframe.client.messages.customer.Create createMessage) throws IOException{
      return createMessage.send(connection);
    }
}
