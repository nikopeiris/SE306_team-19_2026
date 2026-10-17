package lavs.ui;

import business.system.customer.CustomerAddressService;
import business.system.customer.CustomerEmailService;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.TextField;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.text.Text;
import javafx.stage.Modality;
import javafx.stage.Stage;

public class ContactInfoController {

    @FXML private VBox addressListContainer;
    @FXML private VBox phoneNumberListContainer;
    @FXML private VBox emailListContainer;

    private CustomerContext context;
    private boolean hasPrimaryAddress = false;
    private boolean hasMailingAddress = false;
    private boolean hasPrimaryPhone = false;
    private boolean hasPrimaryEmail = false;

    public void init(CustomerContext context) {
        this.context = context;
    }

    @FXML
    private void handleAddAddress(ActionEvent event) {
        Stage popup = new Stage();
        popup.initModality(Modality.APPLICATION_MODAL);
        popup.setTitle("Add Customer Address");

        ComboBox<String> typeBox = new ComboBox<>();
        typeBox.getItems().addAll("Residential", "Postal");
        typeBox.setValue("Residential");

        TextField line1Field = new TextField(); line1Field.setPromptText("Line 1");
        TextField line2Field = new TextField(); line2Field.setPromptText("Line 2 (Optional)");
        TextField suburbField = new TextField(); suburbField.setPromptText("Suburb");
        TextField cityField = new TextField(); cityField.setPromptText("City");
        TextField countryField = new TextField(); countryField.setPromptText("Country");
        TextField postCodeField = new TextField(); postCodeField.setPromptText("Postcode");

        CheckBox primaryCheckBox = new CheckBox("Is Primary Address");
        CheckBox mailingCheckBox = new CheckBox("Is Mailing Address");
        Button saveButton = new Button("Save Address");
        Button cancelButton = new Button("Cancel");

        saveButton.setOnAction(e -> {
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

                if (isPrimary && hasPrimaryAddress) throw new IllegalArgumentException("Only one address can be set as Primary.");
                if (isMailing && hasMailingAddress) throw new IllegalArgumentException("Only one address can be set as Mailing.");

                CustomerAddressService.validateAddressDetails(city, country, "TEMP", l1, null, postCode, suburb, type);

                // Updated to reference CustomerContext instead of CustomerController
                CustomerContext.AddressDto newAddress = new CustomerContext.AddressDto(type, l1, l2, suburb, city, country, postCode, isPrimary, isMailing);
                context.getSavedAddresses().add(newAddress);

                if (isPrimary) hasPrimaryAddress = true;
                if (isMailing) hasMailingAddress = true;

                if (addressListContainer != null) {
                    HBox row = new HBox(12);
                    row.setAlignment(Pos.CENTER_LEFT);
                    String tags = (isPrimary ? " [PRIMARY]" : "") + (isMailing ? " [MAILING]" : "");
                    Text text = new Text(String.format("[%s]%s - %s, %s, %s", type, tags, l1, city, country));
                    Button removeBtn = new Button("Remove");
                    removeBtn.setTextFill(Color.RED);

                    removeBtn.setOnAction(rem -> {
                        if (newAddress.isPrimary()) hasPrimaryAddress = false;
                        if (newAddress.isMailing()) hasMailingAddress = false;
                        context.getSavedAddresses().remove(newAddress);
                        addressListContainer.getChildren().remove(row);
                    });

                    row.getChildren().addAll(text, removeBtn);
                    addressListContainer.getChildren().add(row);
                }
                popup.close();
            } catch (Exception ex) {
                showAlert("Address Error", ex.getMessage());
            }
        });

