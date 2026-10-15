package lavs.ui;

import java.io.IOException;

import business.lavsexceptions.LavsException;
import business.system.LoginService;
import business.validation.ValidationException;
import javafx.animation.Interpolator;
import javafx.animation.KeyFrame;
import javafx.animation.KeyValue;
import javafx.animation.ScaleTransition;
import javafx.animation.Timeline;
import javafx.concurrent.Task;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.geometry.Rectangle2D;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.scene.control.TextField;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Pane;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.shape.Circle;
import javafx.scene.shape.Rectangle;
import javafx.scene.shape.SVGPath;
import javafx.scene.text.Text;
import javafx.stage.Stage;
import javafx.util.Duration;

public class LoginController {

    // ---- Layout containers ----
    @FXML private Pane rootPane;
    @FXML private StackPane leftPane;
    @FXML private Pane rightPane;
    @FXML private VBox card;
    @FXML private VBox usernameSection;
    @FXML private VBox lowerSection;
    @FXML private StackPane passwordStack;

    // ---- Left side controls ----
    @FXML private Button logoButton;
    @FXML private Pane logoContent;
    @FXML private Rectangle logoBackground;
    @FXML private SVGPath logoSwoosh;
    @FXML private HBox logoWordmark;
    @FXML private Text logoTitleStart;
    @FXML private StackPane logoI;
    @FXML private Text logoIStem;
    @FXML private Circle logoDot;
    @FXML private Text logoTitleEnd;
    @FXML private Text usernameLabel;
    @FXML private TextField usernameField;
    @FXML private Text passwordLabel;
    @FXML private PasswordField passwordField;        // masked
    @FXML private TextField passwordTextField;        // plain text (shown when "Show" is on)
    @FXML private Button togglePasswordButton;
    @FXML private Label errorLabel;
    @FXML private Button loginButton;

    // ---- Right side ----
    @FXML private ImageView loginImage;
    @FXML private Text quoteOpenMark;
    @FXML private Text quoteText;
    @FXML private Text quoteAuthor;
    @FXML private Text quoteCloseMark;

    // ---- Styles (match the FXML, with a red border variant for errors) ----
    private static final String USERNAME_STYLE =
            "-fx-background-color: white; -fx-border-color: blue;";
    private static final String USERNAME_ERROR_STYLE =
            "-fx-background-color: white; -fx-border-color: #cc0000; -fx-border-width: 2;";
    private static final String PASSWORD_STYLE =
            "-fx-background-color: #F8fff4; -fx-border-color: blue; -fx-padding: 0 60 0 8;";
    private static final String PASSWORD_ERROR_STYLE =
            "-fx-background-color: #F8fff4; -fx-border-color: #cc0000; -fx-border-width: 2; -fx-padding: 0 60 0 8;";

    // Business layer: ALL validation and authentication happens in here, not in the UI
    private LoginService loginService;

    private Timeline animation;
    private boolean expanded = false;

    @FXML
    private void initialize() {
        setupImage();
        setupPasswordReveal();
        setupPasswordVisibility();
        setupErrorClearing();
        setupKeyboardShortcuts();
        setupLogoHover();
    }

    /** Called by whoever loads this screen to give it the business-layer service. */
    public void setLoginService(LoginService loginService) {
        this.loginService = loginService;
    }

    // ------------------------------------------------------------------
    // Setup
    // ------------------------------------------------------------------

    // load in image
    private void setupImage() {
        Image image = loginImage.getImage();
        double targetRatio = 336.0 / 308.0;
        double imgW = image.getWidth();
        double imgH = image.getHeight();

        double cropW, cropH;
        if (imgW / imgH > targetRatio) {
            cropH = imgH;
            cropW = imgH * targetRatio;
        } else {
            cropW = imgW;
            cropH = imgW / targetRatio;
        }

        loginImage.setViewport(new Rectangle2D(
                (imgW - cropW) / 2,
                (imgH - cropH) / 2,
                cropW, cropH
        ));
    }

    // password section stays hidden until the user starts typing a username
    private void setupPasswordReveal() {
        // Clip the lower section so its content is hidden while it is collapsed
        Rectangle clip = new Rectangle();
        clip.widthProperty().bind(lowerSection.widthProperty());
        clip.heightProperty().bind(lowerSection.heightProperty());
        lowerSection.setClip(clip);

        lowerSection.setTranslateY(-12);

        usernameField.textProperty().addListener((obs, oldText, newText) -> {
            boolean hasText = !newText.trim().isEmpty();
            if (hasText != expanded) {
                setExpanded(hasText);
            }
        });
    }

    // masked and plain-text fields always hold the same text
    private void setupPasswordVisibility() {
        passwordTextField.textProperty().bindBidirectional(passwordField.textProperty());
    }

    // typing again removes the error message and red borders
    private void setupErrorClearing() {
        usernameField.textProperty().addListener((obs, o, n) -> clearError());
        passwordField.textProperty().addListener((obs, o, n) -> clearError());
    }

    // Enter in the password box logs in
    private void setupKeyboardShortcuts() {
        passwordField.setOnAction(e -> loginButton.fire());
        passwordTextField.setOnAction(e -> loginButton.fire());
    }

    // logo grows slightly when the mouse is over it
    private void setupLogoHover() {
        logoButton.setOnMouseEntered(e -> scaleLogo(1.05));
        logoButton.setOnMouseExited(e -> scaleLogo(1.0));
    }

    private void scaleLogo(double target) {
        ScaleTransition st = new ScaleTransition(Duration.millis(150), logoContent);
        st.setToX(target);
        st.setToY(target);
        st.play();
    }

    // ------------------------------------------------------------------
    // Animation
    // ------------------------------------------------------------------

