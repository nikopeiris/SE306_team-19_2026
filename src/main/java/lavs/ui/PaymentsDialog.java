package lavs.ui;

import java.io.IOException;
import java.util.List;
import java.util.Optional;

import business.system.LoanDetailsService;
import business.system.dto.PaymentRow;
import javafx.concurrent.Task;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Label;
import javafx.scene.layout.VBox;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.stage.Window;

/** Lists and manages payments for one loan. */
public class PaymentsDialog {
    @FXML private Label hintLabel;
    @FXML private Label emptyLabel;
    @FXML private Label statusLabel;
    @FXML private VBox listContainer;
    @FXML private Button addButton;

    private LoanDetailsService details;
    private String loanNumber;
    private Stage stage;

    static void show(LoanDetailsService details, String loanNumber, Window owner) {
        try {
            FXMLLoader loader = new FXMLLoader(
                    PaymentsDialog.class.getResource("/lavs/ui/payments_dialog.fxml"));
            Parent root = loader.load();
            PaymentsDialog dialog = loader.getController();
            dialog.open(details, loanNumber, owner, root);
        } catch (IOException e) {
            Alert alert = new Alert(Alert.AlertType.ERROR, CustomerDocumentsDialog.describe(e));
            alert.setTitle("Payments");
            alert.setHeaderText("Could not open the payments window");
            alert.showAndWait();
        }
    }

    private void open(LoanDetailsService details, String loanNumber, Window owner, Parent root) {
        this.details = details;
        this.loanNumber = loanNumber;
        hintLabel.setText("Loan " + loanNumber
                + ". Payment changes are saved straight away.");
        stage = new Stage();
        stage.initOwner(owner);
        stage.initModality(Modality.APPLICATION_MODAL);
        stage.setTitle("Payments - Loan " + loanNumber);
        stage.setScene(CustomerDocumentsDialog.styled(new Scene(root)));
        refresh();
        stage.showAndWait();
    }

    @FXML
    private void handleAdd(ActionEvent event) {
        PaymentDialog.show(details, loanNumber, null, stage, this::refresh);
    }

    @FXML
    private void handleClose(ActionEvent event) {
        stage.close();
    }

    private void refresh() {
        setStatus("Loading payments...", false);
        addButton.setDisable(true);
        Task<List<PaymentRow>> task = new Task<>() {
            @Override
            protected List<PaymentRow> call() throws Exception {
                return details.paymentsFor(loanNumber);
            }
        };
        task.setOnSucceeded(e -> {
            addButton.setDisable(false);
            setStatus("", false);
            render(task.getValue());
        });
        task.setOnFailed(e -> {
            addButton.setDisable(false);
            setStatus(CustomerDocumentsDialog.describe(task.getException()), true);
        });
        CustomerDocumentsDialog.start(task, "load-payments");
    }

    private void render(List<PaymentRow> payments) {
        listContainer.getChildren().clear();
        emptyLabel.setVisible(payments.isEmpty());
        emptyLabel.setManaged(payments.isEmpty());
        try {
            for (PaymentRow payment : payments) {
                FXMLLoader loader = new FXMLLoader(
                        getClass().getResource("/lavs/ui/payment_row.fxml"));
                Node row = loader.load();
                PaymentRowController controller = loader.getController();
                controller.setPayment(payment, p -> edit(p), this::confirmDelete);
                listContainer.getChildren().add(row);
            }
        } catch (IOException e) {
            setStatus(CustomerDocumentsDialog.describe(e), true);
        }
    }

    private void edit(PaymentRow payment) {
        PaymentDialog.show(details, loanNumber, payment, stage, this::refresh);
    }

    private void confirmDelete(PaymentRow payment) {
        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION,
                "Delete the payment of " + LoanFormat.money(payment.amountPaid()) + "?",
                ButtonType.YES, ButtonType.NO);
        confirm.setTitle("Delete payment");
        confirm.setHeaderText(null);
        confirm.initOwner(stage);
        Optional<ButtonType> answer = confirm.showAndWait();
        if (answer.isEmpty() || answer.get() != ButtonType.YES) {
            return;
        }
        setStatus("Deleting payment...", false);
        Task<Void> task = new Task<>() {
            @Override
            protected Void call() throws Exception {
                details.deletePayment(loanNumber, payment.id());
                return null;
            }
        };
        task.setOnSucceeded(e -> refresh());
        task.setOnFailed(e -> setStatus(CustomerDocumentsDialog.describe(task.getException()), true));
        CustomerDocumentsDialog.start(task, "delete-payment");
    }

    private void setStatus(String message, boolean error) {
        statusLabel.setText(message);
        statusLabel.setStyle(error ? "-fx-text-fill: #b00020;" : "-fx-text-fill: #726e97;");
    }
}
