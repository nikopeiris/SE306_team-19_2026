package lavs.ui;

import java.util.function.Consumer;

import business.system.dto.NoteRow;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.scene.control.Label;

/** One row in the notes popup. */
public class NoteRowController {
    @FXML private Label typeLabel;
    @FXML private Label detailLabel;
    @FXML private Label idLabel;

    private NoteRow note;
    private Consumer<NoteRow> onEdit;
    private Consumer<NoteRow> onDelete;

    public void setNote(NoteRow note, Consumer<NoteRow> onEdit, Consumer<NoteRow> onDelete) {
        this.note = note;
        this.onEdit = onEdit;
        this.onDelete = onDelete;
        typeLabel.setText(LoanFormat.text(note.type()));
        detailLabel.setText("Added by " + LoanFormat.text(note.author())
                + " on " + LoanFormat.text(note.dateAdded()));
        idLabel.setText("Note #" + note.id());
    }

    @FXML
    private void handleEdit(ActionEvent event) {
        onEdit.accept(note);
    }

    @FXML
    private void handleDelete(ActionEvent event) {
        onDelete.accept(note);
    }
}