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
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.FlowPane;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.stage.Window;

/**
 * Controller for the documents window (documents_dialog.fxml): shows a customer's
 * documents as tiles, with a + tile to add another. Unlike the contact details,
 * documents are saved to the mainframe straight away, so this only works for a
 * customer that already exists. Every rule is checked by the business layer.
 */
public class CustomerDocumentsDialog {

    @FXML private Label hintLabel;
    @FXML private Label emptyLabel;
    @FXML private Label statusLabel;
    @FXML private FlowPane listContainer;
    @FXML private Button addTile;

    private CustomerContext context;
    private String customerNumber;
    private Stage stage;

    /** Opens the window and waits until it is closed. */
    public static void show(CustomerContext context, String customerNumber, Window owner) {
        try {
            FXMLLoader loader = new FXMLLoader(
                    CustomerDocumentsDialog.class.getResource("/lavs/ui/documents_dialog.fxml"));
            Parent root = loader.load();
            CustomerDocumentsDialog dialog = loader.getController();
            dialog.open(context, customerNumber, root, owner);
        } catch (IOException e) {
            e.printStackTrace();
            Alert alert = new Alert(Alert.AlertType.ERROR);
            alert.setTitle("Documents");
            alert.setHeaderText("Could not open the documents window");
            alert.setContentText(describe(e));
            alert.showAndWait();
        }
    }

    private void open(CustomerContext context, String customerNumber, Parent root, Window owner) {
        this.context = context;
        this.customerNumber = customerNumber;

        hintLabel.setText("Customer " + customerNumber
                + ". Adding or removing a document is saved straight away.");

        stage = new Stage();
        stage.initModality(Modality.APPLICATION_MODAL);
        stage.initOwner(owner);
        stage.setTitle("Documents - Customer " + customerNumber);
        stage.setScene(styled(new Scene(root)));

        refresh();
        stage.showAndWait();
    }

    // ------------------------------------------------------------------
    // Buttons
    // ------------------------------------------------------------------

    @FXML
    private void handleAdd(ActionEvent event) {
        AddDocumentDialog.show(context, customerNumber, stage, this::refresh);
    }

    // ------------------------------------------------------------------
    // The tiles
    // ------------------------------------------------------------------

    private void refresh() {
        setStatus("Loading documents...", false);
        Customer service = context.getCustomerService();
        Task<List<CustomerDocument>> task = new Task<>() {
            @Override
            protected List<CustomerDocument> call() throws Exception {
                return service.listCustomerDocuments(customerNumber);
            }
        };
        task.setOnSucceeded(e -> {
            setStatus("", false);
            render(task.getValue());
        });
        task.setOnFailed(e -> setStatus(describe(task.getException()), true));
        start(task, "load-documents");
    }

    private void render(List<CustomerDocument> documents) {
        // the + tile stays put at the end, everything else is rebuilt
        listContainer.getChildren().removeIf(node -> node != addTile);
        emptyLabel.setVisible(documents.isEmpty());
        emptyLabel.setManaged(documents.isEmpty());
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

    private void openDocument(CustomerDocument document) {
        AddDocumentDialog.showExisting(context, customerNumber, document, stage, this::refresh);
    }

    private void setStatus(String message, boolean isError) {
        statusLabel.setText(message);
        statusLabel.getStyleClass().removeAll("hint-label", "error-label");
        statusLabel.getStyleClass().add(isError ? "error-label" : "hint-label");
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
        URL css = CustomerDocumentsDialog.class.getResource("/lavs/ui/styles.css");
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