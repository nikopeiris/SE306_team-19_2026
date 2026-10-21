package lavs.ui;

import java.util.function.Consumer;

import business.system.dto.CustomerSummary;
import business.system.dto.LoanSummary;
import javafx.scene.control.Label;
import javafx.scene.control.ListView;

/** The bottom strip: the Recent Customers and Recent Loans lists. */
final class RecentListsPanel {

    private static final double ROW_HEIGHT = 40.0;

    RecentListsPanel(ListView<CustomerSummary> customersList, ListView<LoanSummary> loansList,
            Consumer<CustomerSummary> onCustomerClicked, Consumer<LoanSummary> onLoanClicked) {

        customersList.setItems(RecentItems.CUSTOMERS);
        customersList.setFixedCellSize(ROW_HEIGHT);
        customersList.setPlaceholder(emptyNote("No recent customers yet"));
        customersList.setCellFactory(lv -> new RecentCell<CustomerSummary>(
                CustomerSummary::getFullName,
                CustomerSummary::getNumber,
                onCustomerClicked));

        loansList.setItems(RecentItems.LOANS);
        loansList.setFixedCellSize(ROW_HEIGHT);
        loansList.setPlaceholder(emptyNote("No recent loans yet"));
        loansList.setCellFactory(lv -> new RecentCell<LoanSummary>(
                LoanSummary::getLoanNumber,
                loan -> loan.getCustomerName() + "  |  " + loan.getStatus(),
                onLoanClicked));
    }

    private static Label emptyNote(String text) {
        Label note = new Label(text);
        note.getStyleClass().add("empty-note");
        return note;
    }
}
