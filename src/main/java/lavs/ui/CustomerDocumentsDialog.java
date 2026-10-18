package lavs.ui;

import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.util.List;
import java.util.Optional;

import business.lavsexceptions.LavsException;
import business.system.Customer;
import business.system.dto.CustomerDocument;
import business.validation.ValidationException;
import javafx.concurrent.Task;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TextField;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.stage.FileChooser;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.stage.Window;

/**
 * Popup that lists a customer's documents and lets the user add or remove them.
 * Unlike the contact details, documents are saved to the mainframe straight away,
 * so this only works for a customer that already exists. Every rule (what is
 * required, what is valid) is checked by the business layer.
 */
public class CustomerDocumentsDialog {

    private static final String MUTED = "-fx-text-fill: #726e97;";

    private final CustomerContext context;
    private final String customerNumber;

    private final VBox listContainer = new VBox(8);
    private final Label statusLabel = new Label();
    private Stage stage;

    public CustomerDocumentsDialog(CustomerContext context, String customerNumber) {
        this.context = context;
        this.customerNumber = customerNumber;
    }

    public void show(Window owner) {
        stage = new Stage();
        stage.initModality(Modality.APPLICATION_MODAL);
        stage.initOwner(owner);
        stage.setTitle("Documents - Customer " + customerNumber);

        Label heading = new Label("Documents");
        heading.setStyle("-fx-font-size: 16; -fx-font-weight: bold;");
        Label hint = new Label("Customer " + customerNumber
                + ". Adding or removing a document is saved straight away.");
        hint.setStyle(MUTED);
        hint.setWrapText(true);

        ScrollPane scroll = new ScrollPane(listContainer);
        scroll.setFitToWidth(true);
        scroll.setPrefViewportHeight(300);
        scroll.setStyle("-fx-background-color: transparent;");

        statusLabel.setWrapText(true);
        statusLabel.setMaxWidth(Double.MAX_VALUE);

        Button add = new Button("Add Document");
        add.setPrefHeight(36);
        add.setStyle("-fx-background-color: #011936; -fx-text-fill: white;");
        add.setOnAction(e -> showAddDialog());

        Button close = new Button("Close");
        close.setPrefHeight(36);
        close.setCancelButton(true);
        close.setOnAction(e -> stage.close());

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        HBox buttons = new HBox(10, add, spacer, close);
        buttons.setAlignment(Pos.CENTER_LEFT);

        VBox layout = new VBox(14, heading, hint, scroll, statusLabel, buttons);
        layout.setPadding(new Insets(20));
        layout.setPrefWidth(660);

        stage.setScene(withStyles(new Scene(layout)));
        refresh();
        stage.showAndWait();
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
        if (documents.isEmpty()) {
            Label empty = new Label("No documents added yet.");
            empty.setStyle(MUTED);
            listContainer.getChildren().add(empty);
            return;
        }
        for (CustomerDocument document : documents) {
            listContainer.getChildren().add(row(document));
        }
    }

    private HBox row(CustomerDocument document) {
        Label name = new Label(document.fileName());
        name.setStyle("-fx-font-weight: bold;");
        name.setWrapText(true);

        Label info = new Label(document.type());
        info.setStyle(MUTED);

        VBox text = new VBox(2, name, info);
        HBox.setHgrow(text, Priority.ALWAYS);

        Button remove = new Button("Remove");
        remove.setStyle("-fx-text-fill: red;");
        remove.setOnAction(e -> confirmRemove(document));

        HBox row = new HBox(12, text, remove);
        row.setAlignment(Pos.CENTER_LEFT);
        row.setPadding(new Insets(10, 14, 10, 14));
        row.setStyle("-fx-border-color: #d9d9e3; -fx-border-radius: 6; -fx-background-radius: 6;");
        return row;
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

    // ------------------------------------------------------------------
    // Adding a document
    // ------------------------------------------------------------------

    private void showAddDialog() {
        Stage popup = new Stage();
        popup.initModality(Modality.APPLICATION_MODAL);
        popup.initOwner(stage);
        popup.setTitle("Add Document");

        TextField type = input("e.g. Passport");
        TextField fileName = input("e.g. passport.pdf");
        TextField fileType = input("optional, e.g. pdf");
        TextField size = input("size in bytes");
        digitsOnly(size);

        Button choose = new Button("Choose file...");
        choose.setOnAction(e -> {
            File file = new FileChooser().showOpenDialog(popup);
            if (file != null) {
                fileName.setText(file.getName());
                fileType.setText(extensionOf(file.getName()));
                size.setText(String.valueOf(file.length()));
            }
        });

        GridPane form = new GridPane();
        form.setHgap(12);
        form.setVgap(10);
        form.add(new Label("Type *"), 0, 0);
        form.add(type, 1, 0);
        form.add(new Label("File name *"), 0, 1);
        form.add(fileName, 1, 1);
        form.add(new Label("File type"), 0, 2);
        form.add(fileType, 1, 2);
        form.add(new Label("Size (bytes) *"), 0, 3);
        form.add(size, 1, 3);

        Label required = new Label("* required. Only the details are stored, not the file itself.");
        required.setStyle(MUTED);

        Label error = new Label();
        error.setStyle("-fx-text-fill: #b00020;");
        error.setWrapText(true);
        error.setMaxWidth(380);

        Button save = new Button("Save");
        save.setDefaultButton(true);
        Button cancel = new Button("Cancel");
        cancel.setCancelButton(true);
        cancel.setOnAction(e -> popup.close());

        save.setOnAction(e -> {
            Customer service = context.getCustomerService();
            Long parsedSize = parseSize(size.getText());
            String typeText = type.getText();
            String nameText = fileName.getText();
            String fileTypeText = fileType.getText();

            error.setText("");
            save.setDisable(true);
            Task<Void> task = new Task<>() {
                @Override
                protected Void call() throws Exception {
                    service.addCustomerDocument(customerNumber, typeText, nameText,
                            fileTypeText, parsedSize);
                    return null;
                }
            };
            task.setOnSucceeded(done -> {
                popup.close();
                refresh();
            });
            task.setOnFailed(failed -> {
                save.setDisable(false);
                error.setText(describe(task.getException()));
            });
            start(task, "add-document");
        });

        VBox layout = new VBox(14, choose, form, required, error, new HBox(10, save, cancel));
        layout.setPadding(new Insets(20));
        popup.setScene(withStyles(new Scene(layout)));
        popup.showAndWait();
    }

    // ------------------------------------------------------------------
    // Helpers
    // ------------------------------------------------------------------

    private void setStatus(String message, boolean isError) {
        statusLabel.setText(message);
        statusLabel.setStyle(isError ? "-fx-text-fill: #b00020;" : MUTED);
    }

    private static TextField input(String prompt) {
        TextField field = new TextField();
        field.setPromptText(prompt);
        field.setPrefWidth(260);
        return field;
    }

    private static void digitsOnly(TextField field) {
        field.textProperty().addListener((obs, oldValue, newValue) -> {
            if (newValue != null && !newValue.matches("\\d*")) {
                field.setText(oldValue);
            }
        });
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

    private static String describe(Throwable error) {
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

    private Scene withStyles(Scene scene) {
        URL css = getClass().getResource("/lavs/ui/styles.css");
        if (css != null) {
            scene.getStylesheets().add(css.toExternalForm());
        }
        return scene;
    }

    private static void start(Task<?> task, String name) {
        Thread thread = new Thread(task, name);
        thread.setDaemon(true);
        thread.start();
    }
}