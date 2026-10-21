package lavs.ui;

import java.util.List;
import java.util.function.Consumer;

import business.system.Customer;
import business.system.dto.LoanSummary;
import javafx.beans.property.ReadOnlyObjectProperty;
import javafx.beans.property.ReadOnlyObjectWrapper;
import javafx.beans.property.ReadOnlyStringWrapper;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Pos;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableRow;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.layout.HBox;
import javafx.scene.shape.Circle;

/**
 * The Loans card: the search box and status filter narrow the table under them.
 * The loans themselves live in {@link LoanLoader}; this class only shows them.
 */
final class LoanTablePanel {

    private static final String ALL_STATUSES = "All statuses";

    private final TableView<LoanSummary> table;
    private final TextField searchField;
    private final ComboBox<String> statusFilter;
    private final Label statusLabel;
    private final LoanLoader loader;
    private final Runnable onShown;

    private final ObservableList<LoanSummary> visibleLoans = FXCollections.observableArrayList();
    private final Label placeholder = new Label("Loading loans...");

    private Customer customerService;

    /**
     * @param onOpen  called when a loan row is double-clicked
     * @param onShown called after the visible loans were refreshed (used to update the summary card)
     */
    LoanTablePanel(TableView<LoanSummary> table,
            TableColumn<LoanSummary, String> idColumn,
            TableColumn<LoanSummary, String> customerColumn,
            TableColumn<LoanSummary, Double> principalColumn,
            TableColumn<LoanSummary, String> dateColumn,
            TableColumn<LoanSummary, String> statusColumn,
            TextField searchField, ComboBox<String> statusFilter, Label statusLabel,
            LoanLoader loader, Consumer<LoanSummary> onOpen, Runnable onShown) {
        this.table = table;
        this.searchField = searchField;
        this.statusFilter = statusFilter;
        this.statusLabel = statusLabel;
        this.loader = loader;
        this.onShown = onShown;

        placeholder.getStyleClass().add("empty-note");
        placeholder.setWrapText(true);
        placeholder.setMaxWidth(300);
        table.setItems(visibleLoans);
        table.setPlaceholder(placeholder);

        // double-clicking a loan opens its summary (a single click only selects it)
        table.setRowFactory(tv -> {
            TableRow<LoanSummary> row = new TableRow<>();
            row.setOnMouseClicked(e -> {
                if (e.getClickCount() == 2 && !row.isEmpty()) {
                    onOpen.accept(row.getItem());
                }
            });
            return row;
        });

        setupColumns(idColumn, customerColumn, principalColumn, dateColumn, statusColumn);

        // typing narrows the loans straight away; an empty box shows every loan
        searchField.textProperty().addListener((obs, oldText, newText) -> show());

        // status filter: "All statuses" plus the statuses found in the loaded loans
        statusFilter.getItems().setAll(ALL_STATUSES);
        statusFilter.setValue(ALL_STATUSES);
        statusFilter.valueProperty().addListener((obs, oldValue, newValue) -> show());

        updateStatusLabel();
    }

