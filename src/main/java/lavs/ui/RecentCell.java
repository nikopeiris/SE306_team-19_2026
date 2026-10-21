package lavs.ui;

import java.util.function.Consumer;
import java.util.function.Function;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Cursor;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.layout.VBox;

/** One row in the Recent Customers / Recent Loans lists (title + detail). */
class RecentCell<T> extends ListCell<T> {
    private final VBox texts = new VBox(2);
    private final Label primary = new Label();
    private final Label secondary = new Label();
    private final Function<T, String> primaryText;
    private final Function<T, String> secondaryText;

    RecentCell(Function<T, String> primaryText, Function<T, String> secondaryText,
            Consumer<T> onClick) {
        this.primaryText = primaryText;
        this.secondaryText = secondaryText;

        primary.getStyleClass().add("cell-primary");
        secondary.getStyleClass().add("cell-secondary");
        texts.setAlignment(Pos.CENTER_LEFT);
        texts.setPadding(new Insets(0, 10, 0, 10));
        texts.getChildren().addAll(primary, secondary);

        setOnMouseClicked(e -> {
            T item = getItem();
            if (!isEmpty() && item != null) {
                getListView().getSelectionModel().clearSelection();
                onClick.accept(item);
            }
        });
    }

    @Override
    protected void updateItem(T item, boolean empty) {
        super.updateItem(item, empty);
        if (empty || item == null) {
            setText(null);
            setGraphic(null);
            setCursor(Cursor.DEFAULT);
            return;
        }
        primary.setText(primaryText.apply(item));
        secondary.setText(secondaryText.apply(item));
        setText(null);
        setGraphic(texts);
        setCursor(Cursor.HAND);
    }
}
