package lavs.ui;

import java.io.IOException;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.Callable;
import java.util.function.Consumer;

import business.lavsexceptions.LavsException;
import business.system.Customer;
import business.system.LoanCoborrowerService;
import business.system.dto.LoanCoborrower;
import business.system.dto.LoanSummary;
import javafx.collections.FXCollections;
import javafx.beans.property.SimpleStringProperty;
import javafx.concurrent.Task;
import javafx.geometry.Insets;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.ChoiceDialog;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.stage.Window;

/**
 * A dialog for the co-borrowers and guarantors of one loan: view, add, change the type of
 * and remove them. The rules (who may be added, when changes are allowed) belong to the
 * business layer; this class only shows the results and any problems it reports.
 */
final class LoanCoborrowersDialog {
    private static final String ERROR_STYLE = "-fx-text-fill: #b00020;";
    private static final String INFO_STYLE = "-fx-text-fill: #5b6670;";

    private final Window owner;
    private final Customer customerService;
    private final LoanSummary loan;
    private final boolean editable;

    private final TableView<LoanCoborrower> table = new TableView<>();
    private final Label message = new Label();
    private final Button addButton = new Button("Add...");
    private final Button changeTypeButton = new Button("Change Type...");
    private final Button removeButton = new Button("Remove");
    private final Button closeButton = new Button("Close");
    private Stage popup;

    private LoanCoborrowersDialog(Window owner, Customer customerService, LoanSummary loan) {
        this.owner = owner;
        this.customerService = customerService;
        this.loan = loan;
        this.editable = LoanCoborrowerService.canChange(loan.getStatus());
    }

    /** Shows the dialog for a loan and waits for it to close. */
    static void show(Window owner, Customer customerService, LoanSummary loan) {
        new LoanCoborrowersDialog(owner, customerService, loan).open();
    }

    private void open() {
        popup = new Stage();
        popup.initModality(Modality.APPLICATION_MODAL);
        popup.initOwner(owner);
        popup.setTitle("Co-borrowers - Loan " + loan.getLoanNumber());

        Label heading = new Label("Loan " + loan.getLoanNumber() + " for " + loan.getCustomerName()
                + " (" + loan.getStatus() + ")");
        heading.setStyle("-fx-font-weight: bold;");

        buildTable();
        message.setWrapText(true);
        message.setMaxWidth(520);
        if (!editable) {
            showMessage("Co-borrowers can only be changed while a loan is New.", false);
        }

        addButton.setOnAction(e -> showAddDialog());
        changeTypeButton.setOnAction(e -> changeType());
        removeButton.setOnAction(e -> remove());
        closeButton.setCancelButton(true);
        closeButton.setOnAction(e -> popup.close());
        addButton.setDisable(!editable);
        changeTypeButton.disableProperty().bind(
                table.getSelectionModel().selectedItemProperty().isNull());
        removeButton.disableProperty().bind(
                table.getSelectionModel().selectedItemProperty().isNull());
        if (!editable) {
            changeTypeButton.disableProperty().unbind();
            removeButton.disableProperty().unbind();
            changeTypeButton.setDisable(true);
            removeButton.setDisable(true);
        }

        VBox.setVgrow(table, Priority.ALWAYS);
        VBox layout = new VBox(12, heading, table, message,
                new HBox(10, addButton, changeTypeButton, removeButton, closeButton));
        layout.setPadding(new Insets(20));
        popup.setScene(new Scene(layout, 560, 420));

        reload();
        popup.showAndWait();
    }

