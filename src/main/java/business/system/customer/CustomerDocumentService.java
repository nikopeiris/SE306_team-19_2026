package business.system.customer;

import java.io.IOException;

import business.Main;
import business.lavsexceptions.LavsException;
import dataaccess.systemadapters.MainframeAdapter;
import mainframe.client.Message;
import mainframe.client.Status;
import mainframe.client.messages.customer.AddDocument;

public class CustomerDocumentService {
  private final MainframeAdapter adapter;

  public CustomerDocumentService(MainframeAdapter adapter) {
    if (adapter == null) {
      throw new IllegalArgumentException("Document adpater cannot be null");
    }
    this.adapter = adapter;
  }

  public AddDocument addDocument(String customer, String fileName, String fileType, long size, String type) throws IOException, LavsException {
    AddDocument addDocument = new AddDocument(customer.trim(), fileName.trim(), fileType.trim(), size,
        type.trim());
    send(addDocument);
    return addDocument;
  }


  private void send(Message message) throws IOException, LavsException {
    Status status = adapter.send(message);
    Main.isValidStatus(status);
  }
    

}
