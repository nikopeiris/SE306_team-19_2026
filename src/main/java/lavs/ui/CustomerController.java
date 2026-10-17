package lavs.ui;

import business.system.Customer;
import business.system.LoginService;
import javafx.concurrent.Task;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.scene.layout.Pane;
import mainframe.client.messages.customer.Retrieve;

public class CustomerController {

    @FXML private Pane personalInfoPane;
    @FXML private Pane contactInfoPane;
    @FXML private Pane loanPane;

    // Sub-controllers injected via JavaFX fx:id naming conventions 
    // (matches fx:id of the fx:include tag + "Controller")
    @FXML private PersonalInfoController personalInfoTabController;
    @FXML private ContactInfoController contactInfoTabController;
    @FXML private LoanTabController loanTabController;

    private final CustomerContext context = new CustomerContext();

    @FXML
    private void initialize() {
        // Pass the shared context down to the sub-controllers
        if (personalInfoTabController != null) personalInfoTabController.init(context);
        if (contactInfoTabController != null) contactInfoTabController.init(context);
        if (loanTabController != null) loanTabController.init(context);

        showPersonalInfoTab();
    }

    public void setServices(LoginService loginService, Customer customerService) {
        context.setServices(loginService, customerService);
    }

    @FXML
    private void handleSignOut(ActionEvent event) {
        // TODO: log out through loginService and go back to login screen
    }

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
        personalInfoPane.setVisible(true); personalInfoPane.setManaged(true);
        contactInfoPane.setVisible(false); contactInfoPane.setManaged(false);
        loanPane.setVisible(false); loanPane.setManaged(false);
    }

    private void showContactInfoTab() {
        personalInfoPane.setVisible(false); personalInfoPane.setManaged(false);
        contactInfoPane.setVisible(true); contactInfoPane.setManaged(true);
        loanPane.setVisible(false); loanPane.setManaged(false);
    }

    private void showLoanTab() {
        personalInfoPane.setVisible(false);
        personalInfoPane.setManaged(false);
        contactInfoPane.setVisible(false);
        contactInfoPane.setManaged(false);
        loanPane.setVisible(true);
        loanPane.setManaged(true);
    }
    

    // load customer if you click on it
    public void loadCustomer(String customerNumber) {
    Task<Retrieve> task = new Task<>() {
        @Override
        protected Retrieve call() throws Exception {
            return context.getCustomerService().retrieveCustomer(customerNumber);
        }
    };

    task.setOnSucceeded(e -> {
        Retrieve retrieve = task.getValue();
        if (retrieve != null) {
            if (personalInfoTabController != null) {
                personalInfoTabController.populateCustomerData(retrieve);
            }
            // TODO: If you have service calls for existing addresses, phones, and emails, 
            // you can load them here and populate context.getSavedAddresses(), etc.
        }
    });

    task.setOnFailed(e -> {
        Throwable ex = task.getException();
        if (ex != null) {
            ex.printStackTrace();
        }
    });

    Thread thread = new Thread(task, "load-customer-" + customerNumber);
    thread.setDaemon(true);
    thread.start();
}
}