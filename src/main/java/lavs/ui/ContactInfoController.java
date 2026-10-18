package lavs.ui;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

import business.system.customer.CustomerOptions;
import business.system.dto.CustomerDetails.Address;
import business.system.dto.CustomerDetails.Email;
import business.system.dto.CustomerDetails.Phone;
import javafx.collections.FXCollections;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.stage.Modality;
import javafx.stage.Stage;

/**
 * The contact details tab: lists addresses, phone numbers and emails and lets the
 * user add, edit and remove them. Nothing is sent to the mainframe until the customer
 * is saved. Every validation rule is checked by the business layer.
 */
public class ContactInfoController {

    @FXML private VBox addressListContainer;
    @FXML private VBox phoneNumberListContainer;
    @FXML private VBox emailListContainer;

    private CustomerContext context;

    public void init(CustomerContext context) {
        this.context = context;
        refresh();
    }

    /** Redraws all three lists from the context. */
    public void refresh() {
        renderAddresses();
        renderPhones();
        renderEmails();
    }

    // ------------------------------------------------------------------
    // Lists
    // ------------------------------------------------------------------

    private void renderAddresses() {
        addressListContainer.getChildren().clear();
        if (context.getSavedAddresses().isEmpty()) {
            addressListContainer.getChildren().add(emptyLabel("No addresses added yet."));
        }
        for (Address a : new ArrayList<>(context.getSavedAddresses())) {
            String summary = String.format("[%s]%s%s  %s, %s, %s %s, %s",
                    a.type(), tag(a.primary(), "PRIMARY"), tag(a.mailing(), "MAILING"),
                    a.line1(), a.suburb(), a.city(), a.postCode(), a.country());
            addressListContainer.getChildren().add(row(summary,
                    () -> editAddress(a),
                    () -> { context.getSavedAddresses().remove(a); renderAddresses(); }));
        }
    }

    private void renderPhones() {
        phoneNumberListContainer.getChildren().clear();
        if (context.getSavedPhones().isEmpty()) {
            phoneNumberListContainer.getChildren().add(emptyLabel("No phone numbers added yet."));
        }
        for (Phone p : new ArrayList<>(context.getSavedPhones())) {
            String summary = String.format("[%s]%s  %s %s%s",
                    p.type(), tag(p.primary(), "PRIMARY"), p.countryCode(), p.number(),
                    p.canTxt() ? "  (can text)" : "");
            phoneNumberListContainer.getChildren().add(row(summary,
                    () -> editPhone(p),
                    () -> { context.getSavedPhones().remove(p); renderPhones(); }));
        }
    }

    private void renderEmails() {
        emailListContainer.getChildren().clear();
        if (context.getSavedEmails().isEmpty()) {
            emailListContainer.getChildren().add(emptyLabel("No email addresses added yet."));
        }
        for (Email e : new ArrayList<>(context.getSavedEmails())) {
            String summary = String.format("[%s]%s  %s",
                    e.type(), tag(e.primary(), "PRIMARY"), e.address());
            emailListContainer.getChildren().add(row(summary,
                    () -> editEmail(e),
                    () -> { context.getSavedEmails().remove(e); renderEmails(); }));
        }
    }

    private HBox row(String summary, Runnable onEdit, Runnable onRemove) {
        Label label = new Label(summary);
        label.setWrapText(true);
        label.setMaxWidth(Double.MAX_VALUE);
        HBox.setHgrow(label, Priority.ALWAYS);

        Button edit = new Button("Edit");
        edit.setOnAction(e -> onEdit.run());
        Button remove = new Button("Remove");
        remove.setStyle("-fx-text-fill: red;");
        remove.setOnAction(e -> onRemove.run());

        HBox row = new HBox(10, label, edit, remove);
        row.setAlignment(Pos.CENTER_LEFT);
        return row;
    }

    private static Label emptyLabel(String text) {
        Label label = new Label(text);
        label.setStyle("-fx-text-fill: #726e97;");
        return label;
    }

    private static String tag(boolean on, String name) {
        return on ? " [" + name + "]" : "";
    }

    // ------------------------------------------------------------------
    // Buttons on the tab
    // ------------------------------------------------------------------

