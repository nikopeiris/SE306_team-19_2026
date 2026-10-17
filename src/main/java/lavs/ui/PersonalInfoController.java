package lavs.ui;

import java.time.LocalDate;

import business.system.customer.CustomerEmployerService;
import javafx.application.Platform;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.DatePicker;
import javafx.scene.control.TextField;
import javafx.scene.layout.Pane;
import mainframe.client.messages.customer.Retrieve;

public class PersonalInfoController {

    @FXML private TextField firstNameField;
    @FXML private TextField lastNameField;
    @FXML private DatePicker dateOfBirthField;
    @FXML private ComboBox<String> employerField;
    @FXML private ComboBox<String> nationalityField;
    @FXML private ComboBox<String> statusField;
    @FXML private TextField customerIdField;

    @FXML private TextField jobTitleField;
    @FXML private TextField companyField;
    @FXML private DatePicker startDateField;
    @FXML private TextField salaryField;
    @FXML private Button addSecondaryEmploymentButton;

    @FXML private Pane secondaryEmploymentPane;
    @FXML private TextField secondaryJobTitleField;
    @FXML private TextField secondaryCompanyField;
    @FXML private DatePicker secondaryStartDateField;
    @FXML private TextField secondarySalaryField;
    @FXML private Button removeSecondaryEmploymentButton;

    private CustomerContext context;

    /**
     * JavaFX automatically calls initialize() when the FXML file is loaded.
     */
    @FXML
    private void initialize() {
        // Populate Nationality ComboBox
        if (nationalityField != null) {
            nationalityField.getItems().addAll("New Zealand", "British", "Australian", "American", "Other");
            nationalityField.setEditable(true);
        }

        // Populate Status ComboBox
        if (statusField != null) {
            statusField.getItems().addAll("Active", "Reactive");
        }

        // Populate Employer ComboBox & Setup Toggle Listeners
        if (employerField != null) {
            employerField.getItems().addAll("Employed", "None");

            employerField.valueProperty().addListener((obs, oldValue, newValue) -> {
                boolean isEmployed = "Employed".equals(newValue);
                
                if (jobTitleField != null) jobTitleField.setDisable(!isEmployed);
                if (companyField != null) companyField.setDisable(!isEmployed);
                if (startDateField != null) startDateField.setDisable(!isEmployed);
                if (salaryField != null) salaryField.setDisable(!isEmployed);

                if (!isEmployed) {
                    if (jobTitleField != null) jobTitleField.clear();
                    if (companyField != null) companyField.clear();
                    if (startDateField != null) startDateField.setValue(null);
                    if (salaryField != null) salaryField.clear();
                }
            });
        }

        // Salary validation listener
        if (salaryField != null) {
            salaryField.textProperty().addListener((observable, oldValue, newValue) -> {
                if (!newValue.matches("\\d*(\\.\\d*)?")) {
                    salaryField.setText(oldValue);
                }
            });
        }
    }

    public void init(CustomerContext context) {
        this.context = context;
    }

