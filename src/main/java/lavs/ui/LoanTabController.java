package lavs.ui;

import java.io.IOException;
import java.util.List;

import business.system.LoanDetailsService;
import business.system.dto.LoanApplication;
import business.system.dto.LoanRow;
import javafx.beans.property.ReadOnlyStringWrapper;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.concurrent.Task;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableRow;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.layout.Pane;

/**
 * The customer screen's Loan tab. This class only moves values between the screen and the
 * business layer: it shows what the business layer returns and sends back what was typed
 * or picked. Parsing, validation, defaults and calculations are in LoanDetailsService.
 */
public class LoanTabController {

    @FXML private Pane loanTabRoot;
    @FXML private TableView<LoanRow> loansTable;
    @FXML private TableColumn<LoanRow, String> loanIdColumn;
    @FXML private TableColumn<LoanRow, String> loanTypeColumn;
    @FXML private TableColumn<LoanRow, String> loanAmountColumn;
    @FXML private TableColumn<LoanRow, String> loanDateCreatedColumn;
    @FXML private TableColumn<LoanRow, String> loanStatusColumn;
    @FXML private Button addLoanButton;
    @FXML private Button viewLoanButton;
    @FXML private Label messageLabel;

    // ---- add loan overlay ----
    @FXML private Pane addLoanPane;
    @FXML private ComboBox<String> loanTypeCombo;
    @FXML private TextField amountField;
    @FXML private TextField termField;
    @FXML private ComboBox<String> compoundCombo;
    @FXML private TextField rateField;
    @FXML private ComboBox<String> paymentFrequencyCombo;
    @FXML private CheckBox interestOnlyCheck;
    @FXML private Button coborrowersButton;
    @FXML private Label addLoanErrorLabel;
    @FXML private Button submitLoanButton;

    private CustomerContext context;

    private final ObservableList<LoanRow> loans = FXCollections.observableArrayList();
    private final Label placeholder = new Label("");

    /** The customer whose loans are on screen. */
    private String loadedFor = null;
    /** Bumped when a load starts or is abandoned, so late results from an old load are ignored. */
    private volatile int generation = 0;

    // ------------------------------------------------------------------
    // Setup
    // ------------------------------------------------------------------

    @FXML
    private void initialize() {
        loansTable.setItems(loans);
        loansTable.setPlaceholder(placeholder);
        loanIdColumn.setCellValueFactory(c -> new ReadOnlyStringWrapper(LoanFormat.text(c.getValue().loanNumber())));
        loanTypeColumn.setCellValueFactory(c -> new ReadOnlyStringWrapper(LoanFormat.text(c.getValue().type())));
        loanAmountColumn.setCellValueFactory(c -> new ReadOnlyStringWrapper(LoanFormat.money(c.getValue().amount())));
        loanDateCreatedColumn.setCellValueFactory(c -> new ReadOnlyStringWrapper(LoanFormat.text(c.getValue().dateCreated())));
        loanStatusColumn.setCellValueFactory(c -> new ReadOnlyStringWrapper(LoanFormat.text(c.getValue().status())));

        // double-clicking a loan opens its summary
        loansTable.setRowFactory(tv -> {
            TableRow<LoanRow> row = new TableRow<>();
            row.setOnMouseClicked(e -> {
                if (e.getClickCount() == 2 && !row.isEmpty()) {
                    openSummary(row.getItem());
                }
            });
            return row;
        });

        // a loan has to be picked before it can be viewed
        viewLoanButton.disableProperty().bind(
                loansTable.getSelectionModel().selectedItemProperty().isNull());
    }

    /** Called by the customer screen once the shared context is ready. */
    public void init(CustomerContext context) {
        // The services are not set on the context yet while the customer screen is still
        // loading, so nothing here may use them (the dropdowns are filled in handleAddLoan).
        this.context = context;

        loanTabRoot.sceneProperty().addListener((o, was, scene) -> {
            if (scene == null) {
                generation++;       // leaving the screen: ignore any load still running
                loadedFor = null;
            } else {
                refreshIfNeeded();
            }
        });
        loanTabRoot.visibleProperty().addListener((o, was, visible) -> {
            if (visible) {
                refreshIfNeeded();
            }
        });
        refreshIfNeeded();
    }

    /**
     * Reloads the loans. The customer screen should call this after it loads or saves a
     * customer, since a new customer only has a number once saved.
     */
    public void refresh() {
        loadedFor = null;
        refreshIfNeeded();
    }

    /** Called by the customer screen each time this tab is opened. */
    public void onShown() {
        refreshIfNeeded();
    }

    private LoanDetailsService details() {
        return context.getCustomerService().loanDetails();
    }

    // ------------------------------------------------------------------
    // Loading the table
    // ------------------------------------------------------------------

    private void refreshIfNeeded() {
        if (context == null) {
            return;
        }
        String number = context.getCustomerNumber();
        addLoanButton.setDisable(number == null);
        if (number == null) {
            generation++;
            loadedFor = null;
            loans.clear();
            placeholder.setText("");
            messageLabel.setText("Save the customer to add or view loans.");
            return;
        }
        if (number.equals(loadedFor)) {
            return;
        }
        loadedFor = number;
        loadLoans(number);
    }