    @FXML private void handleAddAddress(ActionEvent event) { editAddress(null); }
    @FXML private void handleAddNumber(ActionEvent event) { editPhone(null); }
    @FXML private void handleAddEmail(ActionEvent event) { editEmail(null); }

    @FXML
    private void handleBack(ActionEvent event) {
        context.getNavigation().showPersonalInfo();
    }

    @FXML
    private void handleSave(ActionEvent event) {
        context.getNavigation().save();
    }

    // ------------------------------------------------------------------
    // Add / edit dialogs (existing == null means "add")
    // ------------------------------------------------------------------

    private void editAddress(Address existing) {
        ComboBox<String> type = choice(CustomerOptions.ADDRESS_TYPES, existing == null ? null : existing.type());
        TextField otherType = input(existing == null ? "" : existing.otherType());
        TextField line1 = input(existing == null ? "" : existing.line1());
        TextField line2 = input(existing == null ? "" : existing.line2());
        TextField suburb = input(existing == null ? "" : existing.suburb());
        TextField city = input(existing == null ? "" : existing.city());
        TextField country = input(existing == null ? "" : existing.country());
        TextField postCode = input(existing == null ? "" : existing.postCode());
        CheckBox primary = new CheckBox("Primary address");
        primary.setSelected(existing != null && existing.primary());
        CheckBox mailing = new CheckBox("Mailing address");
        mailing.setSelected(existing != null && existing.mailing());
        otherType.disableProperty().bind(type.valueProperty().isNotEqualTo(CustomerOptions.ADDRESS_TYPE_OTHER));

        GridPane form = form(
                "Type", type, "Other type", otherType, "Line 1", line1, "Line 2 (optional)", line2,
                "Suburb", suburb, "City", city, "Country", country, "Post code", postCode);
        VBox content = new VBox(10, form, primary, mailing);

        showDialog(existing == null ? "Add Address" : "Edit Address", content, () -> {
            Address updated = new Address(existing == null ? null : existing.id(),
                    type.getValue(), otherType.getText(), line1.getText(), line2.getText(),
                    suburb.getText(), city.getText(), country.getText(), postCode.getText(),
                    primary.isSelected(), mailing.isSelected());
            List<Address> candidate = replacing(context.getSavedAddresses(), existing, updated);
            String problem = check(() -> context.getCustomerService().validateAddresses(candidate));
            if (problem == null) {
                context.getSavedAddresses().clear();
                context.getSavedAddresses().addAll(candidate);
                renderAddresses();
            }
            return problem;
        });
    }

    private void editPhone(Phone existing) {
        ComboBox<String> type = choice(CustomerOptions.PHONE_TYPES, existing == null ? null : existing.type());
        TextField otherType = input(existing == null ? "" : existing.otherType());
        ComboBox<String> countryCode = choice(CustomerOptions.COUNTRY_CODES,
                existing == null ? CustomerOptions.COUNTRY_CODES.get(0) : existing.countryCode());
        countryCode.setEditable(true);
        TextField number = input(existing == null ? "" : existing.number());
        CheckBox canTxt = new CheckBox("Can receive text messages");
        canTxt.setSelected(existing != null && existing.canTxt());
        CheckBox primary = new CheckBox("Primary phone number");
        primary.setSelected(existing != null && existing.primary());
        otherType.disableProperty().bind(type.valueProperty().isNotEqualTo(CustomerOptions.PHONE_TYPE_OTHER));

        GridPane form = form("Type", type, "Other type", otherType, "Country code", countryCode, "Number", number);
        VBox content = new VBox(10, form, canTxt, primary);

        showDialog(existing == null ? "Add Phone Number" : "Edit Phone Number", content, () -> {
            Phone updated = new Phone(existing == null ? null : existing.id(),
                    type.getValue(), otherType.getText(), countryCode.getEditor().getText(),
                    number.getText(), canTxt.isSelected(), primary.isSelected());
            List<Phone> candidate = replacing(context.getSavedPhones(), existing, updated);
            String problem = check(() -> context.getCustomerService().validatePhones(candidate));
            if (problem == null) {
                context.getSavedPhones().clear();
                context.getSavedPhones().addAll(candidate);
                renderPhones();
            }
            return problem;
        });
    }

