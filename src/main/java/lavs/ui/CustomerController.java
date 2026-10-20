package lavs.ui;

import java.io.IOException;
import java.net.URL;
import java.util.function.Consumer;

import business.lavsexceptions.LavsException;
import business.system.Customer;
import business.system.LoginService;
import business.system.customer.CustomerEmailService;
import business.system.dto.CustomerDetails;
import business.system.dto.CustomerDetails.SaveResult;
import business.validation.ValidationException;
import javafx.concurrent.Task;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.scene.layout.Pane;
import javafx.stage.Stage;

public class CustomerController {

    @FXML
    private Pane personalInfoPane;
    @FXML
    private Pane contactInfoPane;
    @FXML
    private Pane loanPane;
    private Runnable onCloseAction;
    /** Told the customer number after every successful save, so the dashboard can show it. */
    private Consumer<String> onSavedAction;

    // Sub-controllers injected via JavaFX fx:id naming conventions
    // (matches fx:id of the fx:include tag + "Controller")
    @FXML
    private PersonalInfoController personalInfoTabController;
    @FXML
    private ContactInfoController contactInfoTabController;
    @FXML
    private LoanTabController loanTabController;

    private final CustomerContext context = new CustomerContext();
    private boolean saving = false;

    @FXML
    private void initialize() {
        // What the tabs are allowed to ask this screen to do
        context.setNavigation(new CustomerContext.Navigation() {
            @Override public void showPersonalInfo() { showPersonalInfoTab(); }
            @Override public void showContactInfo() { handleContactInfoTab(null); }
            @Override public void save() { saveCustomer(); }
            @Override public void cancel() { goToDashboard(); }
        });

        // Pass the shared context down to the sub-controllers
        if (personalInfoTabController != null)
            personalInfoTabController.init(context);
        if (contactInfoTabController != null)
            contactInfoTabController.init(context);
        if (loanTabController != null)
            loanTabController.init(context);

        showPersonalInfoTab();
    }

    public void setServices(LoginService loginService, Customer customerService,
            CustomerEmailService emailService) {
        context.setServices(loginService, customerService, emailService);
    }

    public void setOnCloseAction(Runnable onCloseAction) {
        this.onCloseAction = onCloseAction;
    }

    public void setOnSavedAction(Consumer<String> onSavedAction) {
        this.onSavedAction = onSavedAction;
    }

    // ------------------------------------------------------------------
    // Tabs
    // ------------------------------------------------------------------

    @FXML
    private void handlePersonalInfoTab(ActionEvent event) {
        showPersonalInfoTab();
    }

    @FXML
    private void handleContactInfoTab(ActionEvent event) {
        if (personalInfoPane.isVisible()) {
            if (personalInfoTabController != null && personalInfoTabController.validate()) {
                showContactInfoTab();
            }
        } else {
            showContactInfoTab();
        }
    }

    @FXML
    private void handleLoanTab(ActionEvent event) {
        if (personalInfoPane.isVisible()) {
            if (personalInfoTabController != null && personalInfoTabController.validate()) {
                showLoanTab();
            }
        } else {
            showLoanTab();
        }
    }

    private void showPersonalInfoTab() {
        show(personalInfoPane);
    }

    private void showContactInfoTab() {
        show(contactInfoPane);
    }

    private void showLoanTab() {
        show(loanPane);
        if (loanTabController != null)
            loanTabController.onShown();
    }

    private void show(Pane tab) {
        for (Pane pane : new Pane[] { personalInfoPane, contactInfoPane, loanPane }) {
            boolean visible = pane == tab;
            pane.setVisible(visible);
            pane.setManaged(visible);
        }
    }

    // ------------------------------------------------------------------
    // Loading an existing customer
    // ------------------------------------------------------------------

    /** Loads a customer from the mainframe and fills in every tab. */
    public void loadCustomer(String customerNumber) {
        Customer customerSvc = context.getCustomerService();

        Task<CustomerDetails> task = new Task<>() {
            @Override
            protected CustomerDetails call() throws Exception {
                return customerSvc.loadCustomerDetails(customerNumber);
            }
        };

        task.setOnSucceeded(e -> {
            CustomerDetails details = task.getValue();
            context.load(details);
            personalInfoTabController.populate(details);
            contactInfoTabController.refresh();
            if (loanTabController != null)
                loanTabController.refresh();
        });

        task.setOnFailed(e -> showError("Load Error", "Failed to load customer details",
                task.getException()));

        startThread(task, "load-customer-" + customerNumber);
    }