    private void loadLoans(String number) {
        final int gen = ++generation;
        final LoanDetailsService details = details();
        loans.clear();
        placeholder.setText("Loading loans...");
        messageLabel.setText("");

        Task<List<LoanRow>> task = new Task<>() {
            @Override
            protected List<LoanRow> call() throws Exception {
                return details.loansFor(number);
            }
        };
        task.setOnSucceeded(e -> {
            if (gen == generation) {
                loans.setAll(task.getValue());
                placeholder.setText("No loans yet.");
            }
        });
        task.setOnFailed(e -> {
            if (gen == generation) {
                placeholder.setText("Could not load loans.");
                messageLabel.setText(message(task.getException()));
                loadedFor = null;   // try again next time the tab is shown
            }
        });
        startDaemon(task, "loan-tab-load");
    }

    // ------------------------------------------------------------------
    // Viewing a loan
    // ------------------------------------------------------------------

    @FXML
    private void handleViewLoan(ActionEvent event) {
        LoanRow selected = loansTable.getSelectionModel().getSelectedItem();
        if (selected != null) {
            openSummary(selected);
        }
    }

    private void openSummary(LoanRow loan) {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("loan_summary.fxml"));
            Parent loanOverlay = loader.load();

            LoanSummaryController controller = loader.getController();
            controller.start(context.getCustomerService().loanDetails(), loan.loanNumber(), this::refresh);
            controller.setOnCloseAction(() -> loanTabRoot.getChildren().remove(loanOverlay));
            loanTabRoot.getChildren().add(loanOverlay);

        } catch (Exception e) {
            e.printStackTrace();
        }
    }



    // ------------------------------------------------------------------
    // Adding a loan
    // ------------------------------------------------------------------

    @FXML
    private void handleAddLoan(ActionEvent event) {
        fillChoicesOnce();
        addLoanErrorLabel.setText("");
        if (addLoanPane != null) {
            addLoanPane.setVisible(true);
            addLoanPane.setManaged(true);
        }
    }

    @FXML
    private void handleCancelLoan(ActionEvent event) {
        closeOverlay();
    }

    @FXML
    private void handleSubmitLoan(ActionEvent event) {
        final String number = context.getCustomerNumber();
        final LoanApplication application = new LoanApplication(
                loanTypeCombo.getValue(),
                amountField.getText(),
                termField.getText(),
                rateField.getText(),
                compoundCombo.getValue(),
                paymentFrequencyCombo.getValue(),
                interestOnlyCheck.isSelected());
        final LoanDetailsService details = details();

        addLoanErrorLabel.setText("");
        submitLoanButton.setDisable(true);

        Task<String> task = new Task<>() {
            @Override
            protected String call() throws Exception {
                return details.addLoan(number, application).getLoanNumber();
            }
        };
        task.setOnSucceeded(e -> {
            submitLoanButton.setDisable(false);
            closeOverlay();
            resetForm();
            refresh();
            messageLabel.setText("Loan " + task.getValue() + " created.");
        });
        task.setOnFailed(e -> {
            submitLoanButton.setDisable(false);
            addLoanErrorLabel.setText(message(task.getException()));
        });
        startDaemon(task, "add-loan");
    }

    /** Placeholder: co-borrowers are not built yet. */
    @FXML
    private void handleCoborrowers(ActionEvent event) {
    }

    private void closeOverlay() {
        addLoanPane.setVisible(false);
        addLoanPane.setManaged(false);
        addLoanErrorLabel.setText("");
    }

    /** Fills the dropdowns from the business layer the first time the form is opened. */
    private void fillChoicesOnce() {
        if (!loanTypeCombo.getItems().isEmpty()) {
            return;
        }
        LoanDetailsService details = details();
        loanTypeCombo.setItems(FXCollections.observableArrayList(details.loanTypeOptions()));
        compoundCombo.setItems(FXCollections.observableArrayList(details.compoundTypeOptions()));
        paymentFrequencyCombo.setItems(FXCollections.observableArrayList(details.paymentFrequencyOptions()));
        resetForm();
    }

    /** Puts the form back to a blank state. */
    private void resetForm() {
        loanTypeCombo.getSelectionModel().selectFirst();
        compoundCombo.getSelectionModel().selectFirst();
        paymentFrequencyCombo.getSelectionModel().selectFirst();
        amountField.clear();
        termField.clear();
        rateField.clear();
        interestOnlyCheck.setSelected(false);
    }

    // ------------------------------------------------------------------
    // Navigation
    // ------------------------------------------------------------------

    @FXML
    private void handleBack(ActionEvent event) {
        context.getNavigation().showContactInfo();
    }

    @FXML
    private void handleSave(ActionEvent event) {
        context.getNavigation().save();
    }

    // ------------------------------------------------------------------
    // Helpers
    // ------------------------------------------------------------------

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