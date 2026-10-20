package lavs.ui;

import java.util.function.Consumer;

import business.system.dto.PaymentRow;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.scene.control.Label;

/** One row in the payments popup. */
public class PaymentRowController {
    @FXML private Label dateLabel;
    @FXML private Label amountLabel;
    @FXML private Label idLabel;

    private PaymentRow payment;
    private Consumer<PaymentRow> onEdit;
    private Consumer<PaymentRow> onDelete;

    public void setPayment(PaymentRow payment, Consumer<PaymentRow> onEdit,
            Consumer<PaymentRow> onDelete) {
        this.payment = payment;
        this.onEdit = onEdit;
        this.onDelete = onDelete;
        dateLabel.setText(LoanFormat.text(payment.date()));
        amountLabel.setText(LoanFormat.money(payment.amountPaid()));
        idLabel.setText("Payment #" + payment.id());
    }

    @FXML
    private void handleEdit(ActionEvent event) {
        onEdit.accept(payment);
    }

    @FXML
    private void handleDelete(ActionEvent event) {
        onDelete.accept(payment);
    }
}
