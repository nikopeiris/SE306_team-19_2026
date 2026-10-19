package lavs.ui;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.List;

import business.system.Customer;
import business.system.LoanDetailsService;
import business.system.dto.LoanApplication;
import business.system.dto.LoanDetail;
import business.system.dto.RepaymentRow;
import javafx.beans.property.ReadOnlyStringWrapper;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.concurrent.Task;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.layout.GridPane;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.stage.Window;

/**
 * The loan summary window. It shows what LoanDetailsService returns for one loan, the full
 * repayment schedule when asked, and an edit form. No business logic lives here: the form's
 * text and choices go to the business layer as typed, and whether a loan can be edited is
 * whatever the business layer says.
 */
public class LoanSummaryController {

    @FXML private Label messageLabel;
    @FXML private GridPane detailsGrid;
    @FXML private Label nameLabel;
    @FXML private Label loanTypeLabel;
    @FXML private Label termLabel;
    @FXML private Label compoundLabel;
    @FXML private Label amountLabel;
    @FXML private Label paymentFrequencyLabel;
    @FXML private CheckBox interestOnlyCheck;
    @FXML private Label rateLabel;
    @FXML private Label nextPaymentLabel;
    @FXML private Label renewDateLabel;
    @FXML private Label interestPaidLabel;
    @FXML private Label principalPaidLabel;
    @FXML private Label statusLabel;

    @FXML private TableView<RepaymentRow> scheduleTable;
    @FXML private TableColumn<RepaymentRow, String> paymentNumberColumn;
    @FXML private TableColumn<RepaymentRow, String> dateColumn;
    @FXML private TableColumn<RepaymentRow, String> amountColumn;
    @FXML private TableColumn<RepaymentRow, String> principalColumn;
    @FXML private TableColumn<RepaymentRow, String> interestColumn;
    @FXML private TableColumn<RepaymentRow, String> remainingColumn;

    // edit form
    @FXML private GridPane editPane;
    @FXML private ComboBox<String> editLoanTypeCombo;
    @FXML private CheckBox editInterestOnlyCheck;
    @FXML private TextField editAmountField;
    @FXML private TextField editTermField;
    @FXML private ComboBox<String> editCompoundCombo;
    @FXML private TextField editRateField;
    @FXML private ComboBox<String> editPaymentFrequencyCombo;

    @FXML private Button scheduleButton;
    @FXML private Button editButton;
    @FXML private Button saveEditButton;
    @FXML private Button cancelEditButton;
    @FXML private Button closeButton;

    private final ObservableList<RepaymentRow> scheduleRows = FXCollections.observableArrayList();
    private final Label schedulePlaceholder = new Label("");

    private LoanDetailsService details;
    private String loanNumber;
    private Runnable onChanged;
    private LoanDetail current;
    private boolean scheduleRequested = false;

