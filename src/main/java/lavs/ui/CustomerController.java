package lavs.ui;

import business.system.Customer;
import business.system.LoginService;
import business.system.dto.CustomerSummary;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.DatePicker;
import javafx.scene.control.ListView;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.layout.Pane;
import javafx.scene.layout.VBox;
import javafx.scene.shape.Rectangle;
import javafx.scene.text.Text;

/**
 * Controller for customer.fxml (the add/edit customer screen).
 *
 * The screen has three tabs (Personal Info, Contact Info, Loan). Each tab is its own pane,
 * stacked in the same spot, and only one is visible at a time.
 */
public class CustomerController {

    // ---- Layout ----
    @FXML private Pane rootPane;
    @FXML private Rectangle card;
    @FXML private ButtonBar topButtonBar;

    // ---- Header ----
    @FXML private Text logoText;
    @FXML private Button signOutButton;

    // ---- Tab buttons ----
    @FXML private Button personalInfoTabButton;
    @FXML private Button contactInfoTabButton;
    @FXML private Button loanTabButton;

    // ---- Tab panes (one visible at a time) ----
    @FXML private Pane personalInfoPane;
    @FXML private Pane contactInfoPane;
    @FXML private Pane loanPane;

    // =====================================================================
    // Tab 1: Personal Info
    // =====================================================================
    @FXML private Text firstNameLabel;
    @FXML private TextField firstNameField;
    @FXML private Text lastNameLabel;
    @FXML private TextField lastNameField;
    @FXML private Text dateOfBirthLabel;
    @FXML private DatePicker dateOfBirthField;
    @FXML private Text employerLabel;
    @FXML private TextField employerField;
    @FXML private Text nationalityLabel;
    @FXML private ComboBox<String> nationalityField;
    @FXML private Text statusLabel;
    @FXML private ComboBox<String> statusField;
    @FXML private Text customerIdLabel;
    @FXML private TextField customerIdField;

    @FXML private Text jobTitleLabel;
    @FXML private TextField jobTitleField;
    @FXML private Text startDateLabel;
    @FXML private DatePicker startDateField;
    @FXML private Text salaryLabel;
    @FXML private TextField salaryField;
    @FXML private Text secondaryEmploymentText;
    @FXML private Button addSecondaryEmploymentButton;

    @FXML private Button personalCancelButton;
    @FXML private Button personalNextButton;

    // =====================================================================
    // Tab 2: Contact Info
    // =====================================================================
    @FXML private Text addressesHeading;
    @FXML private Text mailingAddressLabel;
    @FXML private Text mailingAddressText;
    @FXML private CheckBox residentialSameAsMailingCheckBox;
    @FXML private Button addAddressButton;

    @FXML private Text phoneNumbersHeading;
    @FXML private Text mobileNumberLabel;
    @FXML private Text mobileNumberText;
    @FXML private Text workNumberLabel;
    @FXML private Text workNumberText;
    @FXML private Button addNumberButton;

    @FXML private Text emailsHeading;
    @FXML private Text personalEmailLabel;
    @FXML private Text personalEmailText;
    @FXML private Text workEmailLabel;
    @FXML private Text workEmailText;
    @FXML private Button addEmailButton;

    @FXML private Button contactBackButton;
    @FXML private Button contactNextButton;

    // =====================================================================
    // Tab 3: Loan
    // =====================================================================
    @FXML private Text loansHeading;
    @FXML private TableView<Object> loansTable;
    @FXML private TableColumn<Object, String> loanSelectColumn;
    @FXML private TableColumn<Object, String> loanIdColumn;
    @FXML private TableColumn<Object, String> loanTypeColumn;
    @FXML private TableColumn<Object, String> loanAmountColumn;
    @FXML private TableColumn<Object, String> loanDateCreatedColumn;
    @FXML private Button addLoanButton;

    @FXML private Text coborrowerLoansHeading;
    @FXML private TableView<Object> coborrowerLoansTable;
    @FXML private TableColumn<Object, String> coborrowerLoanSelectColumn;
    @FXML private TableColumn<Object, String> coborrowerLoanIdColumn;
    @FXML private TableColumn<Object, String> coborrowerLoanTypeColumn;
    @FXML private TableColumn<Object, String> coborrowerLoanAmountColumn;
    @FXML private TableColumn<Object, String> coborrowerLoanDateCreatedColumn;

