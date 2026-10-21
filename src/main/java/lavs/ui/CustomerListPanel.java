package lavs.ui;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import business.system.Customer;
import business.system.customer.CustomerEmailService;
import business.system.dto.CustomerSummary;
import javafx.animation.PauseTransition;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.collections.transformation.FilteredList;
import javafx.concurrent.Task;
import javafx.scene.control.Label;
import javafx.scene.control.ListView;
import javafx.scene.control.TextField;
import javafx.util.Duration;

/**
 * The customer list card: loading customers, filtering as you type, searching the mainframe
 * by name or customer number, and showing each customer's primary email.
 */
final class CustomerListPanel {

    /** What the panel needs from the rest of the dashboard. */
    interface Host {
        /** The full customer list finished loading (a snapshot of it is passed). */
        void customersLoaded(List<CustomerSummary> customers);

        /** Customers were found or saved; their loans may need reading. */
        void customersFound(List<CustomerSummary> customers, boolean forceReload);

        /** Open this customer's screen. */
        void openCustomer(CustomerSummary customer, String customerNumber);
    }

    /** Load every customer's primary email in the background so email search is complete. */
    private static final boolean PREFETCH_EMAILS = true;
    /** A customer number looks like 01-0001-0030590. */
    private static final String CUSTOMER_NUMBER = "\\d{2}-\\d{4}-\\d{7}";

    private final ListView<CustomerSummary> listView;
    private final TextField searchField;
    private final Host host;
    private final EmailDirectory emails;

    private final ObservableList<CustomerSummary> allCustomers = FXCollections.observableArrayList();
    private final FilteredList<CustomerSummary> visibleCustomers =
            new FilteredList<>(allCustomers, c -> true);
    private final Label placeholder = new Label("Loading customers...");
    private boolean loadingCustomers = false;
    private String customerLoadError = null;

    /** Wait for a pause in typing before asking the mainframe. */
    private final PauseTransition searchDelay = new PauseTransition(Duration.millis(450));
    /** Bumped on every search so a slow, older search cannot overwrite a newer one. */
    private int searchGeneration = 0;
    /** What to show in the empty list after a mainframe search (null = nothing special). */
    private String searchMessage = null;

    private Customer customerService;

    CustomerListPanel(ListView<CustomerSummary> listView, TextField searchField, Host host) {
        this.listView = listView;
        this.searchField = searchField;
        this.host = host;
        this.emails = new EmailDirectory(this::refreshRows);

        placeholder.getStyleClass().add("empty-note");

        listView.setItems(visibleCustomers);
        listView.setPlaceholder(placeholder);
        listView.setFixedCellSize(CustomerCell.ROW_HEIGHT);
        listView.setCellFactory(lv -> new CustomerCell(
                emails::textFor, customer -> host.openCustomer(customer, customer.getNumber())));

        // typing filters the list straight away (name, customer id or primary email)
        searchField.textProperty().addListener((obs, oldText, newText) -> {
            searchMessage = null;
            applyFilter();
            searchDelay.playFromStart();   // then also ask the mainframe (see searchMainframe)
        });
        searchDelay.setOnFinished(e -> searchMainframe(false));
        searchField.setOnAction(e -> {   // Enter searches straight away
            searchDelay.stop();
            searchMainframe(true);
        });
    }

    void setServices(Customer customerService, CustomerEmailService emailService) {
        this.customerService = customerService;
        emails.setEmailService(emailService);
    }

    /** Stops any email lookups still running when the user leaves the dashboard. */
    void shutdown() {
        emails.shutdown();
    }

    /** Called when a burst of emails has arrived. */
    private void refreshRows() {
        applyFilter();
        listView.refresh();
    }

    /** The list's current entry for this customer (it may have been renamed since). */
    CustomerSummary currentSummary(CustomerSummary fallback) {
        for (CustomerSummary c : allCustomers) {
            if (c.getNumber().equals(fallback.getNumber())) {
                return c;
            }
        }
        return fallback;
    }

    // ------------------------------------------------------------------
    // Loading and filtering customers
    // ------------------------------------------------------------------

    void load() {
        loadingCustomers = true;
        customerLoadError = null;
        updatePlaceholder();

        Task<List<CustomerSummary>> loadTask = new Task<>() {
            @Override
            protected List<CustomerSummary> call() throws Exception {
                return customerService.listAllCustomers();   // already sorted by first name
            }
        };

        loadTask.setOnSucceeded(e -> {
            loadingCustomers = false;
            allCustomers.setAll(loadTask.getValue());
            updatePlaceholder();
            if (PREFETCH_EMAILS) {
                emails.prefetch(allCustomers);
            }
            host.customersLoaded(new ArrayList<>(allCustomers));
        });

        loadTask.setOnFailed(e -> {
            loadingCustomers = false;
            customerLoadError = UiTasks.describe(loadTask.getException());
            updatePlaceholder();
        });

        UiTasks.startDaemon(loadTask, "load-customers");
    }

    /** Throws away the cached data and loads everything again. */
    void reload() {
        if (loadingCustomers) return; // Prevent overlapping reload calls

        // Clear existing caches to force a fresh fetch
        emails.clear();
        allCustomers.clear();

        load();
    }

    private void applyFilter() {
        String query = searchField.getText() == null
                ? "" : searchField.getText().trim().toLowerCase();
        visibleCustomers.setPredicate(c -> query.isEmpty() || matches(c, query));
        updatePlaceholder();
    }

