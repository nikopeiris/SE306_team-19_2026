package lavs.ui;

import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.scene.control.TableView;
import javafx.scene.layout.Pane;

public class LoanTabController {

    @FXML private TableView<Object> loansTable;
    @FXML private Pane addLoanPane;

    private CustomerContext context;

    public void init(CustomerContext context) {
        this.context = context;
    }

    @FXML
    private void handleAddLoan(ActionEvent event) {
        if (addLoanPane != null) {
            addLoanPane.setVisible(true);
            addLoanPane.setManaged(true);
        }
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
    private void handleSave(ActionEvent event) {
        // Handle final customer save sequence
    }
}