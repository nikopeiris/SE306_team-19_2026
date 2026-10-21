package lavs.ui;

import java.net.URL;
import java.util.List;

import business.system.Customer;
import business.system.LoginService;
import business.system.customer.CustomerEmailService;
import business.system.dto.CustomerSummary;
import business.system.dto.LoanSummary;
import javafx.concurrent.Task;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.ListView;
import javafx.scene.control.ProgressBar;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.layout.Pane;
import javafx.stage.Stage;

/**
 * The dashboard screen. This class only owns what dashboard.fxml injects and wires the
 * components together; the real work lives in:
 * <ul>
 *   <li>{@link CustomerListPanel} - customer list, search, emails (uses {@link EmailDirectory})</li>
 *   <li>{@link LoanLoader} - loan data, background loading and retries</li>
 *   <li>{@link LoanTablePanel} - the Loans card (table, search, status filter)</li>
 *   <li>{@link LoanSummaryCard} - totals, status bubbles, progress bar</li>
 *   <li>{@link RecentListsPanel} - the Recent Customers / Recent Loans strip</li>
 *   <li>{@link DashboardOverlays} - customer and loan-summary overlays</li>
 * </ul>
 */
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

    // Components
    private DashboardOverlays overlays;
    private CustomerListPanel customerPanel;
    private LoanLoader loanLoader;
    private LoanTablePanel loansPanel;
    private LoanSummaryCard summaryCard;

    @FXML
    private void initialize() {
        overlays = new DashboardOverlays(dashboardRoot);

        summaryCard = new LoanSummaryCard(
                summaryTotalsLabel, summaryBubblePane, summaryProgressBar, summaryProgressLabel);

        loanLoader = new LoanLoader(new LoanLoader.Listener() {
            @Override public void loadReset() {
                loansPanel.clear();
                updateSummary();
            }
            @Override public void loansChanged() { loansPanel.show(); }
            @Override public void statusesChanged() { loansPanel.populateStatuses(); }
            @Override public void statusLabelChanged() { loansPanel.updateStatusLabel(); }
        });

        loansPanel = new LoanTablePanel(loanTable,
                loanIdColumn, loanCustomerColumn, loanPrincipalColumn, loanDateColumn, loanStatusColumn,
                loanSearchField, loanStatusFilter, loanStatusLabel,
                loanLoader, this::openLoanSummary, this::updateSummary);
        // a loan has to be picked before its status can be changed
        changeStatusButton.disableProperty().bind(loansPanel.selectedLoanProperty().isNull());

        customerPanel = new CustomerListPanel(customerListView, customerSearchField,
                new CustomerListPanel.Host() {
                    @Override public void customersLoaded(List<CustomerSummary> customers) {
                        loanLoader.loadAll(customers);
                    }
                    @Override public void customersFound(List<CustomerSummary> customers, boolean force) {
                        loanLoader.fetchFor(customers, force);
                    }
                    @Override public void openCustomer(CustomerSummary customer, String number) {
                        showCustomerOverlay(customer, number);
                    }
                });

        new RecentListsPanel(recentCustomersList, recentLoansList,
                customer -> showCustomerOverlay(customer, customer.getNumber()),
                this::openLoanSummary);

        updateSummary();
    }

    /** Called by LoginController when it opens the dashboard. */
    public void setServices(LoginService loginService, Customer customerService,
            CustomerEmailService emailService) {
        this.loginService = loginService;
        this.customerService = customerService;
        this.emailService = emailService;

        overlays.setServices(loginService, customerService, emailService);
        loanLoader.setService(customerService);
        loansPanel.setService(customerService);
        summaryCard.setService(customerService);
        customerPanel.setServices(customerService, emailService);

        customerPanel.load();
    }

    private void updateSummary() {
        summaryCard.update(loanLoader.loans());
    }

    // ------------------------------------------------------------------
    // Customers
    // ------------------------------------------------------------------

    // add customer button
    @FXML
    private void handleAddCustomer(ActionEvent event) {
        showCustomerOverlay(null, null);
    }

    @FXML
    private void handleReloadCustomers(ActionEvent event) {
        customerPanel.reload();
    }

    private void showCustomerOverlay(CustomerSummary customer, String customerNumber) {
        overlays.showCustomer(customer, customerNumber,
                () -> {
                    // a loan may have been added on the customer's loan tab
                    if (customer != null) {
                        loanLoader.fetchFor(List.of(customerPanel.currentSummary(customer)), true);
                    }
                },
                customerPanel::showSaved);
    }

    // ------------------------------------------------------------------
    // Loans
    // ------------------------------------------------------------------

    @FXML
    private void handleChangeStatus(ActionEvent event) {
        LoanSummary selected = loansPanel.selectedLoan();
        if (selected == null) {
            return;
        }
        LoanStatusDialog.show(loanTable.getScene().getWindow(), customerService, selected,
                newStatus -> showNewStatus(selected, newStatus));
    }

    private void openLoanSummary(LoanSummary loan) {
        RecentItems.rememberLoan(loan);
        overlays.showLoanSummary(loan, loansPanel::show);
    }

    /** Shows the changed status without reloading every customer's loans. */
    private void showNewStatus(LoanSummary loan, String newStatus) {
        LoanSummary updated = loan.withStatus(newStatus);
        loanLoader.replaceLoan(loan, updated);
        RecentItems.rememberLoan(updated);
        loansPanel.populateStatuses();
        loansPanel.show();
    }

    // ------------------------------------------------------------------
    // Sign out
    // ------------------------------------------------------------------

    @FXML
    private void handleSignOut(ActionEvent event) {
        signOutButton.setDisable(true);
        stopBackgroundWork();

        // the next person to log in should not see this user's recent items
        RecentItems.clear();

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

        UiTasks.startDaemon(logoutTask, "logout-task");
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

    // stops any loan loading and email lookups still running when the user leaves the dashboard
    private void stopBackgroundWork() {
        loanLoader.stop();
        customerPanel.shutdown();
    }
}