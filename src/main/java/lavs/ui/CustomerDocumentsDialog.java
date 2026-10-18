package lavs.ui;

import java.io.IOException;
import java.net.URL;
import java.util.List;
import java.util.Optional;

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
import javafx.scene.control.ButtonType;
import javafx.scene.control.Label;
import javafx.scene.layout.VBox;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.stage.Window;

/**
 * Controller for the documents popup (documents_dialog.fxml): lists a customer's
 * documents and lets the user add or remove them. Unlike the contact details,
 * documents are saved to the mainframe straight away, so this only works for a
 * customer that already exists. Every rule is checked by the business layer.
 */
public class CustomerDocumentsDialog {

    private static final String MUTED = "-fx-text-fill: #726e97;";
    private static final String ERROR = "-fx-text-fill: #b00020;";

    @FXML private Label hintLabel;
    @FXML private Label emptyLabel;
    @FXML private Label statusLabel;
    @FXML private VBox listContainer;

    private CustomerContext context;
    private String customerNumber;
    private Stage stage;

    /** Opens the popup and waits until it is closed. */
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

    @FXML
    private void handleClose(ActionEvent event) {
        stage.close();
    }

    // ------------------------------------------------------------------
    // The list
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
        listContainer.getChildren().clear();
        emptyLabel.setVisible(documents.isEmpty());
        emptyLabel.setManaged(documents.isEmpty());
        try {
            for (CustomerDocument document : documents) {
                FXMLLoader loader = new FXMLLoader(
                        getClass().getResource("/lavs/ui/document_row.fxml"));
                Node row = loader.load();
                DocumentRowController controller = loader.getController();
                controller.setDocument(document, this::confirmRemove);
                listContainer.getChildren().add(row);
            }
        } catch (IOException e) {
            setStatus(describe(e), true);
        }
    }

    private void confirmRemove(CustomerDocument document) {
        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION,
                "Remove \"" + document.fileName() + "\" from this customer?",
                ButtonType.YES, ButtonType.NO);
        confirm.setTitle("Remove document");
        confirm.setHeaderText(null);
        confirm.initOwner(stage);
        Optional<ButtonType> answer = confirm.showAndWait();
        if (answer.isEmpty() || answer.get() != ButtonType.YES) {
            return;
        }

        Customer service = context.getCustomerService();
        setStatus("Removing document...", false);
        Task<Void> task = new Task<>() {
            @Override
            protected Void call() throws Exception {
                service.deleteCustomerDocument(customerNumber, document.id());
                return null;
            }
        };
        task.setOnSucceeded(e -> refresh());
        task.setOnFailed(e -> setStatus(describe(task.getException()), true));
        start(task, "delete-document");
    }

    private void setStatus(String message, boolean isError) {
        statusLabel.setText(message);
        statusLabel.setStyle(isError ? ERROR : MUTED);
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