    private boolean matches(CustomerSummary customer, String query) {
        if (customer.getFullName().toLowerCase().contains(query)) {
            return true;
        }
        if (customer.getNumber().toLowerCase().contains(query)) {
            return true;
        }
        String email = emails.cachedEmail(customer.getNumber());
        return email != null && email.toLowerCase().contains(query);
    }

    private void updatePlaceholder() {
        if (loadingCustomers) {
            placeholder.setText("Loading customers...");
        } else if (customerLoadError != null) {
            placeholder.setText(customerLoadError);
        } else if (allCustomers.isEmpty()) {
            placeholder.setText("There are no customers yet.");
        } else if (searchMessage != null) {
            placeholder.setText(searchMessage);
        } else {
            placeholder.setText("No customers match your search.");
        }
    }

    // ------------------------------------------------------------------
    // Finding customers on the mainframe
    // ------------------------------------------------------------------
    // The mainframe cannot list every customer: its search only matches WHOLE first or
    // family names (and an empty search returns nothing). The list above therefore only
    // holds customers whose name is a single letter or digit; everyone else is found by
    // typing their full first or family name (or by opening their customer number).

    /**
     * Asks the mainframe for customers matching the typed text and adds them to the list.
     *
     * @param enterPressed true when the user pressed Enter (a customer number then opens the
     *                     customer, and very short text is searched too)
     */
    private void searchMainframe(boolean enterPressed) {
        if (customerService == null) {
            return;
        }
        final String term = searchField.getText() == null
                ? "" : searchField.getText().trim();
        if (term.isEmpty()) {
            return;
        }
        if (term.matches(CUSTOMER_NUMBER)) {
            lookUpByNumber(term, enterPressed);
            return;
        }
        // single characters are already covered by the list itself
        if (term.length() < 2 && !enterPressed) {
            return;
        }

        final int generation = ++searchGeneration;
        searchMessage = "Searching...";
        updatePlaceholder();

        Task<List<CustomerSummary>> task = new Task<>() {
            @Override
            protected List<CustomerSummary> call() throws Exception {
                return customerService.searchCustomers(term);
            }
        };
        task.setOnSucceeded(e -> {
            if (generation != searchGeneration) {
                return;   // the user has typed something newer since
            }
            List<CustomerSummary> found = task.getValue();
            mergeIntoList(found);
            host.customersFound(found, false);
            searchMessage = found.isEmpty()
                    ? "No customer found for \"" + term + "\". The search only matches whole "
                            + "first or family names (e.g. \"jeff\", not \"jef\")."
                    : null;
            applyFilter();
        });
        task.setOnFailed(e -> {
            if (generation != searchGeneration) {
                return;
            }
            searchMessage = UiTasks.describe(task.getException());
            applyFilter();
        });
        UiTasks.startDaemon(task, "search-customers");
    }

    /** Looks a customer up by number; opens them when asked, otherwise just lists them. */
    private void lookUpByNumber(String number, boolean open) {
        final int generation = ++searchGeneration;
        Task<CustomerSummary> task = new Task<>() {
            @Override
            protected CustomerSummary call() throws Exception {
                return customerService.findCustomerSummary(number);
            }
        };
        task.setOnSucceeded(e -> {
            CustomerSummary found = task.getValue();
            mergeIntoList(List.of(found));
            host.customersFound(List.of(found), false);
            if (open) {
                host.openCustomer(found, number);
            } else if (generation == searchGeneration) {
                searchMessage = null;
                applyFilter();
            }
        });
        task.setOnFailed(e -> {
            if (generation != searchGeneration) {
                return;
            }
            searchMessage = "No customer found with number " + number + ".";
            applyFilter();
        });
        UiTasks.startDaemon(task, "lookup-customer");
    }

    /** After a save: fetch the customer again so the list shows the new or changed name. */
    void showSaved(String number) {
        if (number == null || number.isBlank() || customerService == null) {
            return;
        }
        Task<CustomerSummary> task = new Task<>() {
            @Override
            protected CustomerSummary call() throws Exception {
                return customerService.findCustomerSummary(number);
            }
        };
        task.setOnSucceeded(e -> {
            CustomerSummary saved = task.getValue();
            mergeIntoList(List.of(saved));
            host.customersFound(List.of(saved), true);   // loans may have changed too
            RecentItems.rememberCustomer(saved);
            applyFilter();
        });
        task.setOnFailed(e -> task.getException().printStackTrace());
        UiTasks.startDaemon(task, "refresh-saved-customer");
    }

    /** Adds new customers to the list (or refreshes ones already there) and keeps it sorted. */
    private void mergeIntoList(List<CustomerSummary> customers) {
        for (CustomerSummary c : customers) {
            boolean replaced = false;
            for (int i = 0; i < allCustomers.size(); i++) {
                if (allCustomers.get(i).getNumber().equals(c.getNumber())) {
                    allCustomers.set(i, c);
                    replaced = true;
                    break;
                }
            }
            if (!replaced) {
                allCustomers.add(c);
            }
        }
        FXCollections.sort(allCustomers, Comparator
                .comparing((CustomerSummary c) -> nameKey(c.getFirstName()))
                .thenComparing(c -> nameKey(c.getFamilyName())));
    }

    private static String nameKey(String name) {
        return name == null ? "" : name.toLowerCase();
    }
}
