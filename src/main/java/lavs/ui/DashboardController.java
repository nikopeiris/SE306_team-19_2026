package lavs.ui;

import java.net.URL;

import business.system.LoginService;
import javafx.concurrent.Task;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.stage.Stage;

public class DashboardController {

    @FXML private Button signOutButton;

    // Business layer: the UI never talks to the mainframe adapter directly
    private LoginService loginService;

    /** Called by LoginController when it opens the dashboard. */
    public void setLoginService(LoginService loginService) {
        this.loginService = loginService;
    }

    @FXML
    private void handleSignOut(ActionEvent event) {
        signOutButton.setDisable(true);

        // Run on a background thread in case logging out makes a network call
        Task<Void> logoutTask = new Task<>() {
            @Override
            protected Void call() {
                loginService.logout();
                return null;
            }
        };

        logoutTask.setOnSucceeded(e -> goToLogin());
        logoutTask.setOnFailed(e -> {
            // Even if the server couldn't be told, the user still wants to leave
            logoutTask.getException().printStackTrace();
            goToLogin();
        });

        Thread thread = new Thread(logoutTask, "logout-task");
        thread.setDaemon(true);
        thread.start();
    }

    private void goToLogin() {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/lavs/ui/login.fxml"));
            Parent root = loader.load();

            // give the fresh login screen the service so the user can log in again
            LoginController loginController = loader.getController();
            loginController.setLoginService(loginService);

            Scene scene = new Scene(root, 1200, 800);
            URL cssUrl = getClass().getResource("/lavs/ui/styles.css");
            if (cssUrl != null) {
                scene.getStylesheets().add(cssUrl.toExternalForm());
            }

            Stage stage = (Stage) signOutButton.getScene().getWindow();
            stage.setScene(scene);
            stage.setTitle("Mainframe Interface - Login");
            stage.show();
        } catch (Exception e) {
            e.printStackTrace();
            signOutButton.setDisable(false);
        }
    }
}