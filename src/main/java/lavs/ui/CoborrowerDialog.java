package lavs.ui;

import java.io.IOException;
import java.util.List;
import java.util.function.Consumer;

import business.system.LoanDetailsService;
import business.system.dto.CoborrowerRow;
import business.system.dto.CustomerSummary;
import javafx.collections.FXCollections;
import javafx.concurrent.Task;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.control.TextField;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.stage.Window;

/**
 * Add/edit form for a co-borrower or guarantor. The customer is found by name or typed in as a
 * customer number; the business layer checks the choice (see LoanDetailsService).
 */
public class CoborrowerDialog {
    public record Selection(String customerNumber, String type, String displayName) {}

    @FXML private TextField searchField;
    @FXML private Button searchButton;
    @FXML private Label searchStatusLabel;
    @FXML private ListView<CustomerSummary> resultsList;
    @FXML private TextField customerNumberField;
    @FXML private ComboBox<String> typeCombo;
    @FXML private Label errorLabel;
    @FXML private Button saveButton;

    private LoanDetailsService details;
    private String loanNumber;
    private CoborrowerRow existing;
    private Runnable onSaved;
    private Consumer<Selection> onSelected;
    private boolean selectionOnly;
    private Stage stage;

    static void show(LoanDetailsService details, String loanNumber, CoborrowerRow existing,
            Window owner, Runnable onSaved) {
        show(details, loanNumber, existing, owner, onSaved, null);
    }

    static void showForApplication(LoanDetailsService details, Window owner,
            Consumer<Selection> onSelected) {
        show(details, null, null, owner, null, onSelected);
    }

    private static void show(LoanDetailsService details, String loanNumber, CoborrowerRow existing,
            Window owner, Runnable onSaved, Consumer<Selection> onSelected) {
        try {
            FXMLLoader loader = new FXMLLoader(
                    CoborrowerDialog.class.getResource("/lavs/ui/coborrower_dialog.fxml"));
            Parent root = loader.load();
            CoborrowerDialog dialog = loader.getController();
            dialog.open(details, loanNumber, existing, owner, onSaved, onSelected, root);
        } catch (IOException e) {
            Alert alert = new Alert(Alert.AlertType.ERROR, CustomerDocumentsDialog.describe(e));
            alert.setTitle("Co-borrower");
            alert.setHeaderText("Could not open the co-borrower form");
            alert.showAndWait();
        }
    }

    @FXML
    private void initialize() {
        resultsList.setPlaceholder(new Label(""));
        resultsList.setCellFactory(list -> new ListCell<>() {
            @Override
            protected void updateItem(CustomerSummary customer, boolean empty) {
                super.updateItem(customer, empty);
                setText(empty || customer == null
                        ? null : customer.getFullName() + " (" + customer.getNumber() + ")");
            }
        });
        // picking a search result fills in the customer number
        resultsList.getSelectionModel().selectedItemProperty().addListener((o, was, picked) -> {
            if (picked != null) {
                customerNumberField.setText(picked.getNumber());
            }
        });
    }

    private void open(LoanDetailsService details, String loanNumber, CoborrowerRow existing,
            Window owner, Runnable onSaved, Consumer<Selection> onSelected, Parent root) {
        this.details = details;
        this.loanNumber = loanNumber;
        this.existing = existing;
        this.onSaved = onSaved;
        this.onSelected = onSelected;
        this.selectionOnly = onSelected != null;

        typeCombo.setItems(FXCollections.observableArrayList(details.coborrowerTypeOptions()));
        if (existing == null) {
            typeCombo.getSelectionModel().selectFirst();
        } else {
            customerNumberField.setText(existing.customerNumber());
            typeCombo.setValue(existing.type());
        }

        stage = new Stage();
        stage.initOwner(owner);
        stage.initModality(Modality.APPLICATION_MODAL);
        stage.setTitle(selectionOnly ? "Add Co-borrower or Guarantor"
                : existing == null ? "Add Co-borrower" : "Edit Co-borrower");
        stage.setScene(CustomerDocumentsDialog.styled(new Scene(root)));
        stage.showAndWait();
    }

    @FXML
    private void handleSearch(ActionEvent event) {
        final String term = searchField.getText();
        searchStatusLabel.setText("Searching...");
        searchButton.setDisable(true);
        Task<List<CustomerSummary>> task = new Task<>() {
            @Override
            protected List<CustomerSummary> call() throws Exception {
                return details.searchCustomers(term);
            }
        };
        task.setOnSucceeded(e -> {
            searchButton.setDisable(false);
            resultsList.setItems(FXCollections.observableArrayList(task.getValue()));
            searchStatusLabel.setText(task.getValue().isEmpty() ? "No customers found." : "");
        });
        task.setOnFailed(e -> {
            searchButton.setDisable(false);
            searchStatusLabel.setText(CustomerDocumentsDialog.describe(task.getException()));
        });
        CustomerDocumentsDialog.start(task, "search-coborrower");
    }

    @FXML
    private void handleSave(ActionEvent event) {
        errorLabel.setText("");
        if (selectionOnly) {
            String customerNumber = customerNumberField.getText();
            String type = typeCombo.getValue();
            if (customerNumber == null || customerNumber.isBlank()
                    || type == null || type.isBlank()) {
                errorLabel.setText("Customer number and role are required.");
                return;
            }
            onSelected.accept(new Selection(customerNumber.trim(), type,
                    resultsList.getSelectionModel().getSelectedItem() == null
                            ? customerNumber.trim()
                            : resultsList.getSelectionModel().getSelectedItem().getFullName()));
            stage.close();
            return;
        }
        saveButton.setDisable(true);
        final String customerNumber = customerNumberField.getText();
        final String type = typeCombo.getValue();
        Task<Void> task = new Task<>() {
            @Override
            protected Void call() throws Exception {
                if (existing == null) {
                    details.addCoborrower(loanNumber, customerNumber, type);
                } else {
                    details.updateCoborrower(loanNumber, existing.id(), customerNumber, type);
                }
                return null;
            }
        };
        task.setOnSucceeded(e -> {
            stage.close();
            onSaved.run();
        });
        task.setOnFailed(e -> {
            saveButton.setDisable(false);
            errorLabel.setText(CustomerDocumentsDialog.describe(task.getException()));
        });
        CustomerDocumentsDialog.start(task, "save-coborrower");
    }

    @FXML
    private void handleCancel(ActionEvent event) {
        stage.close();
    }
}