    /**
     * Opens the summary window for a loan.
     *
     * @param onChanged run after the loan has been edited, so the caller can refresh its list; may be null
     */
    static void show(Window owner, Customer customerService, String loanNumber, Runnable onChanged) {
        try {
            FXMLLoader loader = new FXMLLoader(LoanSummaryController.class.getResource("loan_summary.fxml"));
            Parent root = loader.load();
            LoanSummaryController controller = loader.getController();

            Stage stage = new Stage();
            stage.initOwner(owner);
            stage.initModality(Modality.WINDOW_MODAL);
            stage.setTitle("Loan Summary");
            stage.setScene(new Scene(root));
            controller.start(customerService.loanDetails(), loanNumber, onChanged);
            stage.show();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    @FXML
    private void initialize() {
        // only the visible parts take up space
        for (Node node : new Node[] { detailsGrid, scheduleTable, editPane,
                editButton, saveEditButton, cancelEditButton }) {
            node.managedProperty().bind(node.visibleProperty());
        }

        scheduleTable.setItems(scheduleRows);
        scheduleTable.setPlaceholder(schedulePlaceholder);
        paymentNumberColumn.setCellValueFactory(c -> new ReadOnlyStringWrapper(LoanFormat.text(c.getValue().paymentNumber())));
        dateColumn.setCellValueFactory(c -> new ReadOnlyStringWrapper(LoanFormat.text(c.getValue().date())));
        amountColumn.setCellValueFactory(c -> new ReadOnlyStringWrapper(LoanFormat.money(c.getValue().amountPaid())));
        principalColumn.setCellValueFactory(c -> new ReadOnlyStringWrapper(LoanFormat.money(c.getValue().principal())));
        interestColumn.setCellValueFactory(c -> new ReadOnlyStringWrapper(LoanFormat.money(c.getValue().interest())));
        remainingColumn.setCellValueFactory(c -> new ReadOnlyStringWrapper(LoanFormat.money(c.getValue().remainingBalance())));
    }

    private void start(LoanDetailsService details, String loanNumber, Runnable onChanged) {
        this.details = details;
        this.loanNumber = loanNumber;
        this.onChanged = onChanged;
        loadDetail();
    }

    // ------------------------------------------------------------------
    // Showing the loan
    // ------------------------------------------------------------------

    private void loadDetail() {
        messageLabel.setText("Loading...");
        Task<LoanDetail> task = new Task<>() {
            @Override
            protected LoanDetail call() throws Exception {
                return details.loanDetail(loanNumber);
            }
        };
        task.setOnSucceeded(e -> {
            messageLabel.setText("");
            showDetail(task.getValue());
            showView(detailsGrid);
        });
        task.setOnFailed(e -> messageLabel.setText(message(task.getException())));
        startDaemon(task, "loan-summary");
    }

    private void showDetail(LoanDetail d) {
        current = d;
        nameLabel.setText(LoanFormat.text(d.borrowerName()));
        loanTypeLabel.setText(LoanFormat.text(d.loanType()));
        termLabel.setText(LoanFormat.text(d.term()));
        compoundLabel.setText(LoanFormat.text(d.compoundType()));
        amountLabel.setText(LoanFormat.money(d.amount()));
        paymentFrequencyLabel.setText(LoanFormat.text(d.paymentFrequency()));
        interestOnlyCheck.setSelected(d.interestOnly());
        rateLabel.setText(LoanFormat.percent(d.rate()));
        nextPaymentLabel.setText(LoanFormat.money(d.nextPaymentAmount()));
        renewDateLabel.setText(LoanFormat.text(d.renewDate()));
        interestPaidLabel.setText(LoanFormat.money(d.totalInterestPaid()));
        principalPaidLabel.setText(LoanFormat.money(d.totalPrincipalPaid()));
        statusLabel.setText(LoanFormat.text(d.status()));
        editButton.setDisable(!d.editable());
    }

    /** Shows one of the three views (details, payment schedule, edit form) and matching buttons. */
    private void showView(Node view) {
        boolean editing = view == editPane;
        detailsGrid.setVisible(view == detailsGrid);
        scheduleTable.setVisible(view == scheduleTable);
        editPane.setVisible(editing);

        editButton.setVisible(!editing);
        saveEditButton.setVisible(editing);
        cancelEditButton.setVisible(editing);
        scheduleButton.setDisable(editing);
        scheduleButton.setText(view == scheduleTable
                ? "Hide Payment Schedule" : "Show full Payment Schedule");

        if (scheduleButton.getScene() != null) {
            scheduleButton.getScene().getWindow().sizeToScene();
        }
    }

    // ------------------------------------------------------------------
    // Payment schedule
    // ------------------------------------------------------------------

    @FXML
    private void handleToggleSchedule(ActionEvent event) {
        boolean showSchedule = !scheduleTable.isVisible();
        showView(showSchedule ? scheduleTable : detailsGrid);
        if (showSchedule) {
            loadScheduleOnce();
        }
    }

    private void loadScheduleOnce() {
        if (scheduleRequested) {
            return;
        }
        scheduleRequested = true;
        schedulePlaceholder.setText("Loading payment schedule...");

        Task<List<RepaymentRow>> task = new Task<>() {
            @Override
            protected List<RepaymentRow> call() throws Exception {
                return details.repaymentSchedule(loanNumber);
            }
        };
        task.setOnSucceeded(e -> {
            scheduleRows.setAll(task.getValue());
            schedulePlaceholder.setText("No payments scheduled.");
        });
        task.setOnFailed(e -> {
            scheduleRequested = false;   // let the next click try again
            schedulePlaceholder.setText("Could not load the payment schedule.");
            messageLabel.setText(message(task.getException()));
        });
        startDaemon(task, "loan-schedule");
    }

    // ------------------------------------------------------------------
    // Editing
    // ------------------------------------------------------------------

    @FXML
    private void handleEdit(ActionEvent event) {
        if (current == null) {
            return;
        }
        fillEditChoicesOnce();

        LoanApplication form = current.editForm();
        editLoanTypeCombo.setValue(form.loanType());
        editInterestOnlyCheck.setSelected(form.interestOnly());
        editAmountField.setText(form.amount());
        editTermField.setText(form.term());
        editCompoundCombo.setValue(form.compoundType());
        editRateField.setText(form.rate());
        editPaymentFrequencyCombo.setValue(form.paymentFrequency());

        messageLabel.setText("");
        showView(editPane);
    }

    @FXML
    private void handleCancelEdit(ActionEvent event) {
        messageLabel.setText("");
        showView(detailsGrid);
    }

    @FXML
    private void handleSaveEdit(ActionEvent event) {
        final LoanApplication form = new LoanApplication(
                editLoanTypeCombo.getValue(),
                editAmountField.getText(),
                editTermField.getText(),
                editRateField.getText(),
                editCompoundCombo.getValue(),
                editPaymentFrequencyCombo.getValue(),
                editInterestOnlyCheck.isSelected());

        messageLabel.setText("");
        saveEditButton.setDisable(true);

        Task<Void> task = new Task<>() {
            @Override
            protected Void call() throws Exception {
                details.updateLoan(loanNumber, form);
                return null;
            }
        };
        task.setOnSucceeded(e -> {
            saveEditButton.setDisable(false);
            scheduleRequested = false;       // the schedule changes with the loan
            scheduleRows.clear();
            loadDetail();                    // shows the saved loan again
            if (onChanged != null) {
                onChanged.run();
            }
        });
        task.setOnFailed(e -> {
            saveEditButton.setDisable(false);
            messageLabel.setText(message(task.getException()));
        });
        startDaemon(task, "loan-edit");
    }

    private void fillEditChoicesOnce() {
        if (!editLoanTypeCombo.getItems().isEmpty()) {
            return;
        }
        editLoanTypeCombo.setItems(FXCollections.observableArrayList(details.loanTypeOptions()));
        editCompoundCombo.setItems(FXCollections.observableArrayList(details.compoundTypeOptions()));
        editPaymentFrequencyCombo.setItems(FXCollections.observableArrayList(details.paymentFrequencyOptions()));
    }

    // ------------------------------------------------------------------
    // Helpers
    // ------------------------------------------------------------------

    @FXML
    private void handleClose(ActionEvent event) {
        ((Stage) closeButton.getScene().getWindow()).close();
    }

    private static String message(Throwable ex) {
        if (ex instanceof IOException) {
            return "Could not reach the mainframe. Please try again.";
        }
        String text = ex == null ? null : ex.getMessage();
        return (text == null || text.isBlank()) ? "Something went wrong." : text;
    }

    private static void startDaemon(Task<?> task, String name) {
        Thread thread = new Thread(task, name);
        thread.setDaemon(true);
        thread.start();
    }
}