package lavs.ui;

import java.io.IOException;
import java.util.List;
import java.util.Optional;

import business.system.LoanDetailsService;
import business.system.dto.CoborrowerRow;
import javafx.concurrent.Task;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Label;
import javafx.scene.layout.VBox;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.stage.Window;

/** Lists and manages the co-borrowers and guarantors of one loan. */
public class CoborrowersDialog {
    @FXML private Label hintLabel;
    @FXML private Label emptyLabel;
    @FXML private Label statusLabel;
    @FXML private VBox listContainer;
    @FXML private Button addButton;

    private LoanDetailsService details;
    private String loanNumber;
    private Stage stage;

    static void show(LoanDetailsService details, String loanNumber, Window owner) {
        try {
            FXMLLoader loader = new FXMLLoader(
                    CoborrowersDialog.class.getResource("/lavs/ui/coborrowers_dialog.fxml"));
            Parent root = loader.load();
            CoborrowersDialog dialog = loader.getController();
            dialog.open(details, loanNumber, owner, root);
        } catch (IOException e) {
            Alert alert = new Alert(Alert.AlertType.ERROR, CustomerDocumentsDialog.describe(e));
            alert.setTitle("Co-borrowers");
            alert.setHeaderText("Could not open the co-borrowers window");
            alert.showAndWait();
        }
    }

    private void open(LoanDetailsService details, String loanNumber, Window owner, Parent root) {
        this.details = details;
        this.loanNumber = loanNumber;
        hintLabel.setText("Loan " + loanNumber
                + ". Co-borrower changes are saved straight away.");
        stage = new Stage();
        stage.initOwner(owner);
        stage.initModality(Modality.APPLICATION_MODAL);
        stage.setTitle("Co-borrowers - Loan " + loanNumber);
        stage.setScene(CustomerDocumentsDialog.styled(new Scene(root)));
        refresh();
        stage.showAndWait();
    }

    @FXML
    private void handleAdd(ActionEvent event) {
        CoborrowerDialog.show(details, loanNumber, null, stage, this::refresh);
    }

    @FXML
    private void handleClose(ActionEvent event) {
        stage.close();
    }

    private void refresh() {
        setStatus("Loading co-borrowers...", false);
        addButton.setDisable(true);
        Task<List<CoborrowerRow>> task = new Task<>() {
            @Override
            protected List<CoborrowerRow> call() throws Exception {
                return details.coborrowersFor(loanNumber);
            }
        };
        task.setOnSucceeded(e -> {
            addButton.setDisable(false);
            setStatus("", false);
            render(task.getValue());
        });
        task.setOnFailed(e -> {
            addButton.setDisable(false);
            setStatus(CustomerDocumentsDialog.describe(task.getException()), true);
        });
        CustomerDocumentsDialog.start(task, "load-coborrowers");
    }

    private void render(List<CoborrowerRow> coborrowers) {
        listContainer.getChildren().clear();
        emptyLabel.setVisible(coborrowers.isEmpty());
        emptyLabel.setManaged(coborrowers.isEmpty());
        try {
            for (CoborrowerRow coborrower : coborrowers) {
                FXMLLoader loader = new FXMLLoader(
                        getClass().getResource("/lavs/ui/coborrower_row.fxml"));
                Node row = loader.load();
                CoborrowerRowController controller = loader.getController();
                controller.setCoborrower(coborrower, c -> edit(c), this::confirmDelete);
                listContainer.getChildren().add(row);
            }
        } catch (IOException e) {
            setStatus(CustomerDocumentsDialog.describe(e), true);
        }
    }

    private void edit(CoborrowerRow coborrower) {
        CoborrowerDialog.show(details, loanNumber, coborrower, stage, this::refresh);
    }

    private void confirmDelete(CoborrowerRow coborrower) {
        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION,
                "Remove " + LoanFormat.text(coborrower.name()) + " from this loan?",
                ButtonType.YES, ButtonType.NO);
        confirm.setTitle("Remove co-borrower");
        confirm.setHeaderText(null);
        confirm.initOwner(stage);
        Optional<ButtonType> answer = confirm.showAndWait();
        if (answer.isEmpty() || answer.get() != ButtonType.YES) {
            return;
        }
        setStatus("Removing co-borrower...", false);
        Task<Void> task = new Task<>() {
            @Override
            protected Void call() throws Exception {
                details.deleteCoborrower(loanNumber, coborrower.id());
                return null;
            }
        };
        task.setOnSucceeded(e -> refresh());
        task.setOnFailed(e -> setStatus(CustomerDocumentsDialog.describe(task.getException()), true));
        CustomerDocumentsDialog.start(task, "delete-coborrower");
    }

    private void setStatus(String message, boolean error) {
        statusLabel.setText(message);
        statusLabel.setStyle(error ? "-fx-text-fill: #b00020;" : "-fx-text-fill: #726e97;");
    }
}