    // ------------------------------------------------------------------
    // Saving (create or edit)
    // ------------------------------------------------------------------

    private void saveCustomer() {
        if (saving) {
            return;
        }
        Customer customerSvc = context.getCustomerService();
        CustomerDetails original = context.getOriginal();
        CustomerDetails edited = new CustomerDetails(
                personalInfoTabController.readPersonal(),
                context.getSavedAddresses(),
                context.getSavedPhones(),
                context.getSavedEmails(),
                personalInfoTabController.readEmployers());

        saving = true;
        Task<SaveResult> task = new Task<>() {
            @Override
            protected SaveResult call() throws Exception {
                return customerSvc.saveCustomer(original, edited);
            }
        };

        task.setOnSucceeded(e -> {
            saving = false;
            SaveResult result = task.getValue();
            if (result.hasProblems()) {
                Alert alert = new Alert(Alert.AlertType.WARNING);
                alert.setTitle("Saved with problems");
                alert.setHeaderText("Customer " + result.customerNumber()
                        + " was saved, but some details could not be:");
                alert.setContentText(String.join("\n", result.problems()));
                alert.showAndWait();
            } else {
                Alert alert = new Alert(Alert.AlertType.INFORMATION);
                alert.setTitle("Customer saved");
                alert.setHeaderText(null);
                alert.setContentText("Customer " + result.customerNumber() + " saved.");
                alert.showAndWait();
            }
            if (onSavedAction != null) {
                onSavedAction.accept(result.customerNumber());
            }
            // Reload so the screen shows exactly what is in the mainframe
            // (and a new customer becomes an existing one for the next save)
            loadCustomer(result.customerNumber());
        });

        task.setOnFailed(e -> {
            saving = false;
            showError("Save Error", "Could not save customer", task.getException());
        });

        startThread(task, "save-customer");
    }

    // ------------------------------------------------------------------
    // Leaving the screen
    // ------------------------------------------------------------------

    private void goToDashboard() {
        if (onCloseAction != null) {
            onCloseAction.run();
        }
    }

    @FXML
    private void handleSignOut(ActionEvent event) {
        Task<Void> logout = new Task<>() {
            @Override
            protected Void call() {
                context.getLoginService().logout();
                return null;
            }
        };
        logout.setOnSucceeded(e -> goToLogin());
        logout.setOnFailed(e -> {
            // Even if the server couldn't be told, the user still wants to leave
            logout.getException().printStackTrace();
            goToLogin();
        });
        startThread(logout, "logout-task");
    }

    private void goToLogin() {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/lavs/ui/login.fxml"));
            Parent root = loader.load();

            LoginController loginController = loader.getController();
            loginController.setLoginService(context.getLoginService());
            loginController.setCustomerService(context.getCustomerService());
            loginController.setEmailService(context.getEmailService());

            switchScene(root, "Mainframe Interface - Login");
        } catch (Exception ex) {
            showError("Navigation Error", "Could not return to login", ex);
        }
    }

    private void switchScene(Parent root, String title) {
        Scene scene = new Scene(root, 1200, 800);
        URL css = getClass().getResource("/lavs/ui/styles.css");
        if (css != null) {
            scene.getStylesheets().add(css.toExternalForm());
        }
        Stage stage = (Stage) personalInfoPane.getScene().getWindow();
        stage.setScene(scene);
        stage.setTitle(title);
        stage.show();
    }

    // ------------------------------------------------------------------
    // Helpers
    // ------------------------------------------------------------------

    private void showError(String title, String header, Throwable error) {
        if (error != null) {
            error.printStackTrace();
        }
        Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.setTitle(title);
        alert.setHeaderText(header);
        alert.setContentText(describe(error));
        alert.showAndWait();
    }

    private static String describe(Throwable error) {
        if (error instanceof ValidationException || error instanceof LavsException) {
            return error.getMessage();
        } else if (error instanceof IOException) {
            return "Could not connect to the server.";
        }
        return error != null && error.getMessage() != null
                ? error.getMessage() : "Something went wrong.";
    }

    private static void startThread(Task<?> task, String name) {
        Thread thread = new Thread(task, name);
        thread.setDaemon(true);
        thread.start();
    }
}