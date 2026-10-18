package lavs.ui;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.List;

import business.system.Customer;
import business.system.LoanDetailsService;
import business.system.dto.LoanDetail;
import business.system.dto.RepaymentRow;
import javafx.beans.property.ReadOnlyStringWrapper;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.concurrent.Task;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.Label;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.layout.GridPane;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.stage.Window;

/**
 * The loan summary window. It shows what LoanDetailsService returns for one loan, and the
 * full repayment schedule when asked. No business logic lives here.
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
    @FXML private Button scheduleButton;

    private final ObservableList<RepaymentRow> scheduleRows = FXCollections.observableArrayList();
    private final Label schedulePlaceholder = new Label("");

    private LoanDetailsService details;
    private String loanNumber;
    private boolean scheduleRequested = false;

    /** Opens the summary window for a loan. */
    static void show(Window owner, Customer customerService, String loanNumber) {
        try {
            FXMLLoader loader = new FXMLLoader(LoanSummaryController.class.getResource("loan_summary.fxml"));
            Parent root = loader.load();
            LoanSummaryController controller = loader.getController();

            Stage stage = new Stage();
            stage.initOwner(owner);
            stage.initModality(Modality.WINDOW_MODAL);
            stage.setTitle("Loan Summary");
            stage.setScene(new Scene(root));
            controller.start(customerService.loanDetails(), loanNumber);
            stage.show();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    @FXML
    private void initialize() {
        // only the visible one takes up space in the window
        detailsGrid.managedProperty().bind(detailsGrid.visibleProperty());
        scheduleTable.managedProperty().bind(scheduleTable.visibleProperty());

        scheduleTable.setItems(scheduleRows);
        scheduleTable.setPlaceholder(schedulePlaceholder);
        paymentNumberColumn.setCellValueFactory(c -> new ReadOnlyStringWrapper(LoanFormat.text(c.getValue().paymentNumber())));
        dateColumn.setCellValueFactory(c -> new ReadOnlyStringWrapper(LoanFormat.text(c.getValue().date())));
        amountColumn.setCellValueFactory(c -> new ReadOnlyStringWrapper(LoanFormat.money(c.getValue().amountPaid())));
        principalColumn.setCellValueFactory(c -> new ReadOnlyStringWrapper(LoanFormat.money(c.getValue().principal())));
        interestColumn.setCellValueFactory(c -> new ReadOnlyStringWrapper(LoanFormat.money(c.getValue().interest())));
        remainingColumn.setCellValueFactory(c -> new ReadOnlyStringWrapper(LoanFormat.money(c.getValue().remainingBalance())));
    }

    private void start(LoanDetailsService details, String loanNumber) {
        this.details = details;
        this.loanNumber = loanNumber;
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
        });
        task.setOnFailed(e -> messageLabel.setText(message(task.getException())));
        startDaemon(task, "loan-summary");
    }

    private void showDetail(LoanDetail d) {
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
    }

    @FXML
    private void handleToggleSchedule(ActionEvent event) {
        boolean showSchedule = !scheduleTable.isVisible();
        scheduleTable.setVisible(showSchedule);
        detailsGrid.setVisible(!showSchedule);
        scheduleButton.setText(showSchedule ? "Hide Payment Schedule" : "Show full Payment Schedule");
        if (showSchedule) {
            loadScheduleOnce();
        }
        scheduleTable.getScene().getWindow().sizeToScene();
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

    @FXML
    private void handleClose(ActionEvent event) {
        ((Stage) scheduleButton.getScene().getWindow()).close();
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
