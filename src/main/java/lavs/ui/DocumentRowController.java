package lavs.ui;

import java.util.function.Consumer;

import business.system.dto.CustomerDocument;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.scene.control.Label;

/** One tile in the documents window (document_row.fxml). Clicking it opens the document's details. */
public class DocumentRowController {

    @FXML private Label nameLabel;
    @FXML private Label typeLabel;

    private CustomerDocument document;
    private Consumer<CustomerDocument> onOpen;

    public void setDocument(CustomerDocument document, Consumer<CustomerDocument> onOpen) {
        this.document = document;
        this.onOpen = onOpen;
        nameLabel.setText(document.fileName());
        typeLabel.setText(document.type());
    }

    @FXML
    private void handleOpen(ActionEvent event) {
        onOpen.accept(document);
    }
}