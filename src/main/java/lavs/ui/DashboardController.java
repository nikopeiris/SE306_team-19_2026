package lavs.ui;

import java.io.IOException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.function.Consumer;
import java.util.function.Function;

import business.lavsexceptions.LavsException;
import business.system.Customer;
import business.system.LoginService;
import business.system.customer.CustomerEmailService;
import business.system.dto.CustomerSummary;
import business.system.dto.LoanListResult;
import business.system.dto.LoanStats;
import business.system.dto.LoanSummary;
import business.validation.ValidationException;
import javafx.animation.PauseTransition;
import javafx.application.Platform;
import javafx.beans.property.ReadOnlyObjectWrapper;
import javafx.beans.property.ReadOnlyStringWrapper;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.collections.transformation.FilteredList;
import javafx.concurrent.Task;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Cursor;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.control.ProgressBar;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableRow;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Pane;
import javafx.scene.layout.Priority;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.shape.Circle;
import javafx.stage.Stage;
import javafx.util.Duration;

public class DashboardController {

    @FXML private Button signOutButton;
    @FXML private TextField customerSearchField;
    @FXML
    private ListView<CustomerSummary> customerListView;
    @FXML private Button addCustomerButton;
    @FXML private Button reloadCustomersButton;

    // Loans card: the search box and type filter narrow the table under them
    @FXML private TextField loanSearchField;
    @FXML private ComboBox<String> loanStatusFilter;
    @FXML private Label loanStatusLabel;
    @FXML private TableView<LoanSummary> loanTable;
    @FXML private TableColumn<LoanSummary, String> loanIdColumn;
    @FXML private TableColumn<LoanSummary, String> loanCustomerColumn;
    @FXML private TableColumn<LoanSummary, Double> loanPrincipalColumn;
    @FXML private TableColumn<LoanSummary, String> loanDateColumn;
    @FXML private TableColumn<LoanSummary, String> loanStatusColumn;
    @FXML
    private Button changeStatusButton;
    @FXML private Pane dashboardRoot;

    // Bottom strip
    @FXML private ListView<CustomerSummary> recentCustomersList;
    @FXML private ListView<LoanSummary> recentLoansList;
    @FXML private Label summaryTotalsLabel;
    @FXML private Pane summaryBubblePane;
    @FXML private Label summaryProgressLabel;
    @FXML private ProgressBar summaryProgressBar;

    // Business layer: the UI never talks to the mainframe adapter directly
    private LoginService loginService;
    private Customer customerService;
    private CustomerEmailService emailService;

    // ---- Customer list layout (the header labels in dashboard.fxml use the same widths) ----
    private static final double ROW_HEIGHT = 40.0;
    private static final double NAME_WIDTH = 140.0;
    private static final double ID_WIDTH = 95.0;

    // ---- Recent lists layout ----
    private static final double RECENT_ROW_HEIGHT = 40.0;
    private static final int MAX_RECENT = 8;

    private static final String ALL_STATUSES = "All statuses";

    /** Load every customer's primary email in the background so email search is complete. */
    private static final boolean PREFETCH_EMAILS = true;

    // ---- Loan Summary bubbles ----
    private static final double BUBBLE_AREA_WIDTH = 432.0;
    private static final double BUBBLE_AREA_HEIGHT = 120.0;
    private static final double BUBBLE_MAX = 116.0;
    private static final double BUBBLE_MIN = 38.0;
    private static final double BUBBLE_OVERLAP = 6.0;

    /** Colours handed out to statuses (the awaiting-verification status is always orange). */
    private static final String[] STATUS_COLORS = {
        "navy", "lavender", "crimson", "navy-soft", "lavender-soft", "crimson-soft"
    };
    /** Each status keeps the same colour for the whole session, in the table and in the bubbles. */
    private static final Map<String, Integer> STATUS_COLOR_INDEX = new LinkedHashMap<>();

    // ---- Recently opened items. Static so they survive the dashboard being rebuilt when the
    // user comes back from a customer screen; cleared on sign out. ----
    private static final ObservableList<CustomerSummary> RECENT_CUSTOMERS = FXCollections.observableArrayList();
    private static final ObservableList<LoanSummary> RECENT_LOANS = FXCollections.observableArrayList();

    // ---- Customer list data ----
    private final ObservableList<CustomerSummary> allCustomers = FXCollections.observableArrayList();
    private final FilteredList<CustomerSummary> visibleCustomers =
            new FilteredList<>(allCustomers, c -> true);
    private final Label placeholder = new Label("Loading customers...");
    private boolean loadingCustomers = false;
    private String customerLoadError = null;

    // ---- Searching the mainframe by name as you type ----
    /** A customer number looks like 01-0001-0030590. */
    private static final String CUSTOMER_NUMBER = "\\d{2}-\\d{4}-\\d{7}";
    /** Wait for a pause in typing before asking the mainframe. */
    private final PauseTransition searchDelay = new PauseTransition(Duration.millis(450));
    /** Bumped on every search so a slow, older search cannot overwrite a newer one. */
    private int searchGeneration = 0;
    /** What to show in the empty list after a mainframe search (null = nothing special). */
    private String searchMessage = null;

    // ---- Loans of customers found by search, and automatic retries ----
    private static final int MAX_LOAN_RETRY_ROUNDS = 4;
    private static final long LOAN_RETRY_DELAY_MS = 2000L;
    /** Numbers of the customers whose loans are in the loan list. */
    private final Set<String> loansLoaded = new HashSet<>();
    /** Customers whose loans the mainframe refused; they are retried automatically. */
    private final Map<String, CustomerSummary> loanRetryPending = new LinkedHashMap<>();
    private boolean loanRetryRunning = false;
    private int loanRetryRound = 0;

