package lavs.ui;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;

import business.system.Customer;
import business.system.dto.CustomerSummary;
import business.system.dto.LoanListResult;
import business.system.dto.LoanSummary;
import javafx.application.Platform;
import javafx.concurrent.Task;

/**
 * Owns the loan data and everything about getting it: the full load in the background,
 * reading individual customers' loans (found by search, saved, or retried), and the
 * automatic retries for customers the mainframe refused. It knows nothing about the
 * widgets; it tells the {@link Listener} when the screen should redraw.
 */
final class LoanLoader {

    /** How the loader asks the screen to update itself (all calls are on the FX thread). */
    interface Listener {
        /** A full reload is starting: clear the visible list and reset the labels. */
        void loadReset();

        /** The loans changed: re-apply the filters and refresh the card. */
        void loansChanged();

        /** The set of statuses may have changed: refresh the status filter. */
        void statusesChanged();

        /** Only the status line (loading / retrying progress) changed. */
        void statusLabelChanged();
    }

    private static final int MAX_RETRY_ROUNDS = 4;
    private static final long RETRY_DELAY_MS = 2000L;

    private final Listener listener;
    private Customer customerService;

    /** Numbers of the customers whose loans are in the loan list. */
    private final Set<String> loansLoaded = new HashSet<>();
    /** Customers whose loans the mainframe refused; they are retried automatically. */
    private final Map<String, CustomerSummary> retryPending = new LinkedHashMap<>();
    private boolean retryRunning = false;
    private int retryRound = 0;

    private List<LoanSummary> allLoans = new ArrayList<>();
    private boolean loading = false;
    private String loadError = null;
    private int loansDone = 0;
    private int loansTotal = 0;
    private int failedCustomers = 0;
    private Task<LoanListResult> loanTask = null;
    /** Bumped whenever a load starts or is abandoned, so late results from an old load are ignored. */
    private volatile int loadGeneration = 0;

    LoanLoader(Listener listener) {
        this.listener = listener;
    }

    void setService(Customer customerService) {
        this.customerService = customerService;
    }

    // ---- What the screen needs to know ----

    /** Read-only view of every loan loaded so far. */
    List<LoanSummary> loans() { return Collections.unmodifiableList(allLoans); }
    boolean isLoading() { return loading; }
    String loadError() { return loadError; }
    int loansDone() { return loansDone; }
    int loansTotal() { return loansTotal; }
    int failedCustomers() { return failedCustomers; }
    int loadedCustomerCount() { return loansLoaded.size(); }
    boolean isRetrying() { return retryRunning; }

    /** Swaps one loan for its updated version (e.g. after a status change). */
    void replaceLoan(LoanSummary old, LoanSummary updated) {
        int index = allLoans.indexOf(old);
        if (index >= 0) {
            allLoans.set(index, updated);
        }
    }

    // ---- Loading every customer's loans ----

    void loadAll(List<CustomerSummary> source) {
        stop();
        final int generation = loadGeneration;
        List<CustomerSummary> customers = new ArrayList<>(source);   // snapshot on the FX thread
        final List<CustomerSummary> failedOut = new CopyOnWriteArrayList<>();

        loading = true;
        loadError = null;
        loansDone = 0;
        loansTotal = customers.size();
        failedCustomers = 0;
        allLoans = new ArrayList<>();
        loansLoaded.clear();
        retryPending.clear();
        retryRunning = false;
        listener.loadReset();

        Task<LoanListResult> task = new Task<>() {
            @Override
            protected LoanListResult call() throws Exception {
                // each customer's loans are handed to the screen as soon as they are read
                return customerService.listLoansFor(customers, (newLoans, done, total) -> {
                    Platform.runLater(() -> {
                        if (generation != loadGeneration) {
                            return;
                        }
                        allLoans.addAll(newLoans);
                        loansDone = done;
                        listener.loansChanged();
                    });
                    return generation == loadGeneration;   // false = stop early
                }, failedOut);
            }
        };

        task.setOnSucceeded(e -> {
            if (generation != loadGeneration) {
                return;
            }
            LoanListResult result = task.getValue();
            loading = false;
            failedCustomers = result.getFailedCustomers();
            if (failedCustomers > 0) {
                System.err.println("Loan load: " + failedCustomers + " of " + customers.size()
                        + " customers could not be read. First error: " + result.getFirstError());
                if (result.getLoans().isEmpty()) {
                    loadError = result.getFirstError();
                }
            }
            for (CustomerSummary c : customers) {
                loansLoaded.add(c.getNumber());
            }
            for (CustomerSummary c : failedOut) {
                loansLoaded.remove(c.getNumber());
                retryPending.put(c.getNumber(), c);
            }
            failedCustomers = retryPending.size();
            listener.statusesChanged();
            listener.loansChanged();
            startRetry();   // try the customers the mainframe refused again
        });

        task.setOnFailed(e -> {
            if (generation != loadGeneration) {
                return;
            }
            loading = false;
            loadError = UiTasks.describe(task.getException());
            listener.statusesChanged();
            listener.loansChanged();
        });

        loanTask = task;
        UiTasks.startDaemon(task, "load-loans");
    }

