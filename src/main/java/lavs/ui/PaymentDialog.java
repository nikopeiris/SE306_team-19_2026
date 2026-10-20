package lavs.ui;

import java.io.IOException;

import business.system.LoanDetailsService;
import business.system.dto.PaymentRow;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.stage.Window;

/** Add/edit form for a payment. */
public class PaymentDialog {
    @FXML private TextField amountField;
    @FXML private TextField dateField;
    @FXML private Label errorLabel;
    @FXML private Button saveButton;

    private LoanDetailsService details;
    private String loanNumber;
    private PaymentRow existing;
    private Runnable onSaved;
    private Stage stage;

    static void show(LoanDetailsService details, String loanNumber, PaymentRow existing,
            Window owner, Runnable onSaved) {
        try {
            FXMLLoader loader = new FXMLLoader(
                    PaymentDialog.class.getResource("/lavs/ui/payment_dialog.fxml"));
            Parent root = loader.load();
            PaymentDialog dialog = loader.getController();
            dialog.open(details, loanNumber, existing, owner, onSaved, root);
        } catch (IOException e) {
            Alert alert = new Alert(Alert.AlertType.ERROR, CustomerDocumentsDialog.describe(e));
            alert.setTitle("Payment");
            alert.setHeaderText("Could not open the payment form");
            alert.showAndWait();
        }
    }

    private void open(LoanDetailsService details, String loanNumber, PaymentRow existing,
            Window owner, Runnable onSaved, Parent root) {
        this.details = details;
        this.loanNumber = loanNumber;
        this.existing = existing;
        this.onSaved = onSaved;
        if (existing != null) {
            amountField.setText(Double.toString(existing.amountPaid()));
            dateField.setText(existing.date());
        }
        stage = new Stage();
        stage.initOwner(owner);
        stage.initModality(Modality.APPLICATION_MODAL);
        stage.setTitle(existing == null ? "Add Payment" : "Edit Payment");
        stage.setScene(CustomerDocumentsDialog.styled(new Scene(root)));
        stage.showAndWait();
    }

    @FXML
    private void handleSave(ActionEvent event) {
        errorLabel.setText("");
        saveButton.setDisable(true);
        double amount;
        try {
            amount = Double.parseDouble(amountField.getText().trim());
        } catch (RuntimeException e) {
            saveButton.setDisable(false);
            errorLabel.setText("Payment amount must be a number.");
            return;
        }
        String date = dateField.getText();
        javafx.concurrent.Task<Void> task = new javafx.concurrent.Task<>() {
            @Override
            protected Void call() throws Exception {
                if (existing == null) {
                    details.addPayment(loanNumber, amount, date);
                } else {
                    details.updatePayment(loanNumber, existing.id(), amount, date);
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
        CustomerDocumentsDialog.start(task, "save-payment");
    }

    @FXML
    private void handleCancel(ActionEvent event) {
        stage.close();
    }
}