    private void setupColumns(TableColumn<LoanSummary, String> idColumn,
            TableColumn<LoanSummary, String> customerColumn,
            TableColumn<LoanSummary, Double> principalColumn,
            TableColumn<LoanSummary, String> dateColumn,
            TableColumn<LoanSummary, String> statusColumn) {

        idColumn.setCellValueFactory(c -> new ReadOnlyStringWrapper(c.getValue().getLoanNumber()));
        customerColumn.setCellValueFactory(c -> new ReadOnlyStringWrapper(c.getValue().getCustomerName()));
        statusColumn.setCellValueFactory(c -> new ReadOnlyStringWrapper(c.getValue().getStatus()));

        // status shown as a coloured dot followed by the status text
        statusColumn.setCellFactory(col -> new TableCell<>() {
            private final Circle dot = new Circle(4);
            private final Label text = new Label();
            private final HBox box = new HBox(8, dot, text);
            {
                box.setAlignment(Pos.CENTER_LEFT);
                text.getStyleClass().add("cell-body");
            }

            @Override
            protected void updateItem(String status, boolean empty) {
                super.updateItem(status, empty);
                if (empty || status == null || status.isBlank()) {
                    setText(null);
                    setGraphic(null);
                    return;
                }
                dot.getStyleClass().setAll("color-" + StatusColors.of(status, customerService));
                text.setText(status);
                setText(null);
                setGraphic(box);
            }
        });

        // principal can be missing (the mainframe sent nothing usable), so show a dash
        principalColumn.setCellValueFactory(c -> new ReadOnlyObjectWrapper<>(c.getValue().getPrincipal()));
        principalColumn.setCellFactory(col -> new TableCell<>() {
            @Override
            protected void updateItem(Double value, boolean empty) {
                super.updateItem(value, empty);
                setText(empty ? null : value == null ? "-" : String.format("$%,.2f", value));
            }
        });

        // dates are shown as the mainframe sends them, so they sort correctly if they are yyyy-MM-dd
        dateColumn.setCellValueFactory(c -> {
            String date = c.getValue().getStartDate();
            return new ReadOnlyStringWrapper(date == null || date.isBlank() ? "-" : date);
        });
    }

    void setService(Customer customerService) {
        this.customerService = customerService;
    }

    // ---- Selection ----

    LoanSummary selectedLoan() {
        return table.getSelectionModel().getSelectedItem();
    }

    /** Lets the screen enable/disable the "change status" button. */
    ReadOnlyObjectProperty<LoanSummary> selectedLoanProperty() {
        return table.getSelectionModel().selectedItemProperty();
    }

    // ---- Refreshing what is shown ----

    /** Re-applies the search text and status filter to the loaded loans. */
    void show() {
        visibleLoans.setAll(customerService.filterLoans(
                loader.loans(), searchField.getText(), selectedStatus()));
        updatePlaceholder();
        updateStatusLabel();
        onShown.run();
    }

    /** Empties the table while a full reload starts. */
    void clear() {
        visibleLoans.clear();
        updatePlaceholder();
        updateStatusLabel();
    }

    /** The status picked in the filter, or null when "All statuses" is selected. */
    private String selectedStatus() {
        String value = statusFilter.getValue();
        return value == null || ALL_STATUSES.equals(value) ? null : value;
    }

    /** Refreshes the status filter from the loans that are loaded, keeping the current pick if it still exists. */
    void populateStatuses() {
        String current = selectedStatus();
        List<String> statuses = customerService.loanStatusesIn(loader.loans());
        statusFilter.getItems().setAll(ALL_STATUSES);
        statusFilter.getItems().addAll(statuses);
        statusFilter.setValue(current != null && statuses.contains(current) ? current : ALL_STATUSES);
    }

    private void updatePlaceholder() {
        if (loader.isLoading()) {
            placeholder.setText("Loading loans...");
        } else if (loader.loadError() != null) {
            placeholder.setText(loader.loadError());
        } else if (loader.loans().isEmpty()) {
            placeholder.setText("There are no loans yet.");
        } else {
            placeholder.setText("No loans match your search.");
        }
    }

    void updateStatusLabel() {
        String text;
        if (loader.isLoading()) {
            text = "Loading loans... " + loader.loansDone() + "/" + loader.loansTotal() + " customers";
        } else if (loader.loadError() != null) {
            text = loader.loans().isEmpty() ? "Could not load loans" : "Loading stopped early";
        } else {
            int all = loader.loans().size();
            int shown = visibleLoans.size();
            String noun = all == 1 ? " loan" : " loans";
            text = shown == all ? all + noun : shown + " of " + all + noun;
            // the mainframe only lists loans per customer, so say whose loans these are
            int customers = loader.loadedCustomerCount();
            text += " from " + customers + (customers == 1 ? " customer" : " customers");
            if (loader.failedCustomers() > 0) {
                text += " (" + loader.failedCustomers() + " skipped"
                        + (loader.isRetrying() ? ", retrying..." : "") + ")";
            }
        }
        statusLabel.setText(text);
    }
}
