package lavs.app;

import java.net.URL;

import business.system.Customer;
import business.system.LoginService;
import business.system.customer.CustomerEmailService;
import dataaccess.systemadapters.MainframeAdapter;
import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.stage.Stage;
import lavs.ui.LoginController;

public class Main extends Application {

    @Override
    public void start(Stage primaryStage) {

        // set log in serve 
        MainframeAdapter mainframeAdapter = new MainframeAdapter("https://animating-eraser-arrogance.ngrok-free.dev/");
        LoginService loginService = new LoginService(mainframeAdapter);
        Customer customerService = new Customer(mainframeAdapter);
        CustomerEmailService emailService = new CustomerEmailService(mainframeAdapter);


        // ui layer
        try {
            URL fxmlUrl = getClass().getResource("/lavs/ui/login.fxml");
            if (fxmlUrl == null) {
                throw new RuntimeException("Could not find /lavs/ui/login.fxml in resources!");
            }
            FXMLLoader loader = new FXMLLoader(fxmlUrl);
            Parent root = loader.load();
            LoginController logIncontroller = loader.getController();
            logIncontroller.setLoginService(loginService);
            logIncontroller.setCustomerService(customerService);
            logIncontroller.setEmailService(emailService);

            // 1. Pass width and height into the Scene constructor (e.g., 1000x800)
            Scene scene = new Scene(root, 1200, 800);

            // 2. Load stylesheet safely
            URL cssUrl = getClass().getResource("/lavs/ui/styles.css");
            if (cssUrl != null) {
                scene.getStylesheets().add(cssUrl.toExternalForm());
            }


            primaryStage.setTitle("Mainframe Interface - Login");
            primaryStage.setScene(scene);

            primaryStage.setResizable(false);

            primaryStage.show();
            
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public static void main(String[] args) {
        launch(args);
    }
}