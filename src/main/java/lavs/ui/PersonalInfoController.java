package lavs.ui;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import business.system.customer.CustomerOptions;
import business.system.dto.CustomerDetails;
import business.system.dto.CustomerDetails.Employer;
import business.system.dto.CustomerDetails.Personal;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.DatePicker;
import javafx.scene.control.TextField;
import javafx.scene.layout.Pane;

/**
 * The personal details tab. It only reads and writes form fields; every rule
 * (what is required, what is valid) is checked by the business layer.
 */
public class PersonalInfoController {

    private static final String EMPLOYED = "Employed";
    private static final String NOT_EMPLOYED = "None";
    private static final String ASSIGNED_ON_SAVE = "Assigned on save";

    @FXML private TextField customerIdField;
    @FXML private TextField statusField;
    @FXML private ComboBox<String> titleField;
    @FXML private TextField firstNameField;
    @FXML private TextField lastNameField;
    @FXML private DatePicker dateOfBirthField;
    @FXML private ComboBox<String> nationalityField;
    @FXML private ComboBox<String> employerField;

    @FXML private TextField jobTitleField;
    @FXML private TextField companyField;
    @FXML private TextField salaryField;
    @FXML private Button addSecondaryEmploymentButton;

    @FXML private Pane secondaryEmploymentPane;
    @FXML private TextField secondaryJobTitleField;
    @FXML private TextField secondaryCompanyField;
    @FXML private TextField secondarySalaryField;

    @FXML private DocumentsSectionController documentsSectionController;

    private CustomerContext context;

    // ids of the employers as loaded, so saving updates them instead of adding duplicates
    private Long primaryEmployerId;
    private Long secondaryEmployerId;

    @FXML
    private void initialize() {
        titleField.getItems().addAll(CustomerOptions.TITLES);
        titleField.setEditable(true);
        nationalityField.getItems().addAll(CustomerOptions.NATIONALITIES);
        nationalityField.setEditable(true);

        customerIdField.setEditable(false);
        statusField.setEditable(false);
        customerIdField.setText(ASSIGNED_ON_SAVE);
        statusField.setText(ASSIGNED_ON_SAVE);

        employerField.getItems().addAll(NOT_EMPLOYED, EMPLOYED);
        employerField.valueProperty().addListener(
                (obs, oldValue, newValue) -> setEmploymentEnabled(EMPLOYED.equals(newValue)));
        employerField.setValue(NOT_EMPLOYED);
        setEmploymentEnabled(false);

        numbersOnly(salaryField);
        numbersOnly(secondarySalaryField);
    }

    public void init(CustomerContext context) {
        this.context = context;
        documentsSectionController.init(context);
    }

    // ------------------------------------------------------------------
    // Reading the form
    // ------------------------------------------------------------------

    public Personal readPersonal() {
        CustomerDetails original = context.getOriginal();
        Personal loaded = original == null ? null : original.personal();
        return new Personal(
                loaded == null ? "" : loaded.number(),
                text(titleField),
                firstNameField.getText(),
                lastNameField.getText(),
                dateOfBirthField.getValue() == null ? "" : dateOfBirthField.getValue().toString(),
                text(nationalityField),
                loaded == null ? null : loaded.organisation(),
                loaded == null ? "" : loaded.status());
    }

    public List<Employer> readEmployers() {
        List<Employer> employers = new ArrayList<>();
        if (EMPLOYED.equals(employerField.getValue())) {
            employers.add(new Employer(primaryEmployerId, CustomerOptions.EMPLOYER_PRIMARY,
                    companyField.getText(), jobTitleField.getText(),
                    parseSalary(salaryField.getText())));
            if (secondaryEmploymentPane.isVisible()) {
                employers.add(new Employer(secondaryEmployerId, CustomerOptions.EMPLOYER_SECONDARY,
                        secondaryCompanyField.getText(), secondaryJobTitleField.getText(),
                        parseSalary(secondarySalaryField.getText())));
            }
        }
        return employers;
    }

    /** Asks the business layer whether this tab is OK; shows its message if not. */
    public boolean validate() {
        try {
            context.getCustomerService().validatePersonalDetails(readPersonal());
            context.getCustomerService().validateEmployers(readEmployers());
            return true;
        } catch (Exception e) {
            Alert alert = new Alert(Alert.AlertType.WARNING);
            alert.setTitle("Validation Error");
            alert.setHeaderText("Please fix the personal details");
            alert.setContentText(e.getMessage() != null
                    ? e.getMessage() : "Please fill out all required personal info fields.");
            alert.showAndWait();
            return false;
        }
    }

    // ------------------------------------------------------------------
    // Filling the form from a loaded customer
    // ------------------------------------------------------------------

    public void populate(CustomerDetails details) {
        Personal p = details.personal();
        customerIdField.setText(p.isNew() ? ASSIGNED_ON_SAVE : p.number());
        statusField.setText(p.status().isEmpty() ? ASSIGNED_ON_SAVE : p.status());
        titleField.setValue(p.title());
        firstNameField.setText(p.firstName());
        lastNameField.setText(p.familyName());
        nationalityField.setValue(p.nationality());
        try {
            dateOfBirthField.setValue(p.dateOfBirth().isEmpty()
                    ? null : java.time.LocalDate.parse(p.dateOfBirth()));
        } catch (java.time.format.DateTimeParseException e) {
            dateOfBirthField.setValue(null);
        }
        populateEmployers(details.employers());
        documentsSectionController.load(p.isNew() ? null : p.number());
    }

