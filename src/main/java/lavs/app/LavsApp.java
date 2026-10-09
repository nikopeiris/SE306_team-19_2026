package lavs.app;

import javafx.application.Application;
import javafx.stage.Stage;
import lavs.ui.AsyncRunner;
import lavs.ui.SceneRouter;
import mainframe.client.HttpConnection;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.Properties;

// the JavaFX application: wires up the services and shows the login screen
public class LavsApp extends Application {
    private AppContext _context;

    @Override
    public void start(Stage stage) {
        _context = new AppContext(new HttpConnection(loadBaseUrl()));
        SceneRouter router = new SceneRouter(stage, _context);
        _context.setRouter(router);

        stage.setTitle("LAVS - Loan Application and Verification System");
        stage.setMinWidth(900);
        stage.setMinHeight(600);
        router.showLogin();
        stage.show();
    }

    @Override
    public void stop() throws Exception {
        AsyncRunner.shutdown();
        if (_context != null) {
            _context.getConnection().close();
        }
    }

    private static String loadBaseUrl() {
        Properties properties = new Properties();
        try (InputStream input = LavsApp.class.getResourceAsStream("/lavs.properties")) {
            if (input != null) {
                properties.load(input);
            }
        } catch (IOException ex) {
            throw new UncheckedIOException(ex);
        }
        // allow overriding with -Dmainframe.baseUrl=... when running
        return System.getProperty("mainframe.baseUrl", properties.getProperty("mainframe.baseUrl"));
    }
}
