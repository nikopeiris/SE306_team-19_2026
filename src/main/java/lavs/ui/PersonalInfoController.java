package lavs.ui;

import business.system.customer.CustomerEmployerService;
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
    @FXML private ComboBox<String> titleField;     // New Title field
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

    @FXML
    private void initialize() {
        // Populate Title ComboBox
        if (titleField != null) {
            titleField.getItems().addAll("Mr", "Ms", "Mrs", "Miss", "Dr", "Prof");
            titleField.setEditable(true);
        }

        // Populate Nationality ComboBox
        if (nationalityField != null) {
            nationalityField.getItems().addAll("New Zealand", "British", "Australian", "American", "Other");
            nationalityField.setEditable(true);
        }

        // Populate Status ComboBox
        if (statusField != null) {
            statusField.getItems().addAll("Active", "Reactive");
            statusField.setEditable(true);
        }

        // Populate Employer ComboBox & Setup Toggle Listeners
        if (employerField != null) {
            employerField.getItems().addAll("Employed", "None");
            employerField.setValue("None");

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

            // Extract Title value
            String title = "";
            if (titleField != null) {
                if (titleField.getSelectionModel().getSelectedItem() != null) {
                    title = titleField.getSelectionModel().getSelectedItem().toString();
                } else if (titleField.getEditor() != null) {
                    title = titleField.getEditor().getText();
                }
            }

            // Extract Status value
            String status = "";
            if (statusField != null) {
                if (statusField.getSelectionModel().getSelectedItem() != null) {
                    status = statusField.getSelectionModel().getSelectedItem().toString();
                } else if (statusField.getEditor() != null) {
                    status = statusField.getEditor().getText();
                }
            }

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

            // Validate mandatory fields locally before backend validation call
            if (title.isBlank()) {
                throw new IllegalArgumentException("Title is required.");
            }
            if (status.isBlank()) {
                throw new IllegalArgumentException("Status is required.");
            }

            if (context != null && context.getCustomerService() != null) {
                context.getCustomerService().validateCustomerDetails(dob, familyName, firstName, nationality, 1L, title);
            } else {
                if (dob.isBlank() || familyName.isBlank() || firstName.isBlank() || nationality.isBlank() || title.isBlank() || status.isBlank()) {
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

    public void populateEmployerData(Object[] employers) {
        if (employers == null || employers.length == 0) {
            if (employerField != null) employerField.setValue("None");
            return;
        }

        if (employerField != null) employerField.setValue("Employed");

        for (var emp : employers) {
            String type = readEmployerValue(emp, "getType", "getTypeName");
            String position = readEmployerValue(emp, "getPosition", "getJobTitle", "getRole");
            String name = readEmployerValue(emp, "getName", "getCompanyName", "getCompany");
            String salary = readEmployerValue(emp, "getSalary", "getAmount");

            if (CustomerEmployerService.TYPE_PRIMARY.equals(type)) {
                if (jobTitleField != null) jobTitleField.setText(position);
                if (companyField != null) companyField.setText(name);
                if (salaryField != null) salaryField.setText(salary);
            } else if (CustomerEmployerService.TYPE_SECONDARY.equals(type)) {
                if (secondaryEmploymentPane != null) {
                    secondaryEmploymentPane.setVisible(true);
                    secondaryEmploymentPane.setManaged(true);
                    if (addSecondaryEmploymentButton != null) {
                        addSecondaryEmploymentButton.setVisible(false);
                        addSecondaryEmploymentButton.setManaged(false);
                    }
                }
                if (secondaryJobTitleField != null) secondaryJobTitleField.setText(position);
                if (secondaryCompanyField != null) secondaryCompanyField.setText(name);
                if (secondarySalaryField != null) secondarySalaryField.setText(salary);
            }
        }
    }

    private static String readEmployerValue(Object employer, String... getters) {
        for (String getter : getters) {
            try {
                java.lang.reflect.Method m = employer.getClass().getMethod(getter);
                Object val = m.invoke(employer);
                if (val != null) return val.toString().trim();
            } catch (Exception ignored) {}
        }
        return "";
    }
    
    public void populateCustomerData(Retrieve retrieve) {
    javafx.application.Platform.runLater(() -> {
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
                dateOfBirthField.setValue(java.time.LocalDate.parse(retrieve.getDateOfBirth().trim()));
            } catch (Exception ignored) {}
        }
        if (nationalityField != null && retrieve.getNationality() != null) {
            nationalityField.setValue(retrieve.getNationality());
        }
        if (titleField != null && retrieve.getTitle() != null) {
            titleField.setValue(retrieve.getTitle());
        }
        if (statusField != null && retrieve.getStatus() != null) {
            statusField.setValue(retrieve.getStatus());
        } else if (statusField != null) {
            statusField.setValue("Active"); // Default fallback
        }
    });
    }

    private static String readEmployerValue(Object employer, String getter) {
        try {
            return String.valueOf(employer.getClass().getMethod(getter).invoke(employer));
        } catch (ReflectiveOperationException e) {
            throw new IllegalArgumentException("Unable to read employer details.", e);
        }
    }

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
}