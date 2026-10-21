package lavs.ui;

import java.io.IOException;
import java.util.concurrent.ThreadFactory;

import business.lavsexceptions.LavsException;
import business.validation.ValidationException;
import javafx.concurrent.Task;

/** Small helpers shared by the dashboard components: background threads and error text. */
final class UiTasks {

    private UiTasks() { }

    static void startDaemon(Task<?> task, String name) {
        Thread thread = new Thread(task, name);
        thread.setDaemon(true);
        thread.start();
    }

    static ThreadFactory daemonFactory(String name) {
        return runnable -> {
            Thread thread = new Thread(runnable, name);
            thread.setDaemon(true);
            return thread;
        };
    }

    /** Turns an exception into a message that is safe to show on screen. */
    static String describe(Throwable error) {
        if (error instanceof ValidationException || error instanceof LavsException) {
            return error.getMessage();
        } else if (error instanceof IOException) {
            return "Could not connect to the server.";
        }
        error.printStackTrace();
        return "Something went wrong loading data.";
    }
}