    private void populateEmployers(List<Employer> employers) {
        primaryEmployerId = null;
        secondaryEmployerId = null;
        Employer primary = null;
        Employer secondary = null;
        for (Employer e : employers) {
            if (CustomerOptions.EMPLOYER_PRIMARY.equals(e.type())) {
                primary = e;
            } else if (CustomerOptions.EMPLOYER_SECONDARY.equals(e.type())) {
                secondary = e;
            }
        }

        boolean employed = primary != null || secondary != null;
        employerField.setValue(employed ? EMPLOYED : NOT_EMPLOYED);
        setEmploymentEnabled(employed);

        if (primary != null) {
            primaryEmployerId = primary.id();
            jobTitleField.setText(primary.position());
            companyField.setText(primary.name());
            salaryField.setText(formatSalary(primary.salary()));
        }
        if (secondary != null) {
            secondaryEmployerId = secondary.id();
            secondaryJobTitleField.setText(secondary.position());
            secondaryCompanyField.setText(secondary.name());
            secondarySalaryField.setText(formatSalary(secondary.salary()));
            showSecondary(true);
        }
    }

    // ------------------------------------------------------------------
    // Employment form behaviour
    // ------------------------------------------------------------------

    private void setEmploymentEnabled(boolean employed) {
        jobTitleField.setDisable(!employed);
        companyField.setDisable(!employed);
        salaryField.setDisable(!employed);
        addSecondaryEmploymentButton.setDisable(!employed);
        if (!employed) {
            jobTitleField.clear();
            companyField.clear();
            salaryField.clear();
            clearSecondary();
            showSecondary(false);
        }
    }

    private void showSecondary(boolean visible) {
        secondaryEmploymentPane.setVisible(visible);
        secondaryEmploymentPane.setManaged(visible);
        addSecondaryEmploymentButton.setVisible(!visible);
        addSecondaryEmploymentButton.setManaged(!visible);
    }

    private void clearSecondary() {
        secondaryJobTitleField.clear();
        secondaryCompanyField.clear();
        secondarySalaryField.clear();
    }

    private static void numbersOnly(TextField field) {
        field.textProperty().addListener((obs, oldValue, newValue) -> {
            if (newValue != null && !newValue.matches("\\d*(\\.\\d*)?")) {
                field.setText(oldValue);
            }
        });
    }

    private static Double parseSalary(String text) {
        try {
            return text == null || text.isBlank() ? null : Double.valueOf(text.trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static String formatSalary(Double salary) {
        if (salary == null) {
            return "";
        }
        return salary == Math.rint(salary) ? String.valueOf(salary.longValue()) : salary.toString();
    }

    /** Text of an editable combo box, whether it was picked or typed. */
    private static String text(ComboBox<String> box) {
        return box.getEditor().getText();
    }

    // ------------------------------------------------------------------
    // Unsaved changes
    // ------------------------------------------------------------------

    /** True when this tab no longer matches what was loaded (or has been filled in on a new customer). */
    public boolean hasUnsavedChanges() {
        CustomerDetails original = context.getOriginal();
        Personal now = readPersonal();
        Personal before = original == null ? null : original.personal();

        if (!same(now.title(), before == null ? null : before.title())
                || !same(now.firstName(), before == null ? null : before.firstName())
                || !same(now.familyName(), before == null ? null : before.familyName())
                || !same(now.dateOfBirth(), before == null ? null : before.dateOfBirth())
                || !same(now.nationality(), before == null ? null : before.nationality())) {
            return true;
        }

        List<Employer> loadedEmployers = original == null ? List.of() : original.employers();
        return !employerKeys(readEmployers()).equals(employerKeys(loadedEmployers));
    }

    // null and blank count as the same, so an untouched field never looks edited
    private static boolean same(String a, String b) {
        return clean(a).equals(clean(b));
    }

    private static String clean(String text) {
        return text == null ? "" : text.trim();
    }

    // one comparable line per employer, sorted so the order they come in doesn't matter
    private static List<String> employerKeys(List<Employer> employers) {
        List<String> keys = new ArrayList<>();
        for (Employer e : employers) {
            if (CustomerOptions.EMPLOYER_PRIMARY.equals(e.type())
                    || CustomerOptions.EMPLOYER_SECONDARY.equals(e.type())) {
                keys.add(e.type() + "|" + clean(e.name()) + "|" + clean(e.position())
                        + "|" + formatSalary(e.salary()));
            }
        }
        Collections.sort(keys);
        return keys;
    }

    // ------------------------------------------------------------------
    // Buttons
    // ------------------------------------------------------------------

    @FXML
    private void handleAddSecondaryEmployment(ActionEvent event) {
        showSecondary(true);
    }

    @FXML
    private void handleRemoveSecondaryEmployment(ActionEvent event) {
        clearSecondary();
        showSecondary(false);
    }

    @FXML
    private void handlePersonalInfoNext(ActionEvent event) {
        context.getNavigation().showContactInfo();
    }

    @FXML
    private void handlePersonalInfoSave(ActionEvent event) {
        context.getNavigation().save();
    }
}