    // ---- Loan list data (loaded in the background, filtered as you type) ----
    private List<LoanSummary> allLoans = new ArrayList<>();
    private final ObservableList<LoanSummary> visibleLoans = FXCollections.observableArrayList();
    private final Label loanPlaceholder = new Label("Loading loans...");
    private boolean loadingLoans = false;
    private String loanLoadError = null;
    private int loansDone = 0;
    private int loansTotal = 0;
    private int failedCustomers = 0;
    private Task<LoanListResult> loanTask = null;
    /** Bumped whenever a load starts or is abandoned, so late results from an old load are ignored. */
    private volatile int loanLoadGeneration = 0;

    // primary email per customer number ("" means the customer has no primary email)
    private final Map<String, String> emailCache = new ConcurrentHashMap<>();
    private final Set<String> emailFailed = ConcurrentHashMap.newKeySet();
    private final Set<String> emailRequestedByRow = ConcurrentHashMap.newKeySet();

    // rows on screen get their emails first; everything else loads behind them
    private final ExecutorService rowEmailExecutor = Executors.newFixedThreadPool(2, daemon("row-email"));
    private final ExecutorService prefetchEmailExecutor = Executors.newFixedThreadPool(2, daemon("prefetch-email"));

    // many emails arrive in a burst, so refresh the list once things settle
    private final PauseTransition refreshDelay = new PauseTransition(Duration.millis(150));

    @FXML
    private void initialize() {
        placeholder.getStyleClass().add("empty-note");

        customerListView.setItems(visibleCustomers);
        customerListView.setPlaceholder(placeholder);
        customerListView.setFixedCellSize(ROW_HEIGHT);
        customerListView.setCellFactory(lv -> new CustomerCell());

        // typing filters the list straight away (name, customer id or primary email)
        customerSearchField.textProperty().addListener((obs, oldText, newText) -> {
            searchMessage = null;
            applyFilter();
            searchDelay.playFromStart();   // then also ask the mainframe (see searchMainframe)
        });
        searchDelay.setOnFinished(e -> searchMainframe(false));
        customerSearchField.setOnAction(e -> {   // Enter searches straight away
            searchDelay.stop();
            searchMainframe(true);
        });

        refreshDelay.setOnFinished(e -> {
            applyFilter();
            customerListView.refresh();
        });

        setupLoanTable();
        setupRecentLists();
        updateSummary();
    }

    private void setupLoanTable() {
        loanPlaceholder.getStyleClass().add("empty-note");
        loanPlaceholder.setWrapText(true);
        loanPlaceholder.setMaxWidth(300);
        loanTable.setItems(visibleLoans);
        loanTable.setPlaceholder(loanPlaceholder);

        // double-clicking a loan opens its summary (a single click only selects it)
        loanTable.setRowFactory(tv -> {
            TableRow<LoanSummary> row = new TableRow<>();
            row.setOnMouseClicked(e -> {
                if (e.getClickCount() == 2 && !row.isEmpty()) {
                    openLoanSummary(row.getItem());
                }
            });
            return row;
        });

        loanIdColumn.setCellValueFactory(c -> new ReadOnlyStringWrapper(c.getValue().getLoanNumber()));
        loanCustomerColumn.setCellValueFactory(c -> new ReadOnlyStringWrapper(c.getValue().getCustomerName()));
        loanStatusColumn.setCellValueFactory(c -> new ReadOnlyStringWrapper(c.getValue().getStatus()));

        // status shown as a coloured dot followed by the status text
        loanStatusColumn.setCellFactory(col -> new TableCell<>() {
            private final Circle dot = new Circle(4);
            private final Label text = new Label();
            private final HBox box = new HBox(8, dot, text);
            {
                box.setAlignment(Pos.CENTER_LEFT);
                text.getStyleClass().add("cell-body");
            }

            @Override
            protected void updateItem(String status, boolean empty) {
                super.updateItem(status, empty);
                if (empty || status == null || status.isBlank()) {
                    setText(null);
                    setGraphic(null);
                    return;
                }
                dot.getStyleClass().setAll("color-" + statusColor(status));
                text.setText(status);
                setText(null);
                setGraphic(box);
            }
        });

        // principal can be missing (the mainframe sent nothing usable), so show a dash
        loanPrincipalColumn.setCellValueFactory(c -> new ReadOnlyObjectWrapper<>(c.getValue().getPrincipal()));
        loanPrincipalColumn.setCellFactory(col -> new TableCell<>() {
            @Override
            protected void updateItem(Double value, boolean empty) {
                super.updateItem(value, empty);
                setText(empty ? null : value == null ? "-" : String.format("$%,.2f", value));
            }
        });
        // dates are shown as the mainframe sends them, so they sort correctly if they are yyyy-MM-dd
        loanDateColumn.setCellValueFactory(c -> {
            String date = c.getValue().getStartDate();
            return new ReadOnlyStringWrapper(date == null || date.isBlank() ? "-" : date);
        });

        // typing narrows the loans straight away; an empty box shows every loan
        loanSearchField.textProperty().addListener((obs, oldText, newText) -> showLoans());

        // status filter: "All statuses" plus the statuses found in the loaded loans
        loanStatusFilter.getItems().setAll(ALL_STATUSES);
        loanStatusFilter.setValue(ALL_STATUSES);
        loanStatusFilter.valueProperty().addListener((obs, oldValue, newValue) -> showLoans());

        // a loan has to be picked before its status can be changed
        changeStatusButton.disableProperty().bind(
                loanTable.getSelectionModel().selectedItemProperty().isNull());
        updateLoanStatusLabel();
    }

