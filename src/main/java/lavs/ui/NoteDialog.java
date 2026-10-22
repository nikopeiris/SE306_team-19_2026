package lavs.ui;

import java.io.IOException;

import business.system.LoanDetailsService;
import business.system.dto.NoteDetail;
import business.system.dto.NoteRow;
import javafx.concurrent.Task;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.stage.Window;

/** Add/edit form for a loan note. */
public class NoteDialog {
    @FXML private TextField typeField;
    @FXML private TextArea linesArea;
    @FXML private Label errorLabel;
    @FXML private Button saveButton;

    private LoanDetailsService details;
    private String loanNumber;
    private NoteRow existing;
    private Runnable onSaved;
    private Stage stage;

    static void show(LoanDetailsService details, String loanNumber, NoteRow existing,
            Window owner, Runnable onSaved) {
        try {
            FXMLLoader loader = new FXMLLoader(
                    NoteDialog.class.getResource("/lavs/ui/note_dialog.fxml"));
            Parent root = loader.load();
            NoteDialog dialog = loader.getController();
            dialog.open(details, loanNumber, existing, owner, onSaved, root);
        } catch (IOException e) {
            Alert alert = new Alert(Alert.AlertType.ERROR, CustomerDocumentsDialog.describe(e));
            alert.setTitle("Note");
            alert.setHeaderText("Could not open the note form");
            alert.showAndWait();
        }
    }

    private void open(LoanDetailsService details, String loanNumber, NoteRow existing,
            Window owner, Runnable onSaved, Parent root) {
        this.details = details;
        this.loanNumber = loanNumber;
        this.existing = existing;
        this.onSaved = onSaved;
        stage = new Stage();
        stage.initOwner(owner);
        stage.initModality(Modality.APPLICATION_MODAL);
        stage.setTitle(existing == null ? "Add Note" : "Edit Note");
        stage.setScene(CustomerDocumentsDialog.styled(new Scene(root)));
        if (existing != null) {
            loadExisting();
        }
        stage.showAndWait();
    }

    /** The list only has each note's type, so the text is fetched before it can be edited. */
    private void loadExisting() {
        saveButton.setDisable(true);
        setStatus("Loading note...", false);
        Task<NoteDetail> task = new Task<>() {
            @Override
            protected NoteDetail call() throws Exception {
                return details.noteDetail(loanNumber, existing.id());
            }
        };
        task.setOnSucceeded(e -> {
            NoteDetail note = task.getValue();
            typeField.setText(note.type());
            linesArea.setText(note.lines());
            saveButton.setDisable(false);
            setStatus("", false);
        });
        // saving stays off, so a note that failed to load is never overwritten with a blank form
        task.setOnFailed(e ->
                setStatus(CustomerDocumentsDialog.describe(task.getException()), true));
        CustomerDocumentsDialog.start(task, "load-note");
    }

    @FXML
    private void handleSave(ActionEvent event) {
        setStatus("", false);
        saveButton.setDisable(true);
        final String type = typeField.getText();
        final String lines = linesArea.getText();
        Task<Void> task = new Task<>() {
            @Override
            protected Void call() throws Exception {
                if (existing == null) {
                    details.addNote(loanNumber, lines, type);
                } else {
                    details.updateNote(loanNumber, existing.id(), lines, type);
                }
                return null;
            }
        };
        task.setOnSucceeded(e -> {
            stage.close();
            onSaved.run();
        });
        task.setOnFailed(e -> {
            saveButton.setDisable(false);
            setStatus(CustomerDocumentsDialog.describe(task.getException()), true);
        });
        CustomerDocumentsDialog.start(task, "save-note");
    }

    @FXML
    private void handleCancel(ActionEvent event) {
        stage.close();
    }

    private void setStatus(String message, boolean error) {
        errorLabel.setText(message);
        errorLabel.setStyle(error ? "-fx-text-fill: #b00020;" : "-fx-text-fill: #726e97;");
    }
}