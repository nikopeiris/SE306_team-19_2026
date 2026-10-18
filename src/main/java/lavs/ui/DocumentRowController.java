package lavs.ui;

import java.util.function.Consumer;

import business.system.dto.CustomerDocument;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.scene.control.Label;

/** One row in the documents popup (document_row.fxml). */
public class DocumentRowController {

    @FXML private Label nameLabel;
    @FXML private Label typeLabel;

    private CustomerDocument document;
    private Consumer<CustomerDocument> onRemove;

    public void setDocument(CustomerDocument document, Consumer<CustomerDocument> onRemove) {
        this.document = document;
        this.onRemove = onRemove;
        nameLabel.setText(document.fileName());
        typeLabel.setText(document.type());
    }

    @FXML
    private void handleRemove(ActionEvent event) {
        onRemove.accept(document);
    }
}