    private void setupRecentLists() {
        recentCustomersList.setItems(RECENT_CUSTOMERS);
        recentCustomersList.setFixedCellSize(RECENT_ROW_HEIGHT);
        recentCustomersList.setPlaceholder(emptyNote("No recent customers yet"));
        recentCustomersList.setCellFactory(lv -> new RecentCell<CustomerSummary>(
                CustomerSummary::getFullName,
                CustomerSummary::getNumber,
                this::handleCustomerSelected));

        recentLoansList.setItems(RECENT_LOANS);
        recentLoansList.setFixedCellSize(RECENT_ROW_HEIGHT);
        recentLoansList.setPlaceholder(emptyNote("No recent loans yet"));
        recentLoansList.setCellFactory(lv -> new RecentCell<LoanSummary>(
                LoanSummary::getLoanNumber,
                loan -> loan.getCustomerName() + "  |  " + loan.getStatus(),
                this::openLoanSummary));
    }

    private Label emptyNote(String text) {
        Label note = new Label(text);
        note.getStyleClass().add("empty-note");
        return note;
    }

    @FXML
    private void handleChangeStatus(ActionEvent event) {
        LoanSummary selected = loanTable.getSelectionModel().getSelectedItem();
        if (selected == null) {
            return;
        }
        LoanStatusDialog.show(loanTable.getScene().getWindow(), customerService, selected,
                newStatus -> showNewStatus(selected, newStatus));
    }

    private void openLoanSummary(LoanSummary loan) {
        remember(RECENT_LOANS, loan, LoanSummary::getLoanNumber);
        showLoanSummaryOverlay(loan);
    }
    
    private void showLoanSummaryOverlay(LoanSummary loan) {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("loan_summary.fxml"));
            Parent loanOverlay = loader.load();

            LoanSummaryController controller = loader.getController();
            
            // Initialize the controller with the loan details service, loan number, 
            // and an optional callback to refresh the table/summary if changes are saved
            controller.start(customerService.loanDetails(), loan.getLoanNumber(), () -> {
                showLoans();
                updateSummary();
            });

            // Set the close action to remove the overlay from the dashboard root when closed/back is clicked
            controller.setOnCloseAction(() -> dashboardRoot.getChildren().remove(loanOverlay));

            // Push the loan summary overlay on top of the dashboard root pane
            dashboardRoot.getChildren().add(loanOverlay);

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /** Shows the changed status without reloading every customer's loans. */
    private void showNewStatus(LoanSummary loan, String newStatus) {
        LoanSummary updated = loan.withStatus(newStatus);
        int index = allLoans.indexOf(loan);
        if (index >= 0) {
            allLoans.set(index, updated);
        }
        remember(RECENT_LOANS, updated, LoanSummary::getLoanNumber);
        populateLoanStatuses();
        showLoans();
    }

    // add customer button
    @FXML
    private void handleAddCustomer(ActionEvent event) {
        showCustomerOverlay(null, null);
    }

    private void showCustomerOverlay(CustomerSummary customer, String customerNumber) {
    try {
        FXMLLoader loader = new FXMLLoader(getClass().getResource("customer.fxml"));
        Parent customerOverlay = loader.load();

        CustomerController controller = loader.getController();
        controller.setServices(loginService, customerService, emailService);
        
        if (customerNumber != null) {
            controller.loadCustomer(customerNumber);
            remember(RECENT_CUSTOMERS, customer, CustomerSummary::getNumber);
        }

        // Add a close mechanism so the overlay can remove itself when Back/Save is clicked
        controller.setOnCloseAction(() -> {
            dashboardRoot.getChildren().remove(customerOverlay);
            // a loan may have been added on the customer's loan tab
            if (customer != null) {
                fetchLoansFor(List.of(currentSummary(customer)), true);
            }
        });
        // After a save, show the new / renamed customer in the list straight away
        controller.setOnSavedAction(this::showSavedCustomer);

        // Overlap it on top of the dashboard root
        dashboardRoot.getChildren().add(customerOverlay);

    } catch (Exception e) {
        e.printStackTrace();
    }
}

    /** Called by LoginController when it opens the dashboard. */
    public void setServices(LoginService loginService, Customer customerService,
            CustomerEmailService emailService) {
        this.loginService = loginService;
        this.customerService = customerService;
        this.emailService = emailService;
        loadCustomers();
    }

    // ------------------------------------------------------------------
    // Recently opened customers / loans (pure screen history, no business rules)
    // ------------------------------------------------------------------

    /** Moves the item to the top of the list, drops duplicates and keeps the list short. */
    private static <T> void remember(ObservableList<T> list, T item, Function<T, String> key) {
        String id = key.apply(item);
        list.removeIf(existing -> key.apply(existing).equals(id));
        list.add(0, item);
        while (list.size() > MAX_RECENT) {
            list.remove(list.size() - 1);
        }
    }

    // ------------------------------------------------------------------
    // Loading and filtering customers
    // ------------------------------------------------------------------

    private void loadCustomers() {
        loadingCustomers = true;
        customerLoadError = null;
        updatePlaceholder();

        Task<List<CustomerSummary>> loadTask = new Task<>() {
            @Override
            protected List<CustomerSummary> call() throws Exception {
                return customerService.listAllCustomers();   // already sorted by first name
            }
        };

        loadTask.setOnSucceeded(e -> {
            loadingCustomers = false;
            allCustomers.setAll(loadTask.getValue());
            updatePlaceholder();
            if (PREFETCH_EMAILS) {
                prefetchEmails();
            }
            loadLoans();
        });

        loadTask.setOnFailed(e -> {
            loadingCustomers = false;
            customerLoadError = describe(loadTask.getException());
            updatePlaceholder();
        });

        Thread thread = new Thread(loadTask, "load-customers");
        thread.setDaemon(true);
        thread.start();
    }

    private void applyFilter() {
        String query = customerSearchField.getText() == null
                ? "" : customerSearchField.getText().trim().toLowerCase();
        visibleCustomers.setPredicate(c -> query.isEmpty() || matches(c, query));
        updatePlaceholder();
    }