    private void setExpanded(boolean show) {
        expanded = show;
        if (animation != null) {
            animation.stop();
        }

        // Full height of the section, calculated from its contents
        double targetHeight = lowerSection.prefHeight(-1);

        Duration duration = Duration.millis(350);
        Interpolator ease = Interpolator.SPLINE(0.25, 0.1, 0.25, 1.0);

        animation = new Timeline(
                new KeyFrame(duration,
                        new KeyValue(lowerSection.maxHeightProperty(), show ? targetHeight : 0, ease),
                        new KeyValue(lowerSection.opacityProperty(), show ? 1.0 : 0.0, ease),
                        new KeyValue(lowerSection.translateYProperty(), show ? 0 : -12, ease)
                )
        );
        animation.play();
    }

    // little left-right shake of the card for a wrong login
    private void shakeCard() {
        Timeline shake = new Timeline(
                new KeyFrame(Duration.millis(0), new KeyValue(card.translateXProperty(), 0)),
                new KeyFrame(Duration.millis(60), new KeyValue(card.translateXProperty(), -10)),
                new KeyFrame(Duration.millis(120), new KeyValue(card.translateXProperty(), 10)),
                new KeyFrame(Duration.millis(180), new KeyValue(card.translateXProperty(), -8)),
                new KeyFrame(Duration.millis(240), new KeyValue(card.translateXProperty(), 8)),
                new KeyFrame(Duration.millis(300), new KeyValue(card.translateXProperty(), 0))
        );
        shake.play();
    }

    // ------------------------------------------------------------------
    // Button handlers
    // ------------------------------------------------------------------

    // Show / Hide the password
    @FXML
    private void handleTogglePassword(ActionEvent event) {
        boolean show = !passwordTextField.isVisible();

        passwordTextField.setVisible(show);
        passwordTextField.setManaged(show);
        passwordField.setVisible(!show);
        passwordField.setManaged(!show);
        togglePasswordButton.setText(show ? "Hide" : "Show");

        // keep the cursor in whichever field is now showing
        TextField active = show ? passwordTextField : passwordField;
        active.requestFocus();
        active.positionCaret(active.getText().length());
    }

    // Clicking the logo resets the form back to its starting state
    @FXML
    private void handleLogoButton(ActionEvent event) {
        usernameField.clear();   // also collapses the password section
        passwordField.clear();
        clearError();

        // go back to masked password
        if (passwordTextField.isVisible()) {
            handleTogglePassword(event);
        }
        usernameField.requestFocus();
    }

    @FXML
    private void handleLoginButton(ActionEvent event) {
        // The UI only passes the raw input on; the business layer validates and authenticates.
        String username = usernameField.getText();
        String password = passwordField.getText();

        setLoggingIn(true);

        // Run on a background thread so a slow network call doesn't freeze the window
        Task<Boolean> loginTask = new Task<>() {
            @Override
            protected Boolean call() throws Exception {
                return loginService.attemptLogin(username, password);
            }
        };

        loginTask.setOnSucceeded(e -> {
            setLoggingIn(false);
            goToDashboard(event);
        });

        loginTask.setOnFailed(e -> {
            setLoggingIn(false);
            handleLoginFailure(loginTask.getException());
        });

        Thread thread = new Thread(loginTask, "login-task");
        thread.setDaemon(true);
        thread.start();
    }

    // ------------------------------------------------------------------
    // Login result handling (no checking here, just showing what the business layer reported)
    // ------------------------------------------------------------------

    private void handleLoginFailure(Throwable error) {
        if (error instanceof ValidationException) {
            // Input was rejected by the business layer's validation (empty, too short, ...)
            showError(error.getMessage(), true, true);
        } else if (error instanceof LavsException) {
            // The mainframe rejected the login (wrong username or password, etc.)
            passwordField.clear();   // clear first: clearing removes any error shown
            showError(error.getMessage(), true, true);
        } else if (error instanceof IOException) {
            // Network / mainframe connection problem
            showError("Could not connect to the server. Please try again.", false, false);
        } else {
            error.printStackTrace();
            showError("Something went wrong. Please try again.", false, false);
        }
    }

    // disables the button while waiting for the business layer
    private void setLoggingIn(boolean loggingIn) {
        loginButton.setDisable(loggingIn);
        loginButton.setText(loggingIn ? "Logging in..." : "Log In");
    }

    private void goToDashboard(ActionEvent event) {
        try {
            // Load the separate dashboard FXML
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/lavs/ui/dashboard.fxml"));
            Parent root = loader.load();
            Stage stage = (Stage) ((Node) event.getSource()).getScene().getWindow();
            stage.setScene(new Scene(root));
            stage.setTitle("Mainframe Interface - Dashboard");
            stage.show();
        } catch (Exception e) {
            e.printStackTrace();
            showError("Could not open the dashboard.", false, false);
        }
    }

    // ------------------------------------------------------------------
    // Error display
    // ------------------------------------------------------------------

    private void showError(String message, boolean markUsername, boolean markPassword) {
        errorLabel.setText(message);
        if (markUsername) {
            usernameField.setStyle(USERNAME_ERROR_STYLE);
        }
        if (markPassword) {
            passwordField.setStyle(PASSWORD_ERROR_STYLE);
            passwordTextField.setStyle(PASSWORD_ERROR_STYLE);
        }
        shakeCard();
        (passwordTextField.isVisible() ? passwordTextField : passwordField).requestFocus();
    }

    private void clearError() {
        errorLabel.setText("");
        usernameField.setStyle(USERNAME_STYLE);
        passwordField.setStyle(PASSWORD_STYLE);
        passwordTextField.setStyle(PASSWORD_STYLE);
    }
}