    @FXML private Button loanBackButton;
    @FXML private Button saveButton;

    // =====================================================================
    // Add Loan pane
    // =====================================================================
    @FXML private Pane addLoanPane;
    @FXML private Text loanTypeLabel;
    @FXML private ComboBox<String> loanTypeComboBox;
    @FXML private Text amountLabel;
    @FXML private TextField amountField;
    @FXML private Text loanTermLabel;
    @FXML private TextField loanTermField;
    @FXML private Text compoundTypeLabel;
    @FXML private ComboBox<String> compoundTypeComboBox;
    @FXML private Text rateLabel;
    @FXML private TextField rateField;
    @FXML private Text paymentFrequencyLabel;
    @FXML private ComboBox<String> paymentFrequencyComboBox;
    @FXML private CheckBox interestOnlyCheckBox;

    @FXML private CheckBox coborrowersCheckBox;
    @FXML private Pane coborrowerSection;
    @FXML private VBox selectedCoborrowersBox;
    @FXML private TextField coborrowerSearchField;
    @FXML private ListView<CustomerSummary> coborrowerResultsList;
    @FXML private Button addNewCoborrowerButton;

    @FXML private Button cancelLoanButton;
    @FXML private Button submitLoanButton;

    // =====================================================================
    // Loan Summary pane
    // =====================================================================
    @FXML private Pane loanSummaryPane;
    @FXML private Text loanSummaryTitleText;
    @FXML private TableView<Object> paymentScheduleTable;
    @FXML private TableColumn<Object, String> paymentDateColumn;
    @FXML private TableColumn<Object, String> paymentAmountColumn;
    @FXML private TableColumn<Object, String> paymentPrincipalColumn;
    @FXML private TableColumn<Object, String> paymentInterestColumn;
    @FXML private TableColumn<Object, String> paymentRemainingBalanceColumn;
    @FXML private Button showFullPaymentScheduleButton;
    @FXML private Button editLoanButton;
    @FXML private Button closeLoanSummaryButton;

    // Business layer services
    private LoginService loginService;
    private Customer customerService;

    @FXML
    private void initialize() {
        showPersonalInfoTab();
        
        if (nationalityField != null) {
            nationalityField.getItems().addAll("New Zealand", "British", "Australian", "American", "Other");
            nationalityField.setEditable(true);
        }

        if (statusField != null) {
            statusField.getItems().addAll("Active", "Reactive");
        }
        
        if (salaryField != null) {
            salaryField.textProperty().addListener((observable, oldValue, newValue) -> {
                if (!newValue.matches("\\d*(\\.\\d*)?")) {
                    salaryField.setText(oldValue);
                }
            });
        }
    }

    /** Called by whoever opens this screen to give it the business-layer services. */
    public void setServices(LoginService loginService, Customer customerService) {
        this.loginService = loginService;
        this.customerService = customerService;
    }

    // ------------------------------------------------------------------
    // Header
    // ------------------------------------------------------------------

    @FXML
    private void handleSignOut(ActionEvent event) {
        // TODO: log out through loginService and go back to the login screen
    }

    // ------------------------------------------------------------------
    // Tab buttons & Visibility Handlers
    // ------------------------------------------------------------------

    @FXML
    private void handlePersonalInfoTab(ActionEvent event) {
        showPersonalInfoTab();
    }

    @FXML
    private void handleContactInfoTab(ActionEvent event) {
        if (personalInfoPane.isVisible()) {
            validateAndProceedPersonalInfo(this::showContactInfoTab);
        } else {
            showContactInfoTab();
        }
    }

