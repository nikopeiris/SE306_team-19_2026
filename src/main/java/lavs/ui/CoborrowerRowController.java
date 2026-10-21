package lavs.ui;

import java.util.function.Consumer;

import business.system.dto.CoborrowerRow;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.scene.control.Label;

/** One row in the co-borrowers popup. */
public class CoborrowerRowController {
    @FXML private Label nameLabel;
    @FXML private Label numberLabel;
    @FXML private Label typeLabel;

    private CoborrowerRow coborrower;
    private Consumer<CoborrowerRow> onEdit;
    private Consumer<CoborrowerRow> onDelete;

    public void setCoborrower(CoborrowerRow coborrower, Consumer<CoborrowerRow> onEdit,
            Consumer<CoborrowerRow> onDelete) {
        this.coborrower = coborrower;
        this.onEdit = onEdit;
        this.onDelete = onDelete;
        nameLabel.setText(LoanFormat.text(coborrower.name()));
        numberLabel.setText("Customer " + LoanFormat.text(coborrower.customerNumber()));
        typeLabel.setText(LoanFormat.text(coborrower.type()));
    }

    @FXML
    private void handleEdit(ActionEvent event) {
        onEdit.accept(coborrower);
    }

    @FXML
    private void handleDelete(ActionEvent event) {
        onDelete.accept(coborrower);
    }
}
