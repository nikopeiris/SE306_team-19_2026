package dataaccess.systemadapters;

import mainframe.client.Status;
import mainframe.client.messages.customer.Create;

import java.io.IOException;

/**
 * Data access operations for customers.
 */
public interface CustomerAdapter {
    Status addCustomer(Create createMessage) throws IOException;
}