    private boolean matches(CustomerSummary customer, String query) {
        if (customer.getFullName().toLowerCase().contains(query)) {
            return true;
        }
        if (customer.getNumber().toLowerCase().contains(query)) {
            return true;
        }
        String email = emailCache.get(customer.getNumber());
        return email != null && email.toLowerCase().contains(query);
    }

    private void updatePlaceholder() {
        if (loadingCustomers) {
            placeholder.setText("Loading customers...");
        } else if (customerLoadError != null) {
            placeholder.setText(customerLoadError);
        } else if (allCustomers.isEmpty()) {
            placeholder.setText("There are no customers yet.");
        } else if (searchMessage != null) {
            placeholder.setText(searchMessage);
        } else {
            placeholder.setText("No customers match your search.");
        }
    }

    // ------------------------------------------------------------------
    // Finding customers on the mainframe
    // ------------------------------------------------------------------
    // The mainframe cannot list every customer: its search only matches WHOLE first or
    // family names (and an empty search returns nothing). The list above therefore only
    // holds customers whose name is a single letter or digit; everyone else is found by
    // typing their full first or family name (or by opening their customer number).

    /**
     * Asks the mainframe for customers matching the typed text and adds them to the list.
     *
     * @param enterPressed true when the user pressed Enter (a customer number then opens the
     *                     customer, and very short text is searched too)
     */
    private void searchMainframe(boolean enterPressed) {
        if (customerService == null) {
            return;
        }
        final String term = customerSearchField.getText() == null
                ? "" : customerSearchField.getText().trim();
        if (term.isEmpty()) {
            return;
        }
        if (term.matches(CUSTOMER_NUMBER)) {
            lookUpByNumber(term, enterPressed);
            return;
        }
        // single characters are already covered by the list itself
        if (term.length() < 2 && !enterPressed) {
            return;
        }

        final int generation = ++searchGeneration;
        searchMessage = "Searching...";
        updatePlaceholder();

        Task<List<CustomerSummary>> task = new Task<>() {
            @Override
            protected List<CustomerSummary> call() throws Exception {
                return customerService.searchCustomers(term);
            }
        };
        task.setOnSucceeded(e -> {
            if (generation != searchGeneration) {
                return;   // the user has typed something newer since
            }
            List<CustomerSummary> found = task.getValue();
            mergeIntoList(found);
            fetchLoansFor(found, false);
            searchMessage = found.isEmpty()
                    ? "No customer found for \"" + term + "\". The search only matches whole "
                            + "first or family names (e.g. \"jeff\", not \"jef\")."
                    : null;
            applyFilter();
        });
        task.setOnFailed(e -> {
            if (generation != searchGeneration) {
                return;
            }
            searchMessage = describe(task.getException());
            applyFilter();
        });
        startDaemon(task, "search-customers");
    }

    /** Looks a customer up by number; opens them when asked, otherwise just lists them. */
    private void lookUpByNumber(String number, boolean open) {
        final int generation = ++searchGeneration;
        Task<CustomerSummary> task = new Task<>() {
            @Override
            protected CustomerSummary call() throws Exception {
                return customerService.findCustomerSummary(number);
            }
        };
        task.setOnSucceeded(e -> {
            CustomerSummary found = task.getValue();
            mergeIntoList(List.of(found));
            fetchLoansFor(List.of(found), false);
            if (open) {
                showCustomerOverlay(found, number);
            } else if (generation == searchGeneration) {
                searchMessage = null;
                applyFilter();
            }
        });
        task.setOnFailed(e -> {
            if (generation != searchGeneration) {
                return;
            }
            searchMessage = "No customer found with number " + number + ".";
            applyFilter();
        });
        startDaemon(task, "lookup-customer");
    }

    /** Adds new customers to the list (or refreshes ones already there) and keeps it sorted. */
    private void mergeIntoList(List<CustomerSummary> customers) {
        for (CustomerSummary c : customers) {
            boolean replaced = false;
            for (int i = 0; i < allCustomers.size(); i++) {
                if (allCustomers.get(i).getNumber().equals(c.getNumber())) {
                    allCustomers.set(i, c);
                    replaced = true;
                    break;
                }
            }
            if (!replaced) {
                allCustomers.add(c);
            }
        }
        FXCollections.sort(allCustomers, Comparator
                .comparing((CustomerSummary c) -> nameKey(c.getFirstName()))
                .thenComparing(c -> nameKey(c.getFamilyName())));
    }

    private static String nameKey(String name) {
        return name == null ? "" : name.toLowerCase();
    }

    /** After a save: fetch the customer again so the list shows the new or changed name. */
    private void showSavedCustomer(String number) {
        if (number == null || number.isBlank() || customerService == null) {
            return;
        }
        Task<CustomerSummary> task = new Task<>() {
            @Override
            protected CustomerSummary call() throws Exception {
                return customerService.findCustomerSummary(number);
            }
        };
        task.setOnSucceeded(e -> {
            CustomerSummary saved = task.getValue();
            mergeIntoList(List.of(saved));
            fetchLoansFor(List.of(saved), true);   // loans may have changed too
            remember(RECENT_CUSTOMERS, saved, CustomerSummary::getNumber);
            applyFilter();
        });
        task.setOnFailed(e -> task.getException().printStackTrace());
        startDaemon(task, "refresh-saved-customer");
    }

    private static void startDaemon(Task<?> task, String name) {
        Thread thread = new Thread(task, name);
        thread.setDaemon(true);
        thread.start();
    }

    private String describe(Throwable error) {
        if (error instanceof ValidationException || error instanceof LavsException) {
            return error.getMessage();
        } else if (error instanceof IOException) {
            return "Could not connect to the server.";
        }
        error.printStackTrace();
        return "Something went wrong loading data.";
    }