    /** Abandons any loan load still running (results from it are ignored from now on). */
    void stop() {
        loadGeneration++;
        if (loanTask != null) {
            loanTask.cancel(true);
            loanTask = null;
        }
        loading = false;
        retryRunning = false;
    }

    // ---- Loans of individual customers (found by search, saved, or retried) ----

    /** What one background round of reading customers' loans produced. */
    private static class LoanFetch {
        final List<LoanSummary> loans = new ArrayList<>();
        final List<CustomerSummary> loaded = new ArrayList<>();
        final List<CustomerSummary> failed = new ArrayList<>();
    }

    /**
     * Reads the loans of these customers and adds them to the loan list.
     *
     * @param force false skips customers whose loans are already loaded; true reads them
     *              again (used after a save, because loans may have changed)
     */
    void fetchFor(List<CustomerSummary> customers, boolean force) {
        if (customerService == null) {
            return;
        }
        List<CustomerSummary> todo = new ArrayList<>();
        for (CustomerSummary c : customers) {
            if (force || !loansLoaded.contains(c.getNumber())) {
                todo.add(c);
            }
        }
        if (todo.isEmpty()) {
            return;
        }
        runFetch(todo, 0L, () -> { });
    }

    private void runFetch(List<CustomerSummary> todo, long delayMs, Runnable afterApplied) {
        final int generation = loadGeneration;
        Task<LoanFetch> task = new Task<>() {
            @Override
            protected LoanFetch call() throws Exception {
                LoanFetch fetch = new LoanFetch();
                if (delayMs > 0) {
                    Thread.sleep(delayMs);
                }
                for (CustomerSummary c : todo) {
                    if (generation != loadGeneration) {
                        break;   // a full reload started; this result will be ignored
                    }
                    try {
                        fetch.loans.addAll(customerService.loansOf(c));
                        fetch.loaded.add(c);
                    } catch (Exception ex) {
                        fetch.failed.add(c);
                    }
                }
                return fetch;
            }
        };
        task.setOnSucceeded(e -> {
            if (generation != loadGeneration) {
                return;
            }
            applyFetch(task.getValue());
            afterApplied.run();
        });
        task.setOnFailed(e -> {
            if (generation == loadGeneration) {
                retryRunning = false;
                listener.statusLabelChanged();
            }
        });
        UiTasks.startDaemon(task, "fetch-loans");
    }

    private void applyFetch(LoanFetch fetch) {
        mergeLoans(fetch.loans);
        for (CustomerSummary c : fetch.loaded) {
            loansLoaded.add(c.getNumber());
            retryPending.remove(c.getNumber());
        }
        for (CustomerSummary c : fetch.failed) {
            retryPending.put(c.getNumber(), c);
        }
        failedCustomers = retryPending.size();
        listener.statusesChanged();
        listener.loansChanged();   // also refreshes the Loan Summary card
        if (!fetch.failed.isEmpty()) {
            startRetry();
        }
    }

    /** Adds loans to the list, replacing any with the same loan number. */
    private void mergeLoans(List<LoanSummary> loans) {
        Map<String, Integer> indexByNumber = new HashMap<>();
        for (int i = 0; i < allLoans.size(); i++) {
            indexByNumber.put(allLoans.get(i).getLoanNumber(), i);
        }
        for (LoanSummary loan : loans) {
            Integer at = indexByNumber.get(loan.getLoanNumber());
            if (at != null) {
                allLoans.set(at, loan);
            } else {
                allLoans.add(loan);
                indexByNumber.put(loan.getLoanNumber(), allLoans.size() - 1);
            }
        }
    }

    // ---- Automatic retries ----

    /** Starts retrying the customers the mainframe refused (does nothing if already retrying). */
    private void startRetry() {
        if (retryRunning || retryPending.isEmpty()) {
            return;
        }
        retryRunning = true;
        retryRound = 0;
        nextRetryRound();
        listener.statusLabelChanged();
    }

    /** Waits a little longer each round, then reads the still-missing customers again. */
    private void nextRetryRound() {
        if (retryPending.isEmpty() || retryRound >= MAX_RETRY_ROUNDS) {
            retryRunning = false;
            listener.statusLabelChanged();
            return;
        }
        retryRound++;
        List<CustomerSummary> todo = new ArrayList<>(retryPending.values());
        runFetch(todo, RETRY_DELAY_MS * retryRound, this::nextRetryRound);
    }
}
