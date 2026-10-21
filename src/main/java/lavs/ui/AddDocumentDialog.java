package lavs.ui;

import java.io.File;
import java.io.IOException;
import java.util.Objects;

import business.system.Customer;
import business.system.dto.CustomerDocument;
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
import javafx.scene.control.TextField;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.stage.FileChooser;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.stage.StageStyle;
import javafx.stage.Window;

/**
 * Controller for the document popup (add_document_dialog.fxml). It adds a new document,
 * or shows an existing one with a delete button. It only reads the fields; every rule
 * (what is required, what is valid) is checked by the business layer and its message
 * is shown in the form.
 */
public class AddDocumentDialog {

    @FXML private Label titleLabel;
    @FXML private Label noteLabel;
    @FXML private Button chooseFileButton;
    @FXML private TextField typeField;
    @FXML private TextField fileNameField;
    @FXML private TextField fileTypeField;
    @FXML private TextField sizeField;
    @FXML private VBox fileTypeBox;
    @FXML private VBox sizeBox;
    @FXML private Label errorLabel;
    @FXML private HBox actionRow;
    @FXML private Button saveButton;
    @FXML private Button deleteButton;
    @FXML private VBox confirmBox;
    @FXML private Button confirmDeleteButton;

    private CustomerContext context;
    private String customerNumber;
    private Runnable onChanged;
    private Stage stage;

    // null while adding, set when the popup was opened from an existing document's tile
    private CustomerDocument document;

    /** Opens the form for a new document and waits until it is closed; {@code onAdded} runs after a successful save. */
    public static void show(CustomerContext context, String customerNumber, Window owner,
            Runnable onAdded) {
        open(context, customerNumber, null, owner, onAdded);
    }

    /** Opens an existing document's details; {@code onChanged} runs after it is deleted. */
    public static void showExisting(CustomerContext context, String customerNumber,
            CustomerDocument document, Window owner, Runnable onChanged) {
        open(context, customerNumber, document, owner, onChanged);
    }

    private static void open(CustomerContext context, String customerNumber,
            CustomerDocument document, Window owner, Runnable onChanged) {
        try {
            FXMLLoader loader = new FXMLLoader(
                    AddDocumentDialog.class.getResource("/lavs/ui/add_document_dialog.fxml"));
            Parent root = loader.load();
            AddDocumentDialog dialog = loader.getController();
            dialog.open(context, customerNumber, document, onChanged, root, owner);
        } catch (IOException e) {
            e.printStackTrace();
            Alert alert = new Alert(Alert.AlertType.ERROR);
            alert.setTitle("Document");
            alert.setHeaderText("Could not open the form");
            alert.setContentText(DocumentsSectionController.describe(e));
            alert.showAndWait();
        }
    }

    @FXML
    private void initialize() {
        sizeField.textProperty().addListener((obs, oldValue, newValue) -> {
            if (newValue != null && !newValue.matches("\\d*")) {
                sizeField.setText(oldValue);
            }
        });
    }

    private void open(CustomerContext context, String customerNumber, CustomerDocument document,
            Runnable onChanged, Parent root, Window owner) {
        this.context = context;
        this.customerNumber = customerNumber;
        this.document = document;
        this.onChanged = onChanged;

        if (document != null) {
            showDetails(document);
        }

        stage = new Stage();
        stage.initModality(Modality.APPLICATION_MODAL);
        stage.initOwner(owner);
        // the X inside the popup is the way out, so no window bar on top of it
        stage.initStyle(StageStyle.UNDECORATED);
        stage.setTitle(document == null ? "Add Document" : "Document");
        stage.setScene(DocumentsSectionController.styled(new Scene(root)));
        stage.showAndWait();
    }

    // The service has no way to edit a document, so an existing one is read-only and can only be deleted.
    // The document list only carries the type and file name, so file type and size aren't shown here.
    private void showDetails(CustomerDocument shown) {
        titleLabel.setText("Document details");
        typeField.setText(Objects.toString(shown.type(), ""));
        fileNameField.setText(Objects.toString(shown.fileName(), ""));

        typeField.setEditable(false);
        fileNameField.setEditable(false);
        setShown(fileTypeBox, false);
        setShown(sizeBox, false);
        setShown(chooseFileButton, false);
        setShown(noteLabel, false);
        setShown(saveButton, false);
        saveButton.setDefaultButton(false);
        setShown(deleteButton, true);
    }

    private static void setShown(Node node, boolean shown) {
        node.setVisible(shown);
        node.setManaged(shown);
    }

    // ------------------------------------------------------------------
    // Adding
    // ------------------------------------------------------------------

    @FXML
    private void handleChooseFile(ActionEvent event) {
        File file = new FileChooser().showOpenDialog(stage);
        if (file != null) {
            fileNameField.setText(file.getName());
            fileTypeField.setText(extensionOf(file.getName()));
            sizeField.setText(String.valueOf(file.length()));
        }
    }

    @FXML
    private void handleSave(ActionEvent event) {
        Customer service = context.getCustomerService();
        String type = typeField.getText();
        String fileName = fileNameField.getText();
        String fileType = fileTypeField.getText();
        Long size = parseSize(sizeField.getText());

        errorLabel.setText("");
        saveButton.setDisable(true);
        Task<Void> task = new Task<>() {
            @Override
            protected Void call() throws Exception {
                service.addCustomerDocument(customerNumber, type, fileName, fileType, size);
                return null;
            }
        };
        task.setOnSucceeded(e -> {
            stage.close();
            onChanged.run();
        });
        task.setOnFailed(e -> {
            saveButton.setDisable(false);
            errorLabel.setText(DocumentsSectionController.describe(task.getException()));
        });
        DocumentsSectionController.start(task, "add-document");
    }

    // ------------------------------------------------------------------
    // Deleting
    // ------------------------------------------------------------------

    // Delete asks first, in the popup itself, then swaps the buttons back if they change their mind
    @FXML
    private void handleDelete(ActionEvent event) {
        errorLabel.setText("");
        setShown(actionRow, false);
        setShown(confirmBox, true);
    }

    @FXML
    private void handleKeepDocument(ActionEvent event) {
        setShown(confirmBox, false);
        setShown(actionRow, true);
    }

    @FXML
    private void handleConfirmDelete(ActionEvent event) {
        Customer service = context.getCustomerService();
        CustomerDocument target = document;

        confirmDeleteButton.setDisable(true);
        Task<Void> task = new Task<>() {
            @Override
            protected Void call() throws Exception {
                service.deleteCustomerDocument(customerNumber, target.id());
                return null;
            }
        };
        task.setOnSucceeded(e -> {
            stage.close();
            onChanged.run();
        });
        task.setOnFailed(e -> {
            confirmDeleteButton.setDisable(false);
            errorLabel.setText(DocumentsSectionController.describe(task.getException()));
            handleKeepDocument(null);
        });
        DocumentsSectionController.start(task, "delete-document");
    }

    @FXML
    private void handleCancel(ActionEvent event) {
        stage.close();
    }

    private static Long parseSize(String text) {
        try {
            return text == null || text.isBlank() ? null : Long.valueOf(text.trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static String extensionOf(String fileName) {
        int dot = fileName.lastIndexOf('.');
        return dot >= 0 && dot < fileName.length() - 1 ? fileName.substring(dot + 1) : "";
    }
}