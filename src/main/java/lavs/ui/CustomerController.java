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
    @FXML private ComboBox<String> employerField;
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
    @FXML
    private Button personalNextButton;
    
    @FXML private Pane secondaryEmploymentPane;
    @FXML private TextField secondaryJobTitleField;
    @FXML private DatePicker secondaryStartDateField;
    @FXML private TextField secondarySalaryField;
    @FXML
    private Button removeSecondaryEmploymentButton;
    @FXML private TextField companyField;
    @FXML
    private TextField secondaryCompanyField;
    @FXML private VBox emailFieldsBox;

    // =====================================================================
    // Tab 2: Contact Info
    // =====================================================================
    @FXML private Text addressesHeading;
    @FXML private Text mailingAddressLabel;
    @FXML private Text mailingAddressText;
    @FXML private CheckBox residentialSameAsMailingCheckBox;
    @FXML
    private Button addAddressButton;
    @FXML private VBox addressListContainer;

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
    @FXML
    private Button contactNextButton;
    private boolean hasPrimaryAddress = false;
    private boolean hasMailingAddress = false;

    @FXML private VBox phoneNumberListContainer;
    private boolean hasPrimaryPhone = false;

    @FXML private VBox emailListContainer;
    private boolean hasPrimaryEmail = false;

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


    // Data structures for backing lists
    public record AddressDto(String type, String line1, String line2, String suburb, String city, String country, String postCode, boolean isPrimary, boolean isMailing) {}
    public record PhoneDto(String type, String countryCode, String number, boolean isPrimary) {}
    public record EmailDto(String type, String email, boolean isPrimary) {}

    // Backing collections
    private final java.util.List<AddressDto> savedAddresses = new java.util.ArrayList<>();
    private final java.util.List<PhoneDto> savedPhones = new java.util.ArrayList<>();
    private final java.util.List<EmailDto> savedEmails = new java.util.ArrayList<>();

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
        if (employerField != null) {
          employerField.getItems().addAll("Employed", "None");

          // Listen for changes to toggle job details requirements
          employerField.valueProperty().addListener((obs, oldValue, newValue) -> {
            boolean isEmployed = "Employed".equals(newValue);

            // Enable or disable employment detail fields
            if (jobTitleField != null)
              jobTitleField.setDisable(!isEmployed);
            if (startDateField != null)
              startDateField.setDisable(!isEmployed);
            if (salaryField != null)
              salaryField.setDisable(!isEmployed);

            // Clear fields if "None" is selected
            if (!isEmployed) {
              if (jobTitleField != null)
                jobTitleField.clear();
              if (startDateField != null)
                startDateField.setValue(null);
              if (salaryField != null)
                salaryField.clear();
            }
          });
        }
        
        // Primary employer listener update
        if (employerField != null) {
            employerField.valueProperty().addListener((obs, oldValue, newValue) -> {
                boolean isEmployed = "Employed".equals(newValue);
                if (jobTitleField != null) jobTitleField.setDisable(!isEmployed);
                if (companyField != null) companyField.setDisable(!isEmployed); // Added
                if (startDateField != null) startDateField.setDisable(!isEmployed);
                if (salaryField != null) salaryField.setDisable(!isEmployed);
                
                if (!isEmployed) {
                    if (jobTitleField != null) jobTitleField.clear();
                    if (companyField != null) companyField.clear(); // Added
                    if (startDateField != null) startDateField.setValue(null);
                    if (salaryField != null) salaryField.clear();
                }
            });
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
            // Primary Employment Validation
            String employmentStatus = (employerField != null && employerField.getValue() != null) 
                ? employerField.getValue().toString() 
                : "";

            if ("Employed".equals(employmentStatus)) {
                String jobTitle = jobTitleField != null ? jobTitleField.getText() : "";
                String company = companyField != null ? companyField.getText() : ""; // Added
                String salary = salaryField != null ? salaryField.getText() : "";
                var startDate = startDateField != null ? startDateField.getValue() : null;

                if (jobTitle.isBlank() || company.isBlank() || salary.isBlank() || startDate == null) {
                    throw new IllegalArgumentException("Job title, company, start date, and salary are required when employed.");
                }
            }

            // Secondary Employment Validation
            if (secondaryEmploymentPane != null && secondaryEmploymentPane.isVisible()) {
                String secJobTitle = secondaryJobTitleField != null ? secondaryJobTitleField.getText() : "";
                String secCompany = secondaryCompanyField != null ? secondaryCompanyField.getText() : "";
                String secSalary = secondarySalaryField != null ? secondarySalaryField.getText() : "";
                var secStartDate = secondaryStartDateField != null ? secondaryStartDateField.getValue() : null;

                if (secJobTitle.isBlank() || secCompany.isBlank() || secSalary.isBlank() || secStartDate == null) {
                    throw new IllegalArgumentException("Job title, company, start date, and salary are required for secondary employment.");
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
        if (secondaryEmploymentPane != null) {
            secondaryEmploymentPane.setVisible(true);
            secondaryEmploymentPane.setManaged(true);
            addSecondaryEmploymentButton.setVisible(false); // Hide button once added
            addSecondaryEmploymentButton.setManaged(false);
        }
    }

    @FXML
    private void handleRemoveSecondaryEmployment(ActionEvent event) {
        if (secondaryEmploymentPane != null) {
            secondaryEmploymentPane.setVisible(false);
            secondaryEmploymentPane.setManaged(false);
            
            // Clear secondary fields when removed
            if (secondaryJobTitleField != null) secondaryJobTitleField.clear();
            if (secondaryStartDateField != null) secondaryStartDateField.setValue(null);
            if (secondarySalaryField != null) secondarySalaryField.clear();
            
            addSecondaryEmploymentButton.setVisible(true); // Bring button back
            addSecondaryEmploymentButton.setManaged(true);
        }
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
        javafx.stage.Stage popup = new javafx.stage.Stage();
        popup.initModality(javafx.stage.Modality.APPLICATION_MODAL);
        popup.setTitle("Add Customer Address");

        ComboBox<String> typeBox = new ComboBox<>();
        typeBox.getItems().addAll("Residential", "Postal");
        typeBox.setValue("Residential");
        typeBox.setPromptText("Address Type");

        TextField line1Field = new TextField(); line1Field.setPromptText("Line 1");
        TextField line2Field = new TextField(); line2Field.setPromptText("Line 2 (Optional)");
        TextField suburbField = new TextField(); suburbField.setPromptText("Suburb");
        TextField cityField = new TextField(); cityField.setPromptText("City");
        TextField countryField = new TextField(); countryField.setPromptText("Country");
        TextField postCodeField = new TextField(); postCodeField.setPromptText("Postcode");

        CheckBox primaryCheckBox = new CheckBox("Is Primary Address");
        CheckBox mailingCheckBox = new CheckBox("Is Mailing Address");

        Button saveAddressButton = new Button("Save Address");
        Button cancelButton = new Button("Cancel");

        saveAddressButton.setOnAction(e -> {
            try {
                String type = typeBox.getValue() != null ? typeBox.getValue() : "";
                String l1 = line1Field.getText();
                String l2 = line2Field.getText();
                String suburb = suburbField.getText();
                String city = cityField.getText();
                String country = countryField.getText();
                String postCode = postCodeField.getText();
                boolean isPrimary = primaryCheckBox.isSelected();
                boolean isMailing = mailingCheckBox.isSelected();
                String customerNum = customerIdField != null ? customerIdField.getText() : "TEMP";

                // Enforce single primary and single mailing rules
                if (isPrimary && hasPrimaryAddress) {
                    throw new IllegalArgumentException("Only one address can be set as Primary.");
                }
                if (isMailing && hasMailingAddress) {
                    throw new IllegalArgumentException("Only one address can be set as Mailing.");
                }

                // Validate using business layer rules[cite: 1]
                if (customerService != null) {
                    customerService.validateAddressDetails(city, country, customerNum, l1, null, postCode, suburb, type);
                } else {
                    if (city.isBlank() || country.isBlank() || l1.isBlank() || postCode.isBlank() || suburb.isBlank() || type.isBlank()) {
                        throw new IllegalArgumentException("All required address fields must be filled.");
                    }
                }

                // Create DTO object
                AddressDto newAddress = new AddressDto(type, l1, l2, suburb, city, country, postCode, isPrimary, isMailing);
                savedAddresses.add(newAddress);

                if (isPrimary) hasPrimaryAddress = true;
                if (isMailing) hasMailingAddress = true;

                // Create UI display row
                if (addressListContainer != null) {
                    javafx.scene.layout.HBox addressRow = new javafx.scene.layout.HBox(12);
                    addressRow.setAlignment(javafx.geometry.Pos.CENTER_LEFT);

                    String tags = "";
                    if (isPrimary) tags += " [PRIMARY]";
                    if (isMailing) tags += " [MAILING]";

                    String addressSummary = String.format("[%s]%s - %s, %s, %s, %s (%s)", 
                        type, tags, l1, suburb, city, country, postCode);
                    
                    Text addressText = new Text(addressSummary);
                    Button removeAddressBtn = new Button("Remove");
                    removeAddressBtn.setTextFill(javafx.scene.paint.Color.RED);
                    
                    removeAddressBtn.setOnAction(remEvent -> {
                        if (newAddress.isPrimary()) hasPrimaryAddress = false;
                        if (newAddress.isMailing()) hasMailingAddress = false;
                        savedAddresses.remove(newAddress);
                        addressListContainer.getChildren().remove(addressRow);
                    });

                    addressRow.getChildren().addAll(addressText, removeAddressBtn);
                    addressListContainer.getChildren().add(addressRow);
                }

                popup.close();
            } catch (Exception ex) {
                javafx.scene.control.Alert alert = new javafx.scene.control.Alert(javafx.scene.control.Alert.AlertType.WARNING);
                alert.setTitle("Address Error");
                alert.setHeaderText("Invalid Address");
                alert.setContentText(ex.getMessage());
                alert.showAndWait();
            }
        });

        cancelButton.setOnAction(e -> popup.close());

        VBox layout = new VBox(10);
        layout.setPadding(new javafx.geometry.Insets(20));
        layout.getChildren().addAll(
            new Text("Enter Address Details:"), typeBox, line1Field, line2Field, suburbField,
            cityField, countryField, postCodeField, primaryCheckBox, mailingCheckBox,
            new javafx.scene.layout.HBox(10, saveAddressButton, cancelButton)
        );

        popup.setScene(new javafx.scene.Scene(layout, 350, 520));
        popup.showAndWait();
    }

    @FXML
    private void handleAddNumber(ActionEvent event) {
        javafx.stage.Stage popup = new javafx.stage.Stage();
        popup.initModality(javafx.stage.Modality.APPLICATION_MODAL);
        popup.setTitle("Add Phone Number");

        ComboBox<String> typeBox = new ComboBox<>();
        typeBox.getItems().addAll("Mobile", "Home", "Work");
        typeBox.setValue("Mobile");
        typeBox.setPromptText("Phone Type");

        ComboBox<String> countryCodeBox = new ComboBox<>();
        countryCodeBox.getItems().addAll("+64 (NZ)", "+61 (AU)", "+1 (US/CA)", "+44 (UK)", "+86 (CN)", "Other");
        countryCodeBox.setValue("+64 (NZ)");
        countryCodeBox.setEditable(true);

        TextField phoneNumberField = new TextField();
        phoneNumberField.setPromptText("Phone Number (e.g. 0211234567)");

        // Numerical check for phone number input
        phoneNumberField.textProperty().addListener((obs, oldValue, newValue) -> {
            if (!newValue.matches("\\d*")) {
                phoneNumberField.setText(newValue.replaceAll("[^\\d]", ""));
            }
        });

        CheckBox primaryCheckBox = new CheckBox("Is Primary Phone");

        Button savePhoneButton = new Button("Save Phone");
        Button cancelButton = new Button("Cancel");

        savePhoneButton.setOnAction(e -> {
            try {
                String type = typeBox.getValue() != null ? typeBox.getValue() : "";
                String countryCode = countryCodeBox.getValue() != null ? countryCodeBox.getValue() : "";
                String number = phoneNumberField.getText().trim();
                boolean isPrimary = primaryCheckBox.isSelected();

                if (number.isBlank()) {
                    throw new IllegalArgumentException("Phone number cannot be empty.");
                }

                // Enforce single primary phone rule
                if (isPrimary && hasPrimaryPhone) {
                    throw new IllegalArgumentException("Only one phone number can be set as Primary.");
                }

              PhoneDto newPhone = new PhoneDto(type, countryCode, number, isPrimary);
              savedPhones.add(newPhone);

              if (isPrimary) hasPrimaryPhone = true;

              if (phoneNumberListContainer != null) {
                  javafx.scene.layout.HBox phoneRow = new javafx.scene.layout.HBox(12);
                  phoneRow.setAlignment(javafx.geometry.Pos.CENTER_LEFT);

                  String tag = isPrimary ? " [PRIMARY]" : "";
                  String phoneSummary = String.format("[%s]%s %s %s", type, tag, countryCode, number);

                  Text phoneText = new Text(phoneSummary);
                  Button removePhoneBtn = new Button("Remove");
                  removePhoneBtn.setTextFill(javafx.scene.paint.Color.RED);

                  removePhoneBtn.setOnAction(remEvent -> {
                      if (newPhone.isPrimary()) hasPrimaryPhone = false;
                      savedPhones.remove(newPhone);
                      phoneNumberListContainer.getChildren().remove(phoneRow);
                  });

                  phoneRow.getChildren().addAll(phoneText, removePhoneBtn);
                  phoneNumberListContainer.getChildren().add(phoneRow);
              }

              popup.close();
            } catch (Exception ex) {
                javafx.scene.control.Alert alert = new javafx.scene.control.Alert(javafx.scene.control.Alert.AlertType.WARNING);
                alert.setTitle("Phone Number Error");
                alert.setHeaderText("Invalid Phone Number");
                alert.setContentText(ex.getMessage());
                alert.showAndWait();
            }
        });

        cancelButton.setOnAction(e -> popup.close());

        VBox layout = new VBox(10);
        layout.setPadding(new javafx.geometry.Insets(20));
        layout.getChildren().addAll(
            new Text("Enter Phone Number Details:"), typeBox, countryCodeBox, phoneNumberField, primaryCheckBox,
            new javafx.scene.layout.HBox(10, savePhoneButton, cancelButton)
        );

        popup.setScene(new javafx.scene.Scene(layout, 340, 280));
        popup.showAndWait();
    }

    @FXML
    private void handleAddEmail(ActionEvent event) {
        javafx.stage.Stage popup = new javafx.stage.Stage();
        popup.initModality(javafx.stage.Modality.APPLICATION_MODAL);
        popup.setTitle("Add Email Address");

        ComboBox<String> typeBox = new ComboBox<>();
        typeBox.getItems().addAll("Personal", "Work");
        typeBox.setValue("Personal");
        typeBox.setPromptText("Email Type");

        TextField emailField = new TextField();
        emailField.setPromptText("email@example.com");

        CheckBox primaryCheckBox = new CheckBox("Is Primary Email");

        Button saveEmailButton = new Button("Save Email");
        Button cancelButton = new Button("Cancel");

        saveEmailButton.setOnAction(e -> {
            try {
                String type = typeBox.getValue() != null ? typeBox.getValue() : "";
                String email = emailField.getText().trim();
                boolean isPrimary = primaryCheckBox.isSelected();

                if (email.isBlank() || !email.contains("@")) {
                    throw new IllegalArgumentException("Please enter a valid email address.");
                }

                // Enforce single primary email rule
                if (isPrimary && hasPrimaryEmail) {
                    throw new IllegalArgumentException("Only one email address can be set as Primary.");
                }

                EmailDto newEmail = new EmailDto(type, email, isPrimary);
                savedEmails.add(newEmail);

                if (isPrimary) hasPrimaryEmail = true;

                if (emailListContainer != null) {
                    javafx.scene.layout.HBox emailRow = new javafx.scene.layout.HBox(12);
                    emailRow.setAlignment(javafx.geometry.Pos.CENTER_LEFT);

                    String tag = isPrimary ? " [PRIMARY]" : "";
                    String emailSummary = String.format("[%s]%s %s", type, tag, email);

                    Text emailText = new Text(emailSummary);
                    Button removeEmailBtn = new Button("Remove");
                    removeEmailBtn.setTextFill(javafx.scene.paint.Color.RED);

                    removeEmailBtn.setOnAction(remEvent -> {
                        if (newEmail.isPrimary()) hasPrimaryEmail = false;
                        savedEmails.remove(newEmail);
                        emailListContainer.getChildren().remove(emailRow);
                    });

                    emailRow.getChildren().addAll(emailText, removeEmailBtn);
                    emailListContainer.getChildren().add(emailRow);
                } 

                popup.close();
            } catch (Exception ex) {
                javafx.scene.control.Alert alert = new javafx.scene.control.Alert(javafx.scene.control.Alert.AlertType.WARNING);
                alert.setTitle("Email Error");
                alert.setHeaderText("Invalid Email");
                alert.setContentText(ex.getMessage());
                alert.showAndWait();
            }
        });

        cancelButton.setOnAction(e -> popup.close());

        VBox layout = new VBox(10);
        layout.setPadding(new javafx.geometry.Insets(20));
        layout.getChildren().addAll(
            new Text("Enter Email Details:"), typeBox, emailField, primaryCheckBox,
            new javafx.scene.layout.HBox(10, saveEmailButton, cancelButton)
        );

        popup.setScene(new javafx.scene.Scene(layout, 320, 240));
        popup.showAndWait();
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
    }

    @FXML
    private void handleShowFullPaymentSchedule(ActionEvent event) {}

    @FXML
    private void handleEditLoan(ActionEvent event) {}

    @FXML
    private void handleCloseLoanSummary(ActionEvent event) {}
}