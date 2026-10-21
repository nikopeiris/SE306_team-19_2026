package lavs.ui;

import java.util.function.Function;

import business.system.dto.CustomerSummary;
import business.system.dto.LoanSummary;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;

/**
 * Recently opened customers and loans (pure screen history, no business rules).
 * Static so they survive the dashboard being rebuilt when the user comes back from a
 * customer screen; cleared on sign out.
 */
final class RecentItems {

    static final int MAX_RECENT = 8;

    static final ObservableList<CustomerSummary> CUSTOMERS = FXCollections.observableArrayList();
    static final ObservableList<LoanSummary> LOANS = FXCollections.observableArrayList();

    private RecentItems() { }

    static void rememberCustomer(CustomerSummary customer) {
        remember(CUSTOMERS, customer, CustomerSummary::getNumber);
    }

    static void rememberLoan(LoanSummary loan) {
        remember(LOANS, loan, LoanSummary::getLoanNumber);
    }

    static void clear() {
        CUSTOMERS.clear();
        LOANS.clear();
    }

    /** Moves the item to the top of the list, drops duplicates and keeps the list short. */
    private static <T> void remember(ObservableList<T> list, T item, Function<T, String> key) {
        String id = key.apply(item);
        list.removeIf(existing -> key.apply(existing).equals(id));
        list.add(0, item);
        while (list.size() > MAX_RECENT) {
            list.remove(list.size() - 1);
        }
    }
}
