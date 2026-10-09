package lavs.ui;

import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.stage.Stage;
import lavs.app.AppContext;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.lang.reflect.InvocationTargetException;

// switches between the application's screens
public final class SceneRouter {
    private static final String STYLESHEET = "/lavs/ui/styles.css";

    private final Stage _stage;
    private final AppContext _context;

    public SceneRouter(Stage stage, AppContext context) {
        _stage = stage;
        _context = context;
    }

    public void showLogin() {
        show("login.fxml");
    }

    public void showDashboard() {
        show("dashboard.fxml");
    }

    private void show(String fxml) {
        FXMLLoader loader = new FXMLLoader(SceneRouter.class.getResource("/lavs/ui/" + fxml));
        // controllers receive the AppContext through their constructor
        loader.setControllerFactory(type -> {
            try {
                return type.getConstructor(AppContext.class).newInstance(_context);
            } catch (NoSuchMethodException | InstantiationException
                     | IllegalAccessException | InvocationTargetException ex) {
                throw new IllegalStateException("Cannot create controller " + type.getName(), ex);
            }
        });

        Parent root;
        try {
            root = loader.load();
        } catch (IOException ex) {
            throw new UncheckedIOException("Cannot load view " + fxml, ex);
        }

        if (_stage.getScene() == null) {
            Scene scene = new Scene(root, 1024, 700);
            scene.getStylesheets().add(SceneRouter.class.getResource(STYLESHEET).toExternalForm());
            _stage.setScene(scene);
        } else {
            _stage.getScene().setRoot(root);
        }
    }
}
