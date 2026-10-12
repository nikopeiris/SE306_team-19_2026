package lavs.app;

import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.stage.Stage;

public class Main extends Application {

    @Override
    public void start(Stage primaryStage) {
        try {
            // Updated path to match src/main/resources/lavs/ui/
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/lavs/ui/login.fxml"));
            Parent root = loader.load();

            Scene scene = new Scene(root);
            
            // Updated stylesheet path
            String css = getClass().getResource("/lavs/ui/styles.css").toExternalForm();
            if (css != null) {
                scene.getStylesheets().add(css);
            }

            primaryStage.setTitle("Mainframe Interface - Login");
            primaryStage.setScene(scene);
            primaryStage.show();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public static void main(String[] args) {
        launch(args);
    }
}