    // ------------------------------------------------------------------
    // Loans (all matching rules live in the business layer)
    // ------------------------------------------------------------------

    private void loadLoans() {
        stopLoanLoading();
        final int generation = loanLoadGeneration;
        List<CustomerSummary> customers = new ArrayList<>(allCustomers);   // snapshot on the FX thread
        final List<CustomerSummary> failedOut = new CopyOnWriteArrayList<>();

        loadingLoans = true;
        loanLoadError = null;
        loansDone = 0;
        loansTotal = customers.size();
        failedCustomers = 0;
        allLoans = new ArrayList<>();
        loansLoaded.clear();
        loanRetryPending.clear();
        loanRetryRunning = false;
        visibleLoans.clear();
        updateLoanPlaceholder();
        updateLoanStatusLabel();
        updateSummary();

        Task<LoanListResult> task = new Task<>() {
            @Override
            protected LoanListResult call() throws Exception {
                // each customer's loans are handed to the screen as soon as they are read
                return customerService.listLoansFor(customers, (newLoans, done, total) -> {
                    Platform.runLater(() -> {
                        if (generation != loanLoadGeneration) {
                            return;
                        }
                        allLoans.addAll(newLoans);
                        loansDone = done;
                        showLoans();
                    });
                    return generation == loanLoadGeneration;   // false = stop early
                }, failedOut);
            }
        };

        task.setOnSucceeded(e -> {
            if (generation != loanLoadGeneration) {
                return;
            }
            LoanListResult result = task.getValue();
            loadingLoans = false;
            failedCustomers = result.getFailedCustomers();
            if (failedCustomers > 0) {
                System.err.println("Loan load: " + failedCustomers + " of " + customers.size()
                        + " customers could not be read. First error: " + result.getFirstError());
                if (result.getLoans().isEmpty()) {
                    loanLoadError = result.getFirstError();
                }
            }
            for (CustomerSummary c : customers) {
                loansLoaded.add(c.getNumber());
            }
            for (CustomerSummary c : failedOut) {
                loansLoaded.remove(c.getNumber());
                loanRetryPending.put(c.getNumber(), c);
            }
            failedCustomers = loanRetryPending.size();
            populateLoanStatuses();
            showLoans();
            startLoanRetry();   // try the customers the mainframe refused again
        });

        task.setOnFailed(e -> {
            if (generation != loanLoadGeneration) {
                return;
            }
            loadingLoans = false;
            loanLoadError = describe(task.getException());
            populateLoanStatuses();
            showLoans();
        });

        loanTask = task;
        Thread thread = new Thread(task, "load-loans");
        thread.setDaemon(true);
        thread.start();
    }

    /** Abandons any loan load still running (results from it are ignored from now on). */
    private void stopLoanLoading() {
        loanLoadGeneration++;
        if (loanTask != null) {
            loanTask.cancel(true);
            loanTask = null;
        }
        loadingLoans = false;
        loanRetryRunning = false;
    }

    // ------------------------------------------------------------------
    // Loans of individual customers (found by search, saved, or retried)
    // ------------------------------------------------------------------

    /** What one background round of reading customers' loans produced. */
    private static class LoanFetch {
        final List<LoanSummary> loans = new ArrayList<>();
        final List<CustomerSummary> loaded = new ArrayList<>();
        final List<CustomerSummary> failed = new ArrayList<>();
    }

    /**
     * Reads the loans of these customers and adds them to the loan list and the summary.
     *
     * @param force false skips customers whose loans are already loaded; true reads them
     *              again (used after a save, because loans may have changed)
     */
    private void fetchLoansFor(List<CustomerSummary> customers, boolean force) {
        if (customerService == null) {
            return;
        }
        List<CustomerSummary> todo = new ArrayList<>();
        for (CustomerSummary c : customers) {
            if (force || !loansLoaded.contains(c.getNumber())) {
                todo.add(c);
            }
        }
        if (todo.isEmpty()) {
            return;
        }
        runLoanFetch(todo, 0L, () -> { });
    }

    private void runLoanFetch(List<CustomerSummary> todo, long delayMs, Runnable afterApplied) {
        final int generation = loanLoadGeneration;
        Task<LoanFetch> task = new Task<>() {
            @Override
            protected LoanFetch call() throws Exception {
                LoanFetch fetch = new LoanFetch();
                if (delayMs > 0) {
                    Thread.sleep(delayMs);
                }
                for (CustomerSummary c : todo) {
                    if (generation != loanLoadGeneration) {
                        break;   // a full reload started; this result will be ignored
                    }
                    try {
                        fetch.loans.addAll(customerService.loansOf(c));
                        fetch.loaded.add(c);
                    } catch (Exception ex) {
                        fetch.failed.add(c);
                    }
                }
                return fetch;
            }
        };
        task.setOnSucceeded(e -> {
            if (generation != loanLoadGeneration) {
                return;
            }
            applyLoanFetch(task.getValue());
            afterApplied.run();
        });
        task.setOnFailed(e -> {
            if (generation == loanLoadGeneration) {
                loanRetryRunning = false;
                updateLoanStatusLabel();
            }
        });
        startDaemon(task, "fetch-loans");
    }

    private void applyLoanFetch(LoanFetch fetch) {
        mergeLoans(fetch.loans);
        for (CustomerSummary c : fetch.loaded) {
            loansLoaded.add(c.getNumber());
            loanRetryPending.remove(c.getNumber());
        }
        for (CustomerSummary c : fetch.failed) {
            loanRetryPending.put(c.getNumber(), c);
        }
        failedCustomers = loanRetryPending.size();
        populateLoanStatuses();
        showLoans();   // also refreshes the Loan Summary card
        if (!fetch.failed.isEmpty()) {
            startLoanRetry();
        }
    }

