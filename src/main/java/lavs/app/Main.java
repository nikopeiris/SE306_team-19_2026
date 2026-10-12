package lavs.app;

import java.net.URL;

import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.stage.Stage;

public class Main extends Application {

    @Override
    public void start(Stage primaryStage) {
        try {
            URL fxmlUrl = getClass().getResource("/lavs/ui/login.fxml");
            if (fxmlUrl == null) {
                throw new RuntimeException("Could not find /lavs/ui/login.fxml in resources!");
            }
            Parent root = FXMLLoader.load(fxmlUrl);

            // 1. Pass width and height into the Scene constructor (e.g., 1000x650)
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