        cancelButton.setOnAction(e -> popup.close());
        VBox layout = new VBox(10, new Text("Enter Address:"), typeBox, line1Field, line2Field, suburbField, cityField, countryField, postCodeField, primaryCheckBox, mailingCheckBox, new HBox(10, saveButton, cancelButton));
        layout.setPadding(new Insets(20));
        popup.setScene(new Scene(layout, 350, 520));
        popup.showAndWait();
    }

    @FXML
    private void handleAddNumber(ActionEvent event) {
        Stage popup = new Stage();
        popup.initModality(Modality.APPLICATION_MODAL);
        popup.setTitle("Add Phone Number");

        ComboBox<String> typeBox = new ComboBox<>();
        typeBox.getItems().addAll("Mobile", "Home", "Work");
        typeBox.setValue("Mobile");

        ComboBox<String> countryCodeBox = new ComboBox<>();
        countryCodeBox.getItems().addAll("+64 (NZ)", "+61 (AU)", "+1 (US/CA)", "+44 (UK)");
        countryCodeBox.setValue("+64 (NZ)");
        countryCodeBox.setEditable(true);

        TextField phoneField = new TextField();
        phoneField.setPromptText("Phone Number");
        CheckBox primaryCheckBox = new CheckBox("Is Primary Phone");
        Button saveButton = new Button("Save Phone");
        Button cancelButton = new Button("Cancel");

        saveButton.setOnAction(e -> {
            try {
                String type = typeBox.getValue();
                String code = countryCodeBox.getValue();
                String num = phoneField.getText().trim();
                boolean isPrimary = primaryCheckBox.isSelected();

                if (num.isBlank()) throw new IllegalArgumentException("Phone number cannot be empty.");
                if (isPrimary && hasPrimaryPhone) throw new IllegalArgumentException("Only one primary phone allowed.");

                // Updated to reference CustomerContext instead of CustomerController
                CustomerContext.PhoneDto newPhone = new CustomerContext.PhoneDto(type, code, num, isPrimary);
                context.getSavedPhones().add(newPhone);
                if (isPrimary) hasPrimaryPhone = true;

                if (phoneNumberListContainer != null) {
                    HBox row = new HBox(12);
                    row.setAlignment(Pos.CENTER_LEFT);
                    Text text = new Text(String.format("[%s]%s %s %s", type, (isPrimary ? " [PRIMARY]" : ""), code, num));
                    Button removeBtn = new Button("Remove");
                    removeBtn.setTextFill(Color.RED);

                    removeBtn.setOnAction(rem -> {
                        if (newPhone.isPrimary()) hasPrimaryPhone = false;
                        context.getSavedPhones().remove(newPhone);
                        phoneNumberListContainer.getChildren().remove(row);
                    });
                    row.getChildren().addAll(text, removeBtn);
                    phoneNumberListContainer.getChildren().add(row);
                }
                popup.close();
            } catch (Exception ex) {
                showAlert("Phone Error", ex.getMessage());
            }
        });

        cancelButton.setOnAction(e -> popup.close());
        VBox layout = new VBox(10, new Text("Enter Phone:"), typeBox, countryCodeBox, phoneField, primaryCheckBox, new HBox(10, saveButton, cancelButton));
        layout.setPadding(new Insets(20));
        popup.setScene(new Scene(layout, 340, 260));
        popup.showAndWait();
    }

    @FXML
    private void handleAddEmail(ActionEvent event) {
        Stage popup = new Stage();
        popup.initModality(Modality.APPLICATION_MODAL);
        popup.setTitle("Add Email");

        ComboBox<String> typeBox = new ComboBox<>();
        typeBox.getItems().addAll("Personal", "Work");
        typeBox.setValue("Personal");

        TextField emailField = new TextField();
        emailField.setPromptText("email@example.com");
        CheckBox primaryCheckBox = new CheckBox("Is Primary Email");
        Button saveButton = new Button("Save Email");
        Button cancelButton = new Button("Cancel");

        saveButton.setOnAction(e -> {
            try {
                String type = typeBox.getValue();
                String email = emailField.getText().trim();
                boolean isPrimary = primaryCheckBox.isSelected();

                CustomerEmailService.validateEmailDetails(email, "TEMP", null, type);
                if (isPrimary && hasPrimaryEmail) throw new IllegalArgumentException("Only one primary email allowed.");

                // Updated to reference CustomerContext instead of CustomerController
                CustomerContext.EmailDto newEmail = new CustomerContext.EmailDto(type, email, isPrimary);
                context.getSavedEmails().add(newEmail);
                if (isPrimary) hasPrimaryEmail = true;

                if (emailListContainer != null) {
                    HBox row = new HBox(12);
                    row.setAlignment(Pos.CENTER_LEFT);
                    Text text = new Text(String.format("[%s]%s %s", type, (isPrimary ? " [PRIMARY]" : ""), email));
                    Button removeBtn = new Button("Remove");
                    removeBtn.setTextFill(Color.RED);

                    removeBtn.setOnAction(rem -> {
                        if (newEmail.isPrimary()) hasPrimaryEmail = false;
                        context.getSavedEmails().remove(newEmail);
                        emailListContainer.getChildren().remove(row);
                    });
                    row.getChildren().addAll(text, removeBtn);
                    emailListContainer.getChildren().add(row);
                }
                popup.close();
            } catch (Exception ex) {
                showAlert("Email Error", ex.getMessage());
            }
        });

        cancelButton.setOnAction(e -> popup.close());
        VBox layout = new VBox(10, new Text("Enter Email:"), typeBox, emailField, primaryCheckBox, new HBox(10, saveButton, cancelButton));
        layout.setPadding(new Insets(20));
        popup.setScene(new Scene(layout, 320, 240));
        popup.showAndWait();
    }

    private void showAlert(String title, String message) {
        Alert alert = new Alert(Alert.AlertType.WARNING);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.showAndWait();
    }
}