    /** Adds loans to the list, replacing any with the same loan number. */
    private void mergeLoans(List<LoanSummary> loans) {
        Map<String, Integer> indexByNumber = new HashMap<>();
        for (int i = 0; i < allLoans.size(); i++) {
            indexByNumber.put(allLoans.get(i).getLoanNumber(), i);
        }
        for (LoanSummary loan : loans) {
            Integer at = indexByNumber.get(loan.getLoanNumber());
            if (at != null) {
                allLoans.set(at, loan);
            } else {
                allLoans.add(loan);
                indexByNumber.put(loan.getLoanNumber(), allLoans.size() - 1);
            }
        }
    }

    /** Starts retrying the customers the mainframe refused (does nothing if already retrying). */
    private void startLoanRetry() {
        if (loanRetryRunning || loanRetryPending.isEmpty()) {
            return;
        }
        loanRetryRunning = true;
        loanRetryRound = 0;
        nextLoanRetryRound();
        updateLoanStatusLabel();
    }

    /** Waits a little longer each round, then reads the still-missing customers again. */
    private void nextLoanRetryRound() {
        if (loanRetryPending.isEmpty() || loanRetryRound >= MAX_LOAN_RETRY_ROUNDS) {
            loanRetryRunning = false;
            updateLoanStatusLabel();
            return;
        }
        loanRetryRound++;
        List<CustomerSummary> todo = new ArrayList<>(loanRetryPending.values());
        runLoanFetch(todo, LOAN_RETRY_DELAY_MS * loanRetryRound, this::nextLoanRetryRound);
    }

    /** The list's current entry for this customer (it may have been renamed since). */
    private CustomerSummary currentSummary(CustomerSummary fallback) {
        for (CustomerSummary c : allCustomers) {
            if (c.getNumber().equals(fallback.getNumber())) {
                return c;
            }
        }
        return fallback;
    }

    private void showLoans() {
        visibleLoans.setAll(customerService.filterLoans(
                allLoans, loanSearchField.getText(), selectedLoanStatus()));
        updateLoanPlaceholder();
        updateLoanStatusLabel();
        updateSummary();
    }

    /** The status picked in the filter, or null when "All statuses" is selected. */
    private String selectedLoanStatus() {
        String value = loanStatusFilter.getValue();
        return value == null || ALL_STATUSES.equals(value) ? null : value;
    }

    /** Refreshes the status filter from the loans that are loaded, keeping the current pick if it still exists. */
    private void populateLoanStatuses() {
        String current = selectedLoanStatus();
        List<String> statuses = customerService.loanStatusesIn(allLoans);
        loanStatusFilter.getItems().setAll(ALL_STATUSES);
        loanStatusFilter.getItems().addAll(statuses);
        loanStatusFilter.setValue(current != null && statuses.contains(current) ? current : ALL_STATUSES);
    }

    private void updateLoanPlaceholder() {
        if (loadingLoans) {
            loanPlaceholder.setText("Loading loans...");
        } else if (loanLoadError != null) {
            loanPlaceholder.setText(loanLoadError);
        } else if (allLoans.isEmpty()) {
            loanPlaceholder.setText("There are no loans yet.");
        } else {
            loanPlaceholder.setText("No loans match your search.");
        }
    }

    private void updateLoanStatusLabel() {
        String text;
        if (loadingLoans) {
            text = "Loading loans... " + loansDone + "/" + loansTotal + " customers";
        } else if (loanLoadError != null) {
            text = allLoans.isEmpty() ? "Could not load loans" : "Loading stopped early";
        } else {
            int all = allLoans.size();
            int shown = visibleLoans.size();
            String noun = all == 1 ? " loan" : " loans";
            text = shown == all ? all + noun : shown + " of " + all + noun;
            // the mainframe only lists loans per customer, so say whose loans these are
            text += " from " + loansLoaded.size() + (loansLoaded.size() == 1 ? " customer" : " customers");
            if (failedCustomers > 0) {
                text += " (" + failedCustomers + " skipped" + (loanRetryRunning ? ", retrying..." : "") + ")";
            }
        }
        loanStatusLabel.setText(text);
    }

    /** Fills the Loan Summary card; the figures come from the business layer. */
    private void updateSummary() {
        if (customerService == null) {   // dashboard is built before the services are handed over
            summaryTotalsLabel.setText("-");
            summaryBubblePane.getChildren().clear();
            summaryProgressBar.setProgress(0);
            summaryProgressLabel.setText("-");
            return;
        }
        LoanStats stats = customerService.summariseLoans(allLoans);
        int total = stats.getTotal();
        summaryTotalsLabel.setText(total == 0 ? "-"
                : total + (total == 1 ? " loan" : " loans")
                        + "  |  $" + compactAmount(stats.getTotalPrincipal()) + " total");

        renderBubbles(stats);

        double progress = stats.getVerificationProgress();
        summaryProgressBar.setProgress(progress);
        summaryProgressLabel.setText(total == 0 ? "-" : Math.round(progress * 100) + "% complete");
    }

