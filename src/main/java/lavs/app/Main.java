package lavs.app;

import javafx.application.Application;

// entry point; kept separate from LavsApp so the app can be launched from a plain classpath
public final class Main {
    public static void main(String[] args) {
        Application.launch(LavsApp.class, args);
    }
}
