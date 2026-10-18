package lavs.ui;

import java.io.File;
import java.io.IOException;

import business.system.Customer;
import javafx.concurrent.Task;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.stage.FileChooser;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.stage.Window;

/**
 * Controller for the add-document form (add_document_dialog.fxml). It only reads
 * the fields; every rule (what is required, what is valid) is checked by the
 * business layer and its message is shown in the form.
 */
public class AddDocumentDialog {

    @FXML private TextField typeField;
    @FXML private TextField fileNameField;
    @FXML private TextField fileTypeField;
    @FXML private TextField sizeField;
    @FXML private Label errorLabel;
    @FXML private Button saveButton;

    private CustomerContext context;
    private String customerNumber;
    private Runnable onAdded;
    private Stage stage;

    /** Opens the form and waits until it is closed; {@code onAdded} runs after a successful save. */
    public static void show(CustomerContext context, String customerNumber, Window owner,
            Runnable onAdded) {
        try {
            FXMLLoader loader = new FXMLLoader(
                    AddDocumentDialog.class.getResource("/lavs/ui/add_document_dialog.fxml"));
            Parent root = loader.load();
            AddDocumentDialog dialog = loader.getController();
            dialog.open(context, customerNumber, onAdded, root, owner);
        } catch (IOException e) {
            e.printStackTrace();
            Alert alert = new Alert(Alert.AlertType.ERROR);
            alert.setTitle("Add Document");
            alert.setHeaderText("Could not open the form");
            alert.setContentText(CustomerDocumentsDialog.describe(e));
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

    private void open(CustomerContext context, String customerNumber, Runnable onAdded,
            Parent root, Window owner) {
        this.context = context;
        this.customerNumber = customerNumber;
        this.onAdded = onAdded;

        stage = new Stage();
        stage.initModality(Modality.APPLICATION_MODAL);
        stage.initOwner(owner);
        stage.setTitle("Add Document");
        stage.setScene(CustomerDocumentsDialog.styled(new Scene(root)));
        stage.showAndWait();
    }

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
            onAdded.run();
        });
        task.setOnFailed(e -> {
            saveButton.setDisable(false);
            errorLabel.setText(CustomerDocumentsDialog.describe(task.getException()));
        });
        CustomerDocumentsDialog.start(task, "add-document");
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