    /** Draws one bubble per status, sized by its share of all loans (area follows the share). */
    private void renderBubbles(LoanStats stats) {
        summaryBubblePane.getChildren().clear();
        List<LoanStats.StatusShare> shares = stats.getShares();
        if (shares.isEmpty()) {
            Label none = new Label("No loans to show yet");
            none.getStyleClass().add("empty-note");
            none.setPrefWidth(BUBBLE_AREA_WIDTH);
            none.setAlignment(Pos.CENTER);
            none.setLayoutY(50);
            summaryBubblePane.getChildren().add(none);
            return;
        }

        int n = shares.size();
        double biggest = shares.get(0).getPercent();
        double[] size = new double[n];
        double sum = 0;
        for (int i = 0; i < n; i++) {
            size[i] = Math.max(BUBBLE_MIN, BUBBLE_MAX * Math.sqrt(shares.get(i).getPercent() / biggest));
            sum += size[i];
        }
        double overlaps = BUBBLE_OVERLAP * (n - 1);
        double total = sum - overlaps;
        if (total > BUBBLE_AREA_WIDTH) {   // lots of statuses: shrink everything to fit
            double scale = (BUBBLE_AREA_WIDTH + overlaps) / sum;
            for (int i = 0; i < n; i++) {
                size[i] *= scale;
            }
            total = BUBBLE_AREA_WIDTH;
        }

        // biggest bubble in the middle, the rest alternate bottom / top so they cluster together
        double x = (BUBBLE_AREA_WIDTH - total) / 2;
        for (int i = 0; i < n; i++) {
            double d = size[i];
            double y = i == 0 ? (BUBBLE_AREA_HEIGHT - d) / 2
                    : i % 2 == 1 ? BUBBLE_AREA_HEIGHT - d : 0;
            summaryBubblePane.getChildren().add(bubble(shares.get(i), d, x, y));
            x += d - BUBBLE_OVERLAP;
        }
    }

    private StackPane bubble(LoanStats.StatusShare share, double diameter, double x, double y) {
        String color = statusColor(share.getStatus());
        String textClass = "orange".equals(color) ? "bubble-dark-text" : "bubble-light-text";

        Circle circle = new Circle(diameter / 2);
        circle.getStyleClass().addAll("bubble", "color-" + color);

        Label percent = new Label(formatPercent(share.getPercent()));
        percent.getStyleClass().addAll("bubble-percent", textClass);
        percent.setStyle(String.format("-fx-font-size: %.0fpx;",
                Math.max(11, Math.min(26, diameter * 0.26))));

        VBox text = new VBox(0, percent);
        text.setAlignment(Pos.CENTER);
        text.setMouseTransparent(true);
        if (diameter >= 72) {   // small bubbles only have room for the percentage
            Label name = new Label(share.getStatus());
            name.getStyleClass().addAll("bubble-name", textClass);
            name.setMaxWidth(diameter - 16);
            text.getChildren().add(name);
        }

        StackPane bubble = new StackPane(circle, text);
        bubble.setLayoutX(x);
        bubble.setLayoutY(y);
        int count = share.getCount();
        Tooltip.install(bubble, new Tooltip(share.getStatus() + ": " + count
                + (count == 1 ? " loan" : " loans") + " (" + formatPercent(share.getPercent()) + ")"));
        return bubble;
    }

    private static String formatPercent(double percent) {
        return percent > 0 && percent < 1 ? "<1%" : Math.round(percent) + "%";
    }

    /** Colour name for a status; the same status always gets the same colour. */
    private String statusColor(String status) {
        if (customerService != null && customerService.isAwaitingVerification(status)) {
            return "orange";
        }
        String key = status.trim().toLowerCase();
        Integer index = STATUS_COLOR_INDEX.get(key);
        if (index == null) {
            index = STATUS_COLOR_INDEX.size();
            STATUS_COLOR_INDEX.put(key, index);
        }
        return STATUS_COLORS[index % STATUS_COLORS.length];
    }

    /** 1234567 becomes "1.2M" so big totals still fit in the tile. */
    private static String compactAmount(double amount) {
        double abs = Math.abs(amount);
        if (abs >= 1_000_000_000) {
            return String.format("%.1fB", amount / 1_000_000_000);
        } else if (abs >= 1_000_000) {
            return String.format("%.1fM", amount / 1_000_000);
        } else if (abs >= 10_000) {
            return String.format("%.0fK", amount / 1_000);
        }
        return String.format("%,.0f", amount);
    }

    // ------------------------------------------------------------------
    // Primary emails (one request per customer, loaded in the background)
    // ------------------------------------------------------------------

    private void prefetchEmails() {
        for (CustomerSummary customer : allCustomers) {
            String number = customer.getNumber();
            prefetchEmailExecutor.submit(() -> fetchEmail(number));
        }
    }

    // called when a row appears on screen and its email isn't known yet
    private void requestEmailForRow(String number) {
        if (!rowEmailExecutor.isShutdown() && emailRequestedByRow.add(number)) {
            rowEmailExecutor.submit(() -> fetchEmail(number));
        }
    }

    private void fetchEmail(String number) {
        if (emailCache.containsKey(number) || emailFailed.contains(number)) {
            return;
        }
        try {
            String email = emailService.findPrimaryEmail(number);
            emailCache.put(number, email == null ? "" : email);
        } catch (Exception ex) {
            emailFailed.add(number);
        }
        if (!rowEmailExecutor.isShutdown()) {
            Platform.runLater(refreshDelay::playFromStart);
        }
    }

    private String emailTextFor(CustomerSummary customer) {
        String number = customer.getNumber();
        String cached = emailCache.get(number);
        if (cached != null) {
            return cached.isEmpty() ? "-" : cached;
        }
        if (emailFailed.contains(number)) {
            return "Unavailable";
        }
        requestEmailForRow(number);
        return "Loading...";
    }

    // ------------------------------------------------------------------
    // One row in the customer list
    // ------------------------------------------------------------------

    private class CustomerCell extends ListCell<CustomerSummary> {
        private final HBox row = new HBox(10);
        private final Label nameLabel = new Label();
        private final Label idLabel = new Label();
        private final Label emailLabel = new Label();

