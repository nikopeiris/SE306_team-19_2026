package lavs.ui;

import java.io.IOException;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;

import business.lavsexceptions.LavsException;
import business.system.Customer;
import business.system.LoginService;
import business.system.customer.CustomerEmailService;
import business.system.dto.CustomerSummary;
import business.system.dto.LoanListResult;
import business.system.dto.LoanSummary;
import business.validation.ValidationException;
import javafx.animation.PauseTransition;
import javafx.application.Platform;
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
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.stage.Stage;
import javafx.util.Duration;

public class DashboardController {

    @FXML private Button signOutButton;
    @FXML private TextField customerSearchField;
    @FXML
    private ListView<CustomerSummary> customerListView;
    @FXML private Button addCustomerButton;

    // Bottom-right "Search for Loan" box: the search field filters the loans table under it
    @FXML private TextField loanSearchField;
    @FXML private Label loanStatusLabel;
    @FXML private TableView<LoanSummary> loanTable;
    @FXML private TableColumn<LoanSummary, String> loanIdColumn;
    @FXML private TableColumn<LoanSummary, String> loanCustomerColumn;
    @FXML private TableColumn<LoanSummary, String> loanStatusColumn;

    // Business layer: the UI never talks to the mainframe adapter directly
    private LoginService loginService;
    private Customer customerService;
    private CustomerEmailService emailService;

    // ---- Customer list layout (the header labels in dashboard.fxml use the same widths) ----
    private static final double ROW_HEIGHT = 34.0;
    private static final double NAME_WIDTH = 290.0;
    private static final double ID_WIDTH = 170.0;

    /** Load every customer's primary email in the background so email search is complete. */
    private static final boolean PREFETCH_EMAILS = true;

    private static final String ROW_STYLE =
            "-fx-background-color: white; -fx-padding: 0;"
            + " -fx-border-color: transparent transparent #ececec transparent;";
    private static final String ROW_HOVER_STYLE =
            "-fx-background-color: #fde9c8; -fx-padding: 0;"
            + " -fx-border-color: transparent transparent #ececec transparent;";

    // ---- Customer list data ----
    private final ObservableList<CustomerSummary> allCustomers = FXCollections.observableArrayList();
    private final FilteredList<CustomerSummary> visibleCustomers =
            new FilteredList<>(allCustomers, c -> true);
    private final Label placeholder = new Label("Loading customers...");
    private boolean loadingCustomers = false;
    private String customerLoadError = null;

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
        placeholder.setStyle("-fx-text-fill: #726e97; -fx-font-size: 13;");

        customerListView.setItems(visibleCustomers);
        customerListView.setPlaceholder(placeholder);
        customerListView.setFixedCellSize(ROW_HEIGHT);
        customerListView.setCellFactory(lv -> new CustomerCell());

        // typing filters the list straight away (name, customer id or primary email)
        customerSearchField.textProperty().addListener((obs, oldText, newText) -> applyFilter());

        refreshDelay.setOnFinished(e -> {
            applyFilter();
            customerListView.refresh();
        });

        setupLoanTable();
    }

    private void setupLoanTable() {
        loanPlaceholder.setStyle("-fx-text-fill: #726e97; -fx-font-size: 12;");
        loanPlaceholder.setWrapText(true);
        loanPlaceholder.setMaxWidth(180);
        loanTable.setItems(visibleLoans);
        loanTable.setPlaceholder(loanPlaceholder);

        loanIdColumn.setCellValueFactory(c -> new ReadOnlyStringWrapper(c.getValue().getLoanNumber()));
        loanCustomerColumn.setCellValueFactory(c -> new ReadOnlyStringWrapper(c.getValue().getCustomerName()));
        loanStatusColumn.setCellValueFactory(c -> new ReadOnlyStringWrapper(c.getValue().getStatus()));

        // typing narrows the loans straight away; an empty box shows every loan
        loanSearchField.textProperty().addListener((obs, oldText, newText) -> showLoans());
        updateLoanStatusLabel();
    }

    // add custoemr button
    @FXML
    private void handleAddCustomer(ActionEvent event) {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("customer.fxml"));
            Parent root = loader.load();

            stopLoanLoading();   // don't compete with the next screen for the mainframe
            // Pass the services and initialize an empty context for a new customer
            CustomerController controller = loader.getController();
            controller.setServices(loginService, customerService, emailService);

            Stage stage = (Stage) ((Node) event.getSource()).getScene().getWindow();
            Scene scene = new Scene(root);
            stage.setScene(scene);
            stage.show();
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
        } else {
            placeholder.setText("No customers match your search.");
        }
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

        loadingLoans = true;
        loanLoadError = null;
        loansDone = 0;
        loansTotal = customers.size();
        failedCustomers = 0;
        allLoans = new ArrayList<>();
        visibleLoans.clear();
        updateLoanPlaceholder();
        updateLoanStatusLabel();

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
                });
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
            showLoans();
        });

        task.setOnFailed(e -> {
            if (generation != loanLoadGeneration) {
                return;
            }
            loadingLoans = false;
            loanLoadError = describe(task.getException());
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
    }

    private void showLoans() {
        visibleLoans.setAll(customerService.filterLoansByNumber(allLoans, loanSearchField.getText()));
        updateLoanPlaceholder();
        updateLoanStatusLabel();
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
            if (failedCustomers > 0) {
                text += " (" + failedCustomers + " customer" + (failedCustomers == 1 ? "" : "s") + " skipped)";
            }
        }
        loanStatusLabel.setText(text);
    }

    // ------------------------------------------------------------------
    // Primary emails (one request per customer, loaded in the background) using async
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
        private final HBox row = new HBox();
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

            nameLabel.setStyle("-fx-text-fill: #011936; -fx-font-family: 'Roboto Bold'; -fx-font-size: 13;");
            idLabel.setStyle("-fx-text-fill: #011936; -fx-font-family: 'Roboto'; -fx-font-size: 13;");
            emailLabel.setStyle("-fx-text-fill: #3b3a7d; -fx-font-family: 'Roboto'; -fx-font-size: 13;");

            row.setAlignment(Pos.CENTER_LEFT);
            row.setPadding(new Insets(0, 12, 0, 12));
            row.setPrefHeight(ROW_HEIGHT);
            row.getChildren().addAll(nameLabel, idLabel, emailLabel);

            setStyle(ROW_STYLE);
            setOnMouseEntered(e -> {
                if (!isEmpty()) {
                    setStyle(ROW_HOVER_STYLE);
                }
            });
            setOnMouseExited(e -> setStyle(ROW_STYLE));
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
    // Clicking a customer
    // ------------------------------------------------------------------

    private void handleCustomerSelected(CustomerSummary customer) {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("customer.fxml"));
            Parent root = loader.load();

            stopLoanLoading();   // don't compete with the customer screen for the mainframe
            CustomerController controller = loader.getController();
            controller.setServices(loginService, customerService, emailService);
            controller.loadCustomer(customer.getNumber());

            Stage stage = (Stage) customerListView.getScene().getWindow();
            Scene scene = new Scene(root);
            stage.setScene(scene);
            stage.show();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    // ------------------------------------------------------------------
    // Sign out
    // ------------------------------------------------------------------

    @FXML
    private void handleSignOut(ActionEvent event) {
        signOutButton.setDisable(true);
        stopBackgroundWork();

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