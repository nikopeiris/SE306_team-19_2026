package lavs.ui;

import java.io.IOException;
import java.util.List;
import java.util.function.Consumer;

import business.lavsexceptions.LavsException;
import business.system.Customer;
import business.system.dto.LoanSummary;
import javafx.collections.FXCollections;
import javafx.concurrent.Task;
import javafx.geometry.Insets;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.stage.Window;

/**
 * A dialog for changing the status of one loan. Which statuses are offered and whether
 * the change is allowed are decided by the business layer; this class only shows them.
 */
final class LoanStatusDialog {
    private static final String ERROR_STYLE = "-fx-text-fill: #b00020;";
    private static final String INFO_STYLE = "-fx-text-fill: #5b6670;";

    private LoanStatusDialog() {
    }

    /**
     * Shows the dialog and waits for it to close.
     *
     * @param onChanged told the new status once the mainframe has accepted the change
     */
    static void show(Window owner, Customer customerService, LoanSummary loan,
            Consumer<String> onChanged) {
        List<String> choices = customerService.allowedLoanStatuses(loan.getStatus());

        Stage popup = new Stage();
        popup.initModality(Modality.APPLICATION_MODAL);
        popup.initOwner(owner);
        popup.setTitle("Change Loan Status");

        GridPane details = new GridPane();
        details.setHgap(12);
        details.setVgap(8);
        details.addRow(0, new Label("Loan"), bold(loan.getLoanNumber()));
        details.addRow(1, new Label("Customer"), bold(loan.getCustomerName()));
        details.addRow(2, new Label("Current status"), bold(loan.getStatus()));

        Label message = new Label();
        message.setWrapText(true);
        message.setMaxWidth(380);

        Button cancel = new Button("Close");
        cancel.setCancelButton(true);
        cancel.setOnAction(e -> popup.close());

        VBox layout = new VBox(14, details);
        layout.setPadding(new Insets(20));

        if (choices.isEmpty()) {
            message.setText("A loan that is " + loan.getStatus()
                    + " can no longer be changed here.");
            message.setStyle(INFO_STYLE);
            layout.getChildren().addAll(message, new HBox(10, cancel));
        } else {
            ComboBox<String> newStatus = new ComboBox<>(
                    FXCollections.observableArrayList(choices));
            newStatus.setValue(choices.get(0));
            newStatus.setPrefWidth(200);
            details.addRow(3, new Label("New status"), newStatus);

            Button apply = new Button("Change Status");
            apply.setDefaultButton(true);
            apply.setOnAction(e -> apply(customerService, loan, newStatus, message, popup,
                    onChanged, apply, newStatus, cancel));
            layout.getChildren().addAll(message, new HBox(10, apply, cancel));
        }

        popup.setScene(new Scene(layout));
        popup.showAndWait();
    }

    /** Sends the change on a background thread so the screen never freezes. */
    private static void apply(Customer customerService, LoanSummary loan,
            ComboBox<String> newStatus, Label message, Stage popup,
            Consumer<String> onChanged, javafx.scene.Node... controls) {
        String target = newStatus.getValue();
        message.setStyle(INFO_STYLE);
        message.setText("Changing status...");
        for (javafx.scene.Node control : controls) {
            control.setDisable(true);
        }

        Task<Void> task = new Task<>() {
            @Override
            protected Void call() throws Exception {
                customerService.changeLoanStatus(loan.getLoanNumber(), target);
                return null;
            }
        };
        task.setOnSucceeded(e -> {
            popup.close();
            onChanged.accept(target);
        });
        task.setOnFailed(e -> {
            // Stay open so the user can read what is missing and try again
            message.setStyle(ERROR_STYLE);
            message.setText(describe(task.getException()));
            for (javafx.scene.Node control : controls) {
                control.setDisable(false);
            }
        });

        Thread thread = new Thread(task, "change-loan-status-" + loan.getLoanNumber());
        thread.setDaemon(true);
        thread.start();
    }

    private static Label bold(String text) {
        Label label = new Label(text == null || text.isBlank() ? "-" : text);
        label.setStyle("-fx-font-weight: bold;");
        return label;
    }

    private static String describe(Throwable error) {
        if (error instanceof LavsException) {
            return error.getMessage();
        } else if (error instanceof IOException) {
            return "Could not connect to the server.";
        }
        return error != null && error.getMessage() != null
                ? error.getMessage() : "Something went wrong.";
    }
}
