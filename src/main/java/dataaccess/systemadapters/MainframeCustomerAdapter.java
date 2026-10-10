package dataaccess.systemadapters;

import mainframe.client.Connection;
import mainframe.client.HttpConnection;
import mainframe.client.Status;
import mainframe.client.messages.customer.Create;

import java.io.IOException;

/**
 * Adapter for customer operations provided by the mainframe.
 */
public class MainframeCustomerAdapter implements CustomerAdapter {
    private final Connection connection;

    public MainframeCustomerAdapter(String baseUrl) {
        this(new HttpConnection(baseUrl));
    }

    public MainframeCustomerAdapter(Connection connection) {
        if (connection == null) {
            throw new IllegalArgumentException("Mainframe connection cannot be null");
        }
        this.connection = connection;
    }

    @Override
    public Status addCustomer(Create createMessage) throws IOException {
        if (createMessage == null) {
            throw new IllegalArgumentException("Customer create message cannot be null");
        }
        return createMessage.send(connection);
    }
}
