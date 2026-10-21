package lavs.ui;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import business.system.customer.CustomerEmailService;
import business.system.dto.CustomerSummary;
import javafx.animation.PauseTransition;
import javafx.application.Platform;
import javafx.util.Duration;

/**
 * Primary emails (one request per customer, loaded in the background).
 * Rows on screen get their emails first; everything else loads behind them.
 */
final class EmailDirectory {

    // primary email per customer number ("" means the customer has no primary email)
    private final Map<String, String> cache = new ConcurrentHashMap<>();
    private final Set<String> failed = ConcurrentHashMap.newKeySet();
    private final Set<String> requestedByRow = ConcurrentHashMap.newKeySet();

    private final ExecutorService rowExecutor =
            Executors.newFixedThreadPool(2, UiTasks.daemonFactory("row-email"));
    private final ExecutorService prefetchExecutor =
            Executors.newFixedThreadPool(2, UiTasks.daemonFactory("prefetch-email"));

    // many emails arrive in a burst, so refresh the screen once things settle
    private final PauseTransition refreshDelay = new PauseTransition(Duration.millis(150));

    private volatile CustomerEmailService emailService;

    /** @param onSettled called on the FX thread after a burst of emails has arrived */
    EmailDirectory(Runnable onSettled) {
        refreshDelay.setOnFinished(e -> onSettled.run());
    }

    void setEmailService(CustomerEmailService emailService) {
        this.emailService = emailService;
    }

    /** The email if it is already known ("" = none), otherwise null. */
    String cachedEmail(String number) {
        return cache.get(number);
    }

    /** Loads every customer's email in the background so email search is complete. */
    void prefetch(List<CustomerSummary> customers) {
        for (CustomerSummary customer : customers) {
            String number = customer.getNumber();
            prefetchExecutor.submit(() -> fetchEmail(number));
        }
    }

    /** Text for the email column; asks for the email in the background if it isn't known yet. */
    String textFor(CustomerSummary customer) {
        String number = customer.getNumber();
        String cached = cache.get(number);
        if (cached != null) {
            return cached.isEmpty() ? "-" : cached;
        }
        if (failed.contains(number)) {
            return "Unavailable";
        }
        requestEmailForRow(number);
        return "Loading...";
    }

    /** Forgets everything so the next look-up fetches fresh data. */
    void clear() {
        cache.clear();
        failed.clear();
        requestedByRow.clear();
    }

    /** Stops any lookups still running when the user leaves the dashboard. */
    void shutdown() {
        rowExecutor.shutdownNow();
        prefetchExecutor.shutdownNow();
        refreshDelay.stop();
    }

    // called when a row appears on screen and its email isn't known yet
    private void requestEmailForRow(String number) {
        if (!rowExecutor.isShutdown() && requestedByRow.add(number)) {
            rowExecutor.submit(() -> fetchEmail(number));
        }
    }

    private void fetchEmail(String number) {
        if (cache.containsKey(number) || failed.contains(number)) {
            return;
        }
        try {
            String email = emailService.findPrimaryEmail(number);
            cache.put(number, email == null ? "" : email);
        } catch (Exception ex) {
            failed.add(number);
        }
        if (!rowExecutor.isShutdown()) {
            Platform.runLater(refreshDelay::playFromStart);
        }
    }
}