    public boolean validate() {
        try {
            String dob = (dateOfBirthField != null && dateOfBirthField.getValue() != null) ? dateOfBirthField.getValue().toString() : "";
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
                ? statusField.getSelectionModel().getSelectedItem().toString() : "";

            String employmentStatus = (employerField != null && employerField.getValue() != null) ? employerField.getValue().toString() : "";

            if ("Employed".equals(employmentStatus)) {
                String jobTitle = jobTitleField != null ? jobTitleField.getText() : "";
                String company = companyField != null ? companyField.getText() : "";
                String salary = salaryField != null ? salaryField.getText() : "";
                var startDate = startDateField != null ? startDateField.getValue() : null;

                CustomerEmployerService.validateEmployerDetails(
                        "TEMP", company, jobTitle, parseSalary(salary), CustomerEmployerService.TYPE_PRIMARY);
                if (startDate == null) {
                    throw new IllegalArgumentException("Start date is required when employed.");
                }
            }

            if (secondaryEmploymentPane != null && secondaryEmploymentPane.isVisible()) {
                String secJobTitle = secondaryJobTitleField != null ? secondaryJobTitleField.getText() : "";
                String secCompany = secondaryCompanyField != null ? secondaryCompanyField.getText() : "";
                String secSalary = secondarySalaryField != null ? secondarySalaryField.getText() : "";
                var secStartDate = secondaryStartDateField != null ? secondaryStartDateField.getValue() : null;

                CustomerEmployerService.validateEmployerDetails(
                        "TEMP", secCompany, secJobTitle, parseSalary(secSalary), CustomerEmployerService.TYPE_SECONDARY);
                if (secStartDate == null) {
                    throw new IllegalArgumentException("Start date is required for secondary employment.");
                }
            }

            if (context != null && context.getCustomerService() != null) {
                context.getCustomerService().validateCustomerDetails(dob, familyName, firstName, nationality, 1L, title);
            } else {
                if (dob.isBlank() || familyName.isBlank() || firstName.isBlank() || nationality.isBlank() || title.isBlank()) {
                    throw new IllegalArgumentException("All personal details fields are required.");
                }
            }
            return true;
        } catch (Exception e) {
            Alert alert = new Alert(Alert.AlertType.WARNING);
            alert.setTitle("Validation Error");
            alert.setHeaderText("Cannot proceed to next tab");
            alert.setContentText(e.getMessage() != null ? e.getMessage() : "Please fill out all required personal info fields.");
            alert.showAndWait();
            return false;
        }
    }

    // the salary field only accepts digits and a dot, so anything unparsable (blank or ".") counts as missing
    private static Double parseSalary(String text) {
        try {
            return text == null || text.isBlank() ? null : Double.valueOf(text.trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    @FXML
    private void handleAddSecondaryEmployment(ActionEvent event) {
        if (secondaryEmploymentPane != null) {
            secondaryEmploymentPane.setVisible(true);
            secondaryEmploymentPane.setManaged(true);
            addSecondaryEmploymentButton.setVisible(false);
            addSecondaryEmploymentButton.setManaged(false);
        }
    }

    @FXML
    private void handleRemoveSecondaryEmployment(ActionEvent event) {
        if (secondaryEmploymentPane != null) {
            secondaryEmploymentPane.setVisible(false);
            secondaryEmploymentPane.setManaged(false);
            if (secondaryJobTitleField != null) secondaryJobTitleField.clear();
            if (secondaryStartDateField != null) secondaryStartDateField.setValue(null);
            if (secondarySalaryField != null) secondarySalaryField.clear();
            addSecondaryEmploymentButton.setVisible(true);
            addSecondaryEmploymentButton.setManaged(true);
        }
    }

    @FXML
    private void handlePersonalInfoNext(ActionEvent event) {
        if (validate()) {
            Pane personalPane = (Pane) customerIdField.getScene().lookup("#personalInfoPane");
            Pane contactPane = (Pane) customerIdField.getScene().lookup("#contactInfoPane");
            
            if (personalPane != null && contactPane != null) {
                personalPane.setVisible(false);
                personalPane.setManaged(false);
                contactPane.setVisible(true);
                contactPane.setManaged(true);
            }
        }
    }

    @FXML
    private void handlePersonalInfoCancel(ActionEvent event) {
        // Handle cancel navigation logic here
    }

    public void populateCustomerData(Retrieve retrieve) {
        Platform.runLater(() -> {
            if (firstNameField != null) {
                firstNameField.setText(retrieve.getFirstName());
            }
            if (lastNameField != null) {
                lastNameField.setText(retrieve.getFamilyName());
            }
            if (customerIdField != null) {
                customerIdField.setText(retrieve.getNumber());
            }
            if (dateOfBirthField != null && retrieve.getDateOfBirth() != null && !retrieve.getDateOfBirth().isBlank()) {
                try {
                    dateOfBirthField.setValue(LocalDate.parse(retrieve.getDateOfBirth().trim()));
                } catch (Exception ignored) {}
            }
            if (nationalityField != null && retrieve.getNationality() != null) {
                nationalityField.setValue(retrieve.getNationality());
            }
            if (statusField != null && retrieve.getTitle() != null) {
                statusField.setValue(retrieve.getTitle());
            }
        });
    }
}