package lavs.ui;

import java.io.IOException;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;

import business.lavsexceptions.LavsException;
import business.system.Customer;
import business.system.dto.CustomerDocument;
import business.validation.ValidationException;
import javafx.concurrent.Task;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.FlowPane;

/**
 * The documents section of the personal info tab (documents_section.fxml): a tile for each
 * of the customer's documents and a + tile to add another. Unlike the contact details,
 * documents are saved to the mainframe straight away, so this only works for a customer
 * that already exists. Every rule is checked by the business layer.
 */
public class DocumentsSectionController {

    @FXML private Label headingLabel;
    @FXML private Label hintLabel;
    @FXML private Label emptyLabel;
    @FXML private Label statusLabel;
    @FXML private FlowPane listContainer;
    @FXML private Button addTile;

    private CustomerContext context;
    private String customerNumber;

    public void init(CustomerContext context) {
        this.context = context;
    }

    /** Shows this customer's documents, or the save-first hint when there is no saved customer yet. */
    public void load(String customerNumber) {
        this.customerNumber = customerNumber;
        boolean available = customerNumber != null && !customerNumber.isBlank();
        setShown(hintLabel, !available);
        setShown(listContainer, available);
        if (available) {
            refresh();
        } else {
            listContainer.getChildren().removeIf(node -> node != addTile);
            setShown(emptyLabel, false);
            headingLabel.setText("Documents");
            setStatus("", false);
        }
    }

    // ------------------------------------------------------------------
    // Buttons
    // ------------------------------------------------------------------

    @FXML
    private void handleAdd(ActionEvent event) {
        AddDocumentDialog.show(context, customerNumber, addTile.getScene().getWindow(), this::refresh);
    }

    private void openDocument(CustomerDocument document) {
        AddDocumentDialog.showExisting(context, customerNumber, document,
                addTile.getScene().getWindow(), this::refresh);
    }

    // ------------------------------------------------------------------
    // The tiles
    // ------------------------------------------------------------------

    private void refresh() {
        setStatus("Loading documents...", false);
        Customer service = context.getCustomerService();
        String number = customerNumber;
        Task<List<CustomerDocument>> task = new Task<>() {
            @Override
            protected List<CustomerDocument> call() throws Exception {
                return service.listCustomerDocuments(number);
            }
        };
        task.setOnSucceeded(e -> {
            // a different customer may have been loaded while this was running
            if (number.equals(customerNumber)) {
                setStatus("", false);
                render(task.getValue());
            }
        });
        task.setOnFailed(e -> setStatus(describe(task.getException()), true));
        start(task, "load-documents");
    }

    private void render(List<CustomerDocument> documents) {
        // the + tile stays put at the end, everything else is rebuilt
        listContainer.getChildren().removeIf(node -> node != addTile);
        setShown(emptyLabel, documents.isEmpty());
        headingLabel.setText(documents.isEmpty() ? "Documents" : "Documents (" + documents.size() + ")");
        try {
            List<Node> tiles = new ArrayList<>();
            for (CustomerDocument document : documents) {
                FXMLLoader loader = new FXMLLoader(
                        getClass().getResource("/lavs/ui/document_row.fxml"));
                Node tile = loader.load();
                DocumentRowController controller = loader.getController();
                controller.setDocument(document, this::openDocument);
                tiles.add(tile);
            }
            listContainer.getChildren().addAll(0, tiles);
        } catch (IOException e) {
            setStatus(describe(e), true);
        }
    }

    private void setStatus(String message, boolean isError) {
        statusLabel.setText(message);
        statusLabel.getStyleClass().removeAll("hint-label", "error-label");
        statusLabel.getStyleClass().add(isError ? "error-label" : "hint-label");
    }

    private static void setShown(Node node, boolean shown) {
        node.setVisible(shown);
        node.setManaged(shown);
    }

    // ------------------------------------------------------------------
    // Shared with AddDocumentDialog
    // ------------------------------------------------------------------

    static String describe(Throwable error) {
        if (error != null) {
            error.printStackTrace();
        }
        if (error instanceof ValidationException || error instanceof LavsException) {
            return error.getMessage();
        } else if (error instanceof IOException) {
            return "Could not connect to the server.";
        }
        return error != null && error.getMessage() != null
                ? error.getMessage() : "Something went wrong.";
    }

    static Scene styled(Scene scene) {
        URL css = DocumentsSectionController.class.getResource("/lavs/ui/styles.css");
        if (css != null) {
            scene.getStylesheets().add(css.toExternalForm());
        }
        return scene;
    }

    static void start(Task<?> task, String name) {
        Thread thread = new Thread(task, name);
        thread.setDaemon(true);
        thread.start();
    }
}
