package lavs.ui;

import java.util.function.Consumer;

import business.system.Customer;
import business.system.LoginService;
import business.system.customer.CustomerEmailService;
import business.system.dto.CustomerSummary;
import business.system.dto.LoanSummary;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.layout.Pane;

/** Opens the customer and loan-summary screens as overlays on top of the dashboard. */
final class DashboardOverlays {

    private final Pane root;
    private LoginService loginService;
    private Customer customerService;
    private CustomerEmailService emailService;

    DashboardOverlays(Pane root) {
        this.root = root;
    }

    void setServices(LoginService loginService, Customer customerService,
            CustomerEmailService emailService) {
        this.loginService = loginService;
        this.customerService = customerService;
        this.emailService = emailService;
    }

    /**
     * Shows the customer screen. A null number opens it empty, to add a new customer.
     *
     * @param onClosed called after the overlay was removed (Back / Save)
     * @param onSaved  called with the customer number after a save
     */
    void showCustomer(CustomerSummary customer, String customerNumber,
            Runnable onClosed, Consumer<String> onSaved) {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("customer.fxml"));
            Parent customerOverlay = loader.load();

            CustomerController controller = loader.getController();
            controller.setServices(loginService, customerService, emailService);

            if (customerNumber != null) {
                controller.loadCustomer(customerNumber);
                RecentItems.rememberCustomer(customer);
            }

            // Add a close mechanism so the overlay can remove itself when Back/Save is clicked
            controller.setOnCloseAction(() -> {
                root.getChildren().remove(customerOverlay);
                onClosed.run();
            });
            // After a save, show the new / renamed customer in the list straight away
            controller.setOnSavedAction(number -> onSaved.accept(number));

            // Overlap it on top of the dashboard root
            root.getChildren().add(customerOverlay);

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /**
     * Shows the loan summary screen.
     *
     * @param onChanged called if changes are saved, so the table and summary can refresh
     */
    void showLoanSummary(LoanSummary loan, Runnable onChanged) {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("loan_summary.fxml"));
            Parent loanOverlay = loader.load();

            LoanSummaryController controller = loader.getController();
            controller.start(customerService.loanDetails(), loan.getLoanNumber(), () -> onChanged.run());

            // Remove the overlay from the dashboard root when closed/back is clicked
            controller.setOnCloseAction(() -> root.getChildren().remove(loanOverlay));

            // Push the loan summary overlay on top of the dashboard root pane
            root.getChildren().add(loanOverlay);

        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
