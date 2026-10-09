package lavs.ui;

import javafx.fxml.FXML;
import javafx.scene.control.Label;
import lavs.app.AppContext;

// placeholder home screen: customer search and drafts will be added here
public class DashboardController {
    private final AppContext _context;

    @FXML private Label userLabel;

    public DashboardController(AppContext context) {
        _context = context;
    }

    @FXML
    private void initialize() {
        userLabel.setText("Logged in as " + _context.getAuthService().getCurrentUser());
    }

    @FXML
    private void onLogout() {
        _context.getAuthService().logout();
        _context.getRouter().showLogin();
    }
}
