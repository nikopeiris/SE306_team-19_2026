package lavs.ui;

import business.system.Customer;
import business.system.LoginService;
import javafx.application.Platform;
import javafx.concurrent.Task;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.scene.control.Alert;
import javafx.scene.layout.Pane;
import mainframe.client.messages.customer.Retrieve;

public class CustomerController {

    @FXML
    private Pane personalInfoPane;
    @FXML
    private Pane contactInfoPane;
    @FXML
    private Pane loanPane;

    // Sub-controllers injected via JavaFX fx:id naming conventions 
    // (matches fx:id of the fx:include tag + "Controller")
    @FXML
    private PersonalInfoController personalInfoTabController;
    @FXML
    private ContactInfoController contactInfoTabController;
    @FXML
    private LoanTabController loanTabController;

    private final CustomerContext context = new CustomerContext();

    @FXML
    private void initialize() {
        // Pass the shared context down to the sub-controllers
        if (personalInfoTabController != null)
            personalInfoTabController.init(context);
        if (contactInfoTabController != null)
            contactInfoTabController.init(context);
        if (loanTabController != null)
            loanTabController.init(context);

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
        personalInfoPane.setVisible(true);
        personalInfoPane.setManaged(true);
        contactInfoPane.setVisible(false);
        contactInfoPane.setManaged(false);
        loanPane.setVisible(false);
        loanPane.setManaged(false);
    }

    private void showContactInfoTab() {
        personalInfoPane.setVisible(false);
        personalInfoPane.setManaged(false);
        contactInfoPane.setVisible(true);
        contactInfoPane.setManaged(true);
        loanPane.setVisible(false);
        loanPane.setManaged(false);
    }

    private void showLoanTab() {
        personalInfoPane.setVisible(false);
        personalInfoPane.setManaged(false);
        contactInfoPane.setVisible(false);
        contactInfoPane.setManaged(false);
        loanPane.setVisible(true);
        loanPane.setManaged(true);
    }

    // Load customer data when clicked from the dashboard
    public void loadCustomer(String customerNumber) {
        Task<Void> task = new Task<>() {
            @Override
            protected Void call() throws Exception {
                Customer customerSvc = context.getCustomerService();

                // Retrieve Personal Info
                Retrieve retrieve = customerSvc.retrieveCustomer(customerNumber);

                // Clear previous context data
                context.getSavedAddresses().clear();
                context.getSavedPhones().clear();
                context.getSavedEmails().clear();

                // Load Addresses safely using reflection to handle SDK getter naming variations
                var addresses = customerSvc.listCustomerAddresses(customerNumber);
                if (addresses != null) {
                    for (var addr : addresses) {
                        context.getSavedAddresses().add(new CustomerContext.AddressDto(
                                readAddrVal(addr, "getType"),
                                readAddrVal(addr, "getLine1", "getStreet1", "getStreet"),
                                readAddrVal(addr, "getLine2", "getStreet2"),
                                readAddrVal(addr, "getSuburb", "getDistrict", "getTown"),
                                readAddrVal(addr, "getCity"),
                                readAddrVal(addr, "getCountry"),
                                readAddrVal(addr, "getPostCode", "getPostalCode", "getPostcode"),
                                readAddrBool(addr, "getIsPrimary", "isPrimary"),
                                readAddrBool(addr, "getIsMailing", "isMailing")));
                    }
                }

                // Load Phone Numbers
                var phones = customerSvc.listCustomerPhoneNumbers(customerNumber);
                if (phones != null) {
                    for (var phone : phones) {
                        context.getSavedPhones().add(new CustomerContext.PhoneDto(
                                phone.getType(), phone.getCountryCode(), phone.getNumber(),
                                "y".equals(phone.getIsPrimary())));
                    }
                }

                // Load Emails
                var emails = customerSvc.listCustomerEmails(customerNumber);
                if (emails != null) {
                    for (var email : emails) {
                        context.getSavedEmails().add(new CustomerContext.EmailDto(
                                email.getType(), email.getAddress(), "y".equals(email.getIsPrimary())));
                    }
                }

                // Load Employers
                var employers = customerSvc.listCustomerEmployers(customerNumber);

                Platform.runLater(() -> {
                    if (personalInfoTabController != null) {
                        personalInfoTabController.populateCustomerData(retrieve);
                        personalInfoTabController.populateEmployerData(employers);
                    }
                    if (contactInfoTabController != null) {
                        contactInfoTabController.populateContactData();
                    }
                });

                return null;
            }
        };

        task.setOnFailed(e -> {
            Throwable ex = task.getException();
            if (ex != null) {
                ex.printStackTrace();
                Platform.runLater(() -> {
                    Alert alert = new Alert(Alert.AlertType.ERROR);
                    alert.setTitle("Load Error");
                    alert.setHeaderText("Failed to load customer details");
                    alert.setContentText(ex.getMessage());
                    alert.showAndWait();
                });
            }
        });

        Thread thread = new Thread(task, "load-customer-" + customerNumber);
        thread.setDaemon(true);
        thread.start();
    }
    

    private static String readAddrVal(Object obj, String... getters) {
    for (String getter : getters) {
        try {
            java.lang.reflect.Method m = obj.getClass().getMethod(getter);
            Object val = m.invoke(obj);
            if (val != null) return val.toString().trim();
        } catch (Exception ignored) {}
    }
        return "";
    }

    private static boolean readAddrBool(Object obj, String... getters) {
        for (String getter : getters) {
            try {
                java.lang.reflect.Method m = obj.getClass().getMethod(getter);
                Object val = m.invoke(obj);
                if (val instanceof Boolean) return (Boolean) val;
                if (val != null) {
                    String s = val.toString().trim();
                    return "y".equalsIgnoreCase(s) || "true".equalsIgnoreCase(s) || "1".equals(s);
                }
            } catch (Exception ignored) {}
        }
        return false;
    }
}