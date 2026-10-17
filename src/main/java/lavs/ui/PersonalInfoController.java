package lavs.ui;

import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.DatePicker;
import javafx.scene.control.TextField;
import javafx.scene.layout.Pane;

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

                if (jobTitle.isBlank() || company.isBlank() || salary.isBlank() || startDate == null) {
                    throw new IllegalArgumentException("Job title, company, start date, and salary are required when employed.");
                }
            }

            if (secondaryEmploymentPane != null && secondaryEmploymentPane.isVisible()) {
                String secJobTitle = secondaryJobTitleField != null ? secondaryJobTitleField.getText() : "";
                String secCompany = secondaryCompanyField != null ? secondaryCompanyField.getText() : "";
                String secSalary = secondarySalaryField != null ? secondarySalaryField.getText() : "";
                var secStartDate = secondaryStartDateField != null ? secondaryStartDateField.getValue() : null;

                if (secJobTitle.isBlank() || secCompany.isBlank() || secSalary.isBlank() || secStartDate == null) {
                    throw new IllegalArgumentException("Job title, company, start date, and salary are required for secondary employment.");
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
}