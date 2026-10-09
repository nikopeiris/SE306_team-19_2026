package lavs.ui;

import javafx.fxml.FXML;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.scene.control.ProgressIndicator;
import javafx.scene.control.TextField;
import javafx.scene.layout.Pane;
import lavs.app.AppContext;
import lavs.service.AuthService;

public class LoginController {
    private static final String UNREACHABLE = "Mainframe unreachable - check your network connection";

    private final AppContext _context;
    private final AuthService _authService;

    @FXML private Pane formPane;
    @FXML private TextField usernameField;
    @FXML private PasswordField passwordField;
    @FXML private Label errorLabel;
    @FXML private Label connectionLabel;
    @FXML private ProgressIndicator busyIndicator;

    public LoginController(AppContext context) {
        _context = context;
        _authService = context.getAuthService();
    }

    @FXML
    private void initialize() {
        errorLabel.setText("");
        busyIndicator.visibleProperty().bind(formPane.disabledProperty());
        checkConnection();
    }

    @FXML
    private void onLogin() {
        errorLabel.setText("");
        String username = usernameField.getText();
        String password = passwordField.getText();
        AsyncRunner.run(() -> {
                    _authService.login(username, password);
                    return null;
                },
                formPane,
                ignored -> _context.getRouter().showDashboard(),
                error -> {
                    errorLabel.setText(error.getMessage());
                    passwordField.clear();
                    passwordField.requestFocus();
                });
    }

    private void checkConnection() {
        connectionLabel.setText("Checking connection to mainframe...");
        AsyncRunner.run(_authService::isMainframeReachable, null,
                reachable -> connectionLabel.setText(reachable ? "Mainframe online" : UNREACHABLE),
                error -> connectionLabel.setText(UNREACHABLE));
    }
}