    @FXML
    private void handleLoanTab(ActionEvent event) {
        if (personalInfoPane.isVisible()) {
            validateAndProceedPersonalInfo(this::showLoanTab);
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

    /**
     * Validates Personal Info tab fields via business layer before proceeding.
     */
    private boolean validateAndProceedPersonalInfo(Runnable onSuccessAction) {
        try {
            String dob = (dateOfBirthField != null && dateOfBirthField.getValue() != null) 
                ? dateOfBirthField.getValue().toString() 
                : "";
            
            String familyName = lastNameField != null ? lastNameField.getText() : "";
            String firstName = firstNameField != null ? firstNameField.getText() : "";
            
            String nationality = "";
            if (nationalityField != null) {
                if (nationalityField.getSelectionModel().getSelectedItem() != null) {
                    nationality = nationalityField.getSelectionModel().getSelectedItem().toString();
                } else if (nationalityField.getEditor() != null) {
                    nationality = nationalityField.getEditor().getText();
                }
            }
            
            String title = (statusField != null && statusField.getSelectionModel().getSelectedItem() != null) 
                ? statusField.getSelectionModel().getSelectedItem().toString() 
                : "";
            
            Long organisation = 1L;
            if (employerField != null && !employerField.getText().isBlank()) {
                try {
                    organisation = Long.parseLong(employerField.getText().trim());
                } catch (NumberFormatException e) {
                    organisation = 0L; 
                }
            }

            if (customerService != null) {
                customerService.validateCustomerDetails(dob, familyName, firstName, nationality, organisation, title);
            } else {
                if (dob.isBlank() || familyName.isBlank() || firstName.isBlank() || nationality.isBlank() || title.isBlank()) {
                    throw new IllegalArgumentException("All personal details fields are required.");
                }
            }

            onSuccessAction.run();
            return true;

        } catch (Exception e) {
            System.err.println("Validation failed: " + e.getMessage());
            
            javafx.scene.control.Alert alert = new javafx.scene.control.Alert(javafx.scene.control.Alert.AlertType.WARNING);
            alert.setTitle("Validation Error");
            alert.setHeaderText("Cannot proceed to next tab");
            alert.setContentText(e.getMessage() != null ? e.getMessage() : "Please fill out all required personal info fields.");
            alert.showAndWait();
            
            return false;
        }
    }

    // ------------------------------------------------------------------
    // Tab 1: Personal Info Actions
    // ------------------------------------------------------------------

    @FXML
    private void handleAddSecondaryEmployment(ActionEvent event) {
        // TODO: add a second set of employment fields
    }

    @FXML
    private void handlePersonalInfoCancel(ActionEvent event) {
        // TODO: leave this screen and go back to the dashboard
    }

    @FXML
    private void handlePersonalInfoNext(ActionEvent event) {
        validateAndProceedPersonalInfo(this::showContactInfoTab);
    }

    // ------------------------------------------------------------------
    // Tab 2: Contact Info Actions
    // ------------------------------------------------------------------

    @FXML
    private void handleResidentialSameAsMailing(ActionEvent event) {
        // TODO: react to checkbox
    }

    @FXML
    private void handleAddAddress(ActionEvent event) {
        // TODO: open add address form
    }

    @FXML
    private void handleAddNumber(ActionEvent event) {
        // TODO: open add phone form
    }

    @FXML
    private void handleAddEmail(ActionEvent event) {
        // TODO: open add email form
    }

    @FXML
    private void handleContactInfoBack(ActionEvent event) {
        showPersonalInfoTab();
    }

    @FXML
    private void handleContactInfoNext(ActionEvent event) {
        showLoanTab();
    }

    // ------------------------------------------------------------------
    // Tab 3: Loan Actions
    // ------------------------------------------------------------------

    @FXML
    private void handleAddLoan(ActionEvent event) {
        if (addLoanPane != null) {
            addLoanPane.setVisible(true);
            addLoanPane.setManaged(true);
        }
    }

    @FXML
    private void handleCoborrowersCheckBox(ActionEvent event) {
        // TODO: show/hide coborrower section
    }

    @FXML
    private void handleAddNewCoborrower(ActionEvent event) {
        // TODO: add new coborrower
    }

    @FXML
    private void handleCancelLoan(ActionEvent event) {
        if (addLoanPane != null) {
            addLoanPane.setVisible(false);
            addLoanPane.setManaged(false);
        }
    }

    @FXML
    private void handleSubmitLoan(ActionEvent event) {
        if (addLoanPane != null) {
            addLoanPane.setVisible(false);
            addLoanPane.setManaged(false);
        }
    }

    @FXML
    private void handleLoanBack(ActionEvent event) {
        showContactInfoTab();
    }

    @FXML
    private void handleSave(ActionEvent event) {
        // TODO: save customer & loan details
    }

    @FXML
    private void handleShowFullPaymentSchedule(ActionEvent event) {}

    @FXML
    private void handleEditLoan(ActionEvent event) {}

    @FXML
    private void handleCloseLoanSummary(ActionEvent event) {}
}