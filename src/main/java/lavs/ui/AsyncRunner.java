package lavs.ui;

import javafx.concurrent.Task;
import javafx.scene.Node;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Consumer;

// runs slow work (e.g. mainframe calls) off the JavaFX thread so the UI stays responsive
public final class AsyncRunner {
    private static final ExecutorService EXECUTOR = Executors.newCachedThreadPool(runnable -> {
        Thread thread = new Thread(runnable, "lavs-worker");
        thread.setDaemon(true);
        return thread;
    });

    // work that runs in the background and may throw
    @FunctionalInterface
    public interface Work<T> {
        T run() throws Exception;
    }

    private AsyncRunner() {
    }

    // runs work in the background; busyNode is disabled while it runs (may be null).
    // onSuccess and onError are called back on the JavaFX thread.
    public static <T> void run(Work<T> work, Node busyNode, Consumer<T> onSuccess, Consumer<Throwable> onError) {
        Task<T> task = new Task<>() {
            @Override
            protected T call() throws Exception {
                return work.run();
            }
        };
        if (busyNode != null) {
            busyNode.disableProperty().bind(task.runningProperty());
        }
        task.setOnSucceeded(event -> onSuccess.accept(task.getValue()));
        task.setOnFailed(event -> onError.accept(task.getException()));
        EXECUTOR.submit(task);
    }

    public static void shutdown() {
        EXECUTOR.shutdownNow();
    }
}
