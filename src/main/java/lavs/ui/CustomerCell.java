package lavs.ui;

import java.util.function.Consumer;
import java.util.function.Function;

import business.system.dto.CustomerSummary;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Cursor;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;

/** One row in the customer list: name, customer number and primary email. */
class CustomerCell extends ListCell<CustomerSummary> {

    // the header labels in dashboard.fxml use the same widths
    static final double ROW_HEIGHT = 40.0;
    private static final double NAME_WIDTH = 140.0;
    private static final double ID_WIDTH = 95.0;

    private final HBox row = new HBox(10);
    private final Label nameLabel = new Label();
    private final Label idLabel = new Label();
    private final Label emailLabel = new Label();
    private final Function<CustomerSummary, String> emailText;

    CustomerCell(Function<CustomerSummary, String> emailText, Consumer<CustomerSummary> onSelected) {
        this.emailText = emailText;

        nameLabel.setMinWidth(NAME_WIDTH);
        nameLabel.setPrefWidth(NAME_WIDTH);
        nameLabel.setMaxWidth(NAME_WIDTH);
        idLabel.setMinWidth(ID_WIDTH);
        idLabel.setPrefWidth(ID_WIDTH);
        idLabel.setMaxWidth(ID_WIDTH);
        emailLabel.setMaxWidth(Double.MAX_VALUE);
        HBox.setHgrow(emailLabel, Priority.ALWAYS);

        nameLabel.getStyleClass().add("cell-primary");
        idLabel.getStyleClass().add("cell-body");
        emailLabel.getStyleClass().add("cell-secondary");

        row.setAlignment(Pos.CENTER_LEFT);
        row.setPadding(new Insets(0, 10, 0, 10));
        row.setPrefHeight(ROW_HEIGHT);
        row.getChildren().addAll(nameLabel, idLabel, emailLabel);

        setOnMouseClicked(e -> {
            CustomerSummary customer = getItem();
            if (!isEmpty() && customer != null) {
                getListView().getSelectionModel().clearSelection();
                onSelected.accept(customer);
            }
        });
    }

    @Override
    protected void updateItem(CustomerSummary customer, boolean empty) {
        super.updateItem(customer, empty);
        if (empty || customer == null) {
            setText(null);
            setGraphic(null);
            setCursor(Cursor.DEFAULT);
            return;
        }
        nameLabel.setText(customer.getFullName());
        idLabel.setText(customer.getNumber());
        emailLabel.setText(emailText.apply(customer));
        setText(null);
        setGraphic(row);
        setCursor(Cursor.HAND);
    }
}