        CustomerCell() {
            nameLabel.setMinWidth(NAME_WIDTH);
            nameLabel.setPrefWidth(NAME_WIDTH);
            nameLabel.setMaxWidth(NAME_WIDTH);
            idLabel.setMinWidth(ID_WIDTH);
            idLabel.setPrefWidth(ID_WIDTH);
            idLabel.setMaxWidth(ID_WIDTH);
            emailLabel.setMaxWidth(Double.MAX_VALUE);
            HBox.setHgrow(emailLabel, Priority.ALWAYS);

            nameLabel.getStyleClass().add("cell-primary");
            idLabel.getStyleClass().add("cell-body");
            emailLabel.getStyleClass().add("cell-secondary");

            row.setAlignment(Pos.CENTER_LEFT);
            row.setPadding(new Insets(0, 10, 0, 10));
            row.setPrefHeight(ROW_HEIGHT);
            row.getChildren().addAll(nameLabel, idLabel, emailLabel);

            setOnMouseClicked(e -> {
                CustomerSummary customer = getItem();
                if (!isEmpty() && customer != null) {
                    getListView().getSelectionModel().clearSelection();
                    handleCustomerSelected(customer);
                }
            });
        }

        @Override
        protected void updateItem(CustomerSummary customer, boolean empty) {
            super.updateItem(customer, empty);
            if (empty || customer == null) {
                setText(null);
                setGraphic(null);
                setCursor(Cursor.DEFAULT);
                return;
            }
            nameLabel.setText(customer.getFullName());
            idLabel.setText(customer.getNumber());
            emailLabel.setText(emailTextFor(customer));
            setText(null);
            setGraphic(row);
            setCursor(Cursor.HAND);
        }
    }

    // ------------------------------------------------------------------
    // One row in the Recent Customers / Recent Loans lists (title + detail)
    // ------------------------------------------------------------------

    private static class RecentCell<T> extends ListCell<T> {
        private final VBox texts = new VBox(2);
        private final Label primary = new Label();
        private final Label secondary = new Label();
        private final Function<T, String> primaryText;
        private final Function<T, String> secondaryText;

        RecentCell(Function<T, String> primaryText, Function<T, String> secondaryText,
                Consumer<T> onClick) {
            this.primaryText = primaryText;
            this.secondaryText = secondaryText;

            primary.getStyleClass().add("cell-primary");
            secondary.getStyleClass().add("cell-secondary");
            texts.setAlignment(Pos.CENTER_LEFT);
            texts.setPadding(new Insets(0, 10, 0, 10));
            texts.getChildren().addAll(primary, secondary);

            setOnMouseClicked(e -> {
                T item = getItem();
                if (!isEmpty() && item != null) {
                    getListView().getSelectionModel().clearSelection();
                    onClick.accept(item);
                }
            });
        }

        @Override
        protected void updateItem(T item, boolean empty) {
            super.updateItem(item, empty);
            if (empty || item == null) {
                setText(null);
                setGraphic(null);
                setCursor(Cursor.DEFAULT);
                return;
            }
            primary.setText(primaryText.apply(item));
            secondary.setText(secondaryText.apply(item));
            setText(null);
            setGraphic(texts);
            setCursor(Cursor.HAND);
        }
    }

    // ------------------------------------------------------------------
    // Clicking a customer
    // ------------------------------------------------------------------

    private void handleCustomerSelected(CustomerSummary customer) {
        showCustomerOverlay(customer, customer.getNumber());
    }

    // ------------------------------------------------------------------
    // Sign out
    // ------------------------------------------------------------------

    @FXML
    private void handleSignOut(ActionEvent event) {
        signOutButton.setDisable(true);
        stopBackgroundWork();

        // the next person to log in should not see this user's recent items
        RECENT_CUSTOMERS.clear();
        RECENT_LOANS.clear();

        // Run on a background thread in case logging out makes a network call
        Task<Void> logoutTask = new Task<>() {
            @Override
            protected Void call() {
                loginService.logout();
                return null;
            }
        };

        logoutTask.setOnSucceeded(e -> goToLogin());
        logoutTask.setOnFailed(e -> {
            // Even if the server couldn't be told, the user still wants to leave
            logoutTask.getException().printStackTrace();
            goToLogin();
        });

        Thread thread = new Thread(logoutTask, "logout-task");
        thread.setDaemon(true);
        thread.start();
    }

    private void goToLogin() {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/lavs/ui/login.fxml"));
            Parent root = loader.load();

            // give the fresh login screen the services so the user can log in again
            LoginController loginController = loader.getController();
            loginController.setLoginService(loginService);
            loginController.setCustomerService(customerService);
            loginController.setEmailService(emailService);

            Scene scene = new Scene(root, 1200, 800);
            URL cssUrl = getClass().getResource("/lavs/ui/styles.css");
            if (cssUrl != null) {
                scene.getStylesheets().add(cssUrl.toExternalForm());
            }

            Stage stage = (Stage) signOutButton.getScene().getWindow();
            stage.setScene(scene);
            stage.setTitle("Mainframe Interface - Login");
            stage.show();
        } catch (Exception e) {
            e.printStackTrace();
            signOutButton.setDisable(false);
        }
    }

    // stops any email lookups still running when the user leaves the dashboard
    private void stopBackgroundWork() {
        stopLoanLoading();
        rowEmailExecutor.shutdownNow();
        prefetchEmailExecutor.shutdownNow();
        refreshDelay.stop();
    }

    private static ThreadFactory daemon(String name) {
        return runnable -> {
            Thread thread = new Thread(runnable, name);
            thread.setDaemon(true);
            return thread;
        };
    }

    @FXML
    private void handleReloadCustomers(ActionEvent event) {
        if (loadingCustomers) return; // Prevent overlapping reload calls

        // Clear existing caches to force a fresh fetch
        emailCache.clear();
        emailFailed.clear();
        emailRequestedByRow.clear();
        allCustomers.clear();

        loadCustomers();
    }

}