    private void editEmail(Email existing) {
        ComboBox<String> type = choice(CustomerOptions.EMAIL_TYPES, existing == null ? null : existing.type());
        TextField otherType = input(existing == null ? "" : existing.otherType());
        TextField address = input(existing == null ? "" : existing.address());
        address.setPromptText("name@example.com");
        CheckBox primary = new CheckBox("Primary email address");
        primary.setSelected(existing != null && existing.primary());
        otherType.disableProperty().bind(type.valueProperty().isNotEqualTo(CustomerOptions.EMAIL_TYPE_OTHER));

        GridPane form = form("Type", type, "Other type", otherType, "Email", address);
        VBox content = new VBox(10, form, primary);

        showDialog(existing == null ? "Add Email Address" : "Edit Email Address", content, () -> {
            Email updated = new Email(existing == null ? null : existing.id(),
                    type.getValue(), otherType.getText(), address.getText(), primary.isSelected());
            List<Email> candidate = replacing(context.getSavedEmails(), existing, updated);
            String problem = check(() -> context.getCustomerService().validateEmails(candidate));
            if (problem == null) {
                context.getSavedEmails().clear();
                context.getSavedEmails().addAll(candidate);
                renderEmails();
            }
            return problem;
        });
    }

    // ------------------------------------------------------------------
    // Dialog and form helpers
    // ------------------------------------------------------------------

    /** Shows a modal dialog. {@code onSave} returns an error message, or null to close. */
    private void showDialog(String title, Node content, Supplier<String> onSave) {
        Stage popup = new Stage();
        popup.initModality(Modality.APPLICATION_MODAL);
        popup.initOwner(addressListContainer.getScene().getWindow());
        popup.setTitle(title);

        Label error = new Label();
        error.setStyle("-fx-text-fill: #b00020;");
        error.setWrapText(true);
        error.setMaxWidth(380);

        Button save = new Button("Save");
        save.setDefaultButton(true);
        save.setOnAction(e -> {
            String problem = onSave.get();
            if (problem == null) {
                popup.close();
            } else {
                error.setText(problem);
            }
        });
        Button cancel = new Button("Cancel");
        cancel.setCancelButton(true);
        cancel.setOnAction(e -> popup.close());

        VBox layout = new VBox(14, content, error, new HBox(10, save, cancel));
        layout.setPadding(new Insets(20));
        popup.setScene(new Scene(layout));
        popup.showAndWait();
    }

    @FunctionalInterface
    private interface Check {
        void run() throws Exception;
    }

    /** Runs a business-layer validation and returns its message, or null if it passed. */
    private static String check(Check validation) {
        try {
            validation.run();
            return null;
        } catch (Exception e) {
            return e.getMessage() != null ? e.getMessage() : "Invalid details";
        }
    }

    private static GridPane form(Object... labelsAndFields) {
        GridPane grid = new GridPane();
        grid.setHgap(12);
        grid.setVgap(10);
        for (int i = 0; i < labelsAndFields.length; i += 2) {
            grid.add(new Label((String) labelsAndFields[i]), 0, i / 2);
            grid.add((Node) labelsAndFields[i + 1], 1, i / 2);
        }
        return grid;
    }

    private static ComboBox<String> choice(List<String> items, String selected) {
        ComboBox<String> box = new ComboBox<>(FXCollections.observableArrayList(items));
        if (selected != null && !selected.isBlank() && !items.contains(selected)) {
            box.getItems().add(selected);   // a value from the server that is not in our list
        }
        box.setValue(selected != null && !selected.isBlank() ? selected : items.get(0));
        box.setPrefWidth(250);
        return box;
    }

    private static TextField input(String value) {
        TextField field = new TextField(value);
        field.setPrefWidth(250);
        return field;
    }

    private static <T> List<T> replacing(List<T> items, T existing, T updated) {
        List<T> result = new ArrayList<>(items);
        int index = existing == null ? -1 : result.indexOf(existing);
        if (index >= 0) {
            result.set(index, updated);
        } else {
            result.add(updated);
        }
        return result;
    }
}