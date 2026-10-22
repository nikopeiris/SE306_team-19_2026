package lavs.ui;

import java.io.IOException;
import java.util.List;
import java.util.Optional;

import business.system.LoanDetailsService;
import business.system.dto.NoteRow;
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

/** Lists and manages notes for one loan. */
public class NotesDialog {
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
                    NotesDialog.class.getResource("/lavs/ui/notes_dialog.fxml"));
            Parent root = loader.load();
            NotesDialog dialog = loader.getController();
            dialog.open(details, loanNumber, owner, root);
        } catch (IOException e) {
            Alert alert = new Alert(Alert.AlertType.ERROR, CustomerDocumentsDialog.describe(e));
            alert.setTitle("Notes");
            alert.setHeaderText("Could not open the notes window");
            alert.showAndWait();
        }
    }

    private void open(LoanDetailsService details, String loanNumber, Window owner, Parent root) {
        this.details = details;
        this.loanNumber = loanNumber;
        hintLabel.setText("Loan " + loanNumber + ". Note changes are saved straight away.");
        stage = new Stage();
        stage.initOwner(owner);
        stage.initModality(Modality.APPLICATION_MODAL);
        stage.setTitle("Notes - Loan " + loanNumber);
        stage.setScene(CustomerDocumentsDialog.styled(new Scene(root)));
        refresh();
        stage.showAndWait();
    }

    @FXML
    private void handleAdd(ActionEvent event) {
        NoteDialog.show(details, loanNumber, null, stage, this::refresh);
    }

    @FXML
    private void handleClose(ActionEvent event) {
        stage.close();
    }

    private void refresh() {
        setStatus("Loading notes...", false);
        addButton.setDisable(true);
        Task<List<NoteRow>> task = new Task<>() {
            @Override
            protected List<NoteRow> call() throws Exception {
                return details.notesFor(loanNumber);
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
        CustomerDocumentsDialog.start(task, "load-notes");
    }

    private void render(List<NoteRow> notes) {
        listContainer.getChildren().clear();
        emptyLabel.setVisible(notes.isEmpty());
        emptyLabel.setManaged(notes.isEmpty());
        try {
            for (NoteRow note : notes) {
                FXMLLoader loader = new FXMLLoader(
                        getClass().getResource("/lavs/ui/note_row.fxml"));
                Node row = loader.load();
                NoteRowController controller = loader.getController();
                controller.setNote(note, n -> edit(n), this::confirmDelete);
                listContainer.getChildren().add(row);
            }
        } catch (IOException e) {
            setStatus(CustomerDocumentsDialog.describe(e), true);
        }
    }

    private void edit(NoteRow note) {
        NoteDialog.show(details, loanNumber, note, stage, this::refresh);
    }

    private void confirmDelete(NoteRow note) {
        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION,
                "Delete the " + LoanFormat.text(note.type()) + " note?",
                ButtonType.YES, ButtonType.NO);
        confirm.setTitle("Delete note");
        confirm.setHeaderText(null);
        confirm.initOwner(stage);
        Optional<ButtonType> answer = confirm.showAndWait();
        if (answer.isEmpty() || answer.get() != ButtonType.YES) {
            return;
        }
        setStatus("Deleting note...", false);
        Task<Void> task = new Task<>() {
            @Override
            protected Void call() throws Exception {
                details.deleteNote(loanNumber, note.id());
                return null;
            }
        };
        task.setOnSucceeded(e -> refresh());
        task.setOnFailed(e ->
                setStatus(CustomerDocumentsDialog.describe(task.getException()), true));
        CustomerDocumentsDialog.start(task, "delete-note");
    }

    private void setStatus(String message, boolean error) {
        statusLabel.setText(message);
        statusLabel.setStyle(error ? "-fx-text-fill: #b00020;" : "-fx-text-fill: #726e97;");
    }
}