    private void buildTable() {
        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);
        table.setPlaceholder(new Label("Loading..."));
        table.getColumns().add(column("Name", LoanCoborrower::name));
        table.getColumns().add(column("Customer Number", LoanCoborrower::customerNumber));
        table.getColumns().add(column("Type", LoanCoborrower::type));
    }

    private static TableColumn<LoanCoborrower, String> column(String title,
            java.util.function.Function<LoanCoborrower, String> value) {
        TableColumn<LoanCoborrower, String> column = new TableColumn<>(title);
        column.setCellValueFactory(row -> new SimpleStringProperty(value.apply(row.getValue())));
        return column;
    }

    // ------------------------------------------------------------------
    // Actions
    // ------------------------------------------------------------------

    private void reload() {
        run("Loading...", () -> customerService.listLoanCoborrowers(loan.getLoanNumber()),
                coborrowers -> {
                    table.setItems(FXCollections.observableArrayList(coborrowers));
                    table.setPlaceholder(new Label("This loan has no co-borrowers."));
                });
    }

    private void showAddDialog() {
        TextField customerNumber = new TextField();
        customerNumber.setPromptText("Customer number, e.g. from the customer list");
        customerNumber.setPrefWidth(280);
        ComboBox<String> type = new ComboBox<>(
                FXCollections.observableArrayList(LoanCoborrowerService.TYPES));
        type.setValue(LoanCoborrowerService.TYPES.get(0));

        GridPane form = new GridPane();
        form.setHgap(12);
        form.setVgap(8);
        form.addRow(0, new Label("Customer"), customerNumber);
        form.addRow(1, new Label("Type"), type);

        Label error = new Label();
        error.setWrapText(true);
        error.setMaxWidth(380);
        error.setStyle(ERROR_STYLE);

        Stage addPopup = new Stage();
        addPopup.initModality(Modality.APPLICATION_MODAL);
        addPopup.initOwner(popup);
        addPopup.setTitle("Add Co-borrower");
        Button add = new Button("Add");
        add.setDefaultButton(true);
        Button cancel = new Button("Cancel");
        cancel.setCancelButton(true);
        cancel.setOnAction(e -> addPopup.close());

        add.setOnAction(e -> {
            error.setText("");
            add.setDisable(true);
            Task<Void> task = new Task<>() {
                @Override
                protected Void call() throws Exception {
                    customerService.addLoanCoborrower(loan.getLoanNumber(),
                            customerNumber.getText(), type.getValue());
                    return null;
                }
            };
            task.setOnSucceeded(done -> {
                addPopup.close();
                reload();
            });
            task.setOnFailed(failed -> {
                // Keep what was typed so one wrong value is a quick fix
                error.setText(describe(task.getException()));
                add.setDisable(false);
            });
            start(task, "add-coborrower");
        });

        VBox layout = new VBox(12, form, error, new HBox(10, add, cancel));
        layout.setPadding(new Insets(20));
        addPopup.setScene(new Scene(layout));
        addPopup.showAndWait();
    }

    private void changeType() {
        LoanCoborrower selected = table.getSelectionModel().getSelectedItem();
        if (selected == null) {
            return;
        }
        List<String> otherTypes = LoanCoborrowerService.TYPES.stream()
                .filter(type -> !type.equals(selected.type())).toList();
        ChoiceDialog<String> chooser = new ChoiceDialog<>(otherTypes.get(0), otherTypes);
        chooser.initOwner(popup);
        chooser.setTitle("Change Type");
        chooser.setHeaderText("Change the type for " + selected.name());
        chooser.setContentText("New type:");
        Optional<String> newType = chooser.showAndWait();
        newType.ifPresent(type -> run("Changing type...", () -> {
            customerService.changeLoanCoborrowerType(loan.getLoanNumber(), selected, type);
            return customerService.listLoanCoborrowers(loan.getLoanNumber());
        }, table2 -> table.setItems(FXCollections.observableArrayList(table2))));
    }

    private void remove() {
        LoanCoborrower selected = table.getSelectionModel().getSelectedItem();
        if (selected == null) {
            return;
        }
        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION,
                "Remove " + selected.name() + " (" + selected.customerNumber()
                + ") from this loan?", ButtonType.OK, ButtonType.CANCEL);
        confirm.initOwner(popup);
        confirm.setHeaderText(null);
        Optional<ButtonType> answer = confirm.showAndWait();
        if (answer.isPresent() && answer.get() == ButtonType.OK) {
            run("Removing...", () -> {
                customerService.removeLoanCoborrower(loan.getLoanNumber(), selected);
                return customerService.listLoanCoborrowers(loan.getLoanNumber());
            }, remaining -> table.setItems(FXCollections.observableArrayList(remaining)));
        }
    }

    // ------------------------------------------------------------------
    // Helpers
    // ------------------------------------------------------------------

    /** Runs work on a background thread, keeping the buttons off until it finishes. */
    private <T> void run(String busyText, Callable<T> work, Consumer<T> onSuccess) {
        showMessage(busyText, false);
        setBusy(true);
        Task<T> task = new Task<>() {
            @Override
            protected T call() throws Exception {
                return work.call();
            }
        };
        task.setOnSucceeded(e -> {
            onSuccess.accept(task.getValue());
            showMessage(editable ? "" : "Co-borrowers can only be changed while a loan is New.",
                    false);
            setBusy(false);
        });
        task.setOnFailed(e -> {
            table.setPlaceholder(new Label("Co-borrowers could not be loaded."));
            showMessage(describe(task.getException()), true);
            setBusy(false);
        });
        start(task, "loan-coborrowers-" + loan.getLoanNumber());
    }

    private void setBusy(boolean busy) {
        table.setDisable(busy);
        addButton.setDisable(busy || !editable);
        if (editable) {
            changeTypeButton.disableProperty().unbind();
            removeButton.disableProperty().unbind();
            changeTypeButton.setDisable(busy);
            removeButton.setDisable(busy);
            if (!busy) {
                changeTypeButton.disableProperty().bind(
                        table.getSelectionModel().selectedItemProperty().isNull());
                removeButton.disableProperty().bind(
                        table.getSelectionModel().selectedItemProperty().isNull());
            }
        }
    }

    private void showMessage(String text, boolean isError) {
        message.setText(text);
        message.setStyle(isError ? ERROR_STYLE : INFO_STYLE);
    }

    private static void start(Task<?> task, String name) {
        Thread thread = new Thread(task, name);
        thread.setDaemon(true);
        thread.start();
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
