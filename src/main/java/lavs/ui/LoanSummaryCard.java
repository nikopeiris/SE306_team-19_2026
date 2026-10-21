package lavs.ui;

import java.util.List;

import business.system.Customer;
import business.system.dto.LoanStats;
import business.system.dto.LoanSummary;
import javafx.geometry.Pos;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressBar;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.Pane;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.shape.Circle;

/** The Loan Summary card: totals, one bubble per status, and the verification progress bar. */
final class LoanSummaryCard {

    private static final double BUBBLE_AREA_WIDTH = 432.0;
    private static final double BUBBLE_AREA_HEIGHT = 120.0;
    private static final double BUBBLE_MAX = 116.0;
    private static final double BUBBLE_MIN = 38.0;
    private static final double BUBBLE_OVERLAP = 6.0;

    private final Label totalsLabel;
    private final Pane bubblePane;
    private final ProgressBar progressBar;
    private final Label progressLabel;

    private Customer customerService;

    LoanSummaryCard(Label totalsLabel, Pane bubblePane, ProgressBar progressBar, Label progressLabel) {
        this.totalsLabel = totalsLabel;
        this.bubblePane = bubblePane;
        this.progressBar = progressBar;
        this.progressLabel = progressLabel;
    }

    void setService(Customer customerService) {
        this.customerService = customerService;
    }

    /** Fills the card; the figures come from the business layer. */
    void update(List<LoanSummary> loans) {
        if (customerService == null) {   // dashboard is built before the services are handed over
            totalsLabel.setText("-");
            bubblePane.getChildren().clear();
            progressBar.setProgress(0);
            progressLabel.setText("-");
            return;
        }
        LoanStats stats = customerService.summariseLoans(loans);
        int total = stats.getTotal();
        totalsLabel.setText(total == 0 ? "-"
                : total + (total == 1 ? " loan" : " loans")
                        + "  |  $" + compactAmount(stats.getTotalPrincipal()) + " total");

        renderBubbles(stats);

        double progress = stats.getVerificationProgress();
        progressBar.setProgress(progress);
        progressLabel.setText(total == 0 ? "-" : Math.round(progress * 100) + "% complete");
    }

    /** Draws one bubble per status, sized by its share of all loans (area follows the share). */
    private void renderBubbles(LoanStats stats) {
        bubblePane.getChildren().clear();
        List<LoanStats.StatusShare> shares = stats.getShares();
        if (shares.isEmpty()) {
            Label none = new Label("No loans to show yet");
            none.getStyleClass().add("empty-note");
            none.setPrefWidth(BUBBLE_AREA_WIDTH);
            none.setAlignment(Pos.CENTER);
            none.setLayoutY(50);
            bubblePane.getChildren().add(none);
            return;
        }

        int n = shares.size();
        double biggest = shares.get(0).getPercent();
        double[] size = new double[n];
        double sum = 0;
        for (int i = 0; i < n; i++) {
            size[i] = Math.max(BUBBLE_MIN, BUBBLE_MAX * Math.sqrt(shares.get(i).getPercent() / biggest));
            sum += size[i];
        }
        double overlaps = BUBBLE_OVERLAP * (n - 1);
        double total = sum - overlaps;
        if (total > BUBBLE_AREA_WIDTH) {   // lots of statuses: shrink everything to fit
            double scale = (BUBBLE_AREA_WIDTH + overlaps) / sum;
            for (int i = 0; i < n; i++) {
                size[i] *= scale;
            }
            total = BUBBLE_AREA_WIDTH;
        }

        // biggest bubble in the middle, the rest alternate bottom / top so they cluster together
        double x = (BUBBLE_AREA_WIDTH - total) / 2;
        for (int i = 0; i < n; i++) {
            double d = size[i];
            double y = i == 0 ? (BUBBLE_AREA_HEIGHT - d) / 2
                    : i % 2 == 1 ? BUBBLE_AREA_HEIGHT - d : 0;
            bubblePane.getChildren().add(bubble(shares.get(i), d, x, y));
            x += d - BUBBLE_OVERLAP;
        }
    }

    private StackPane bubble(LoanStats.StatusShare share, double diameter, double x, double y) {
        String color = StatusColors.of(share.getStatus(), customerService);
        String textClass = "orange".equals(color) ? "bubble-dark-text" : "bubble-light-text";

        Circle circle = new Circle(diameter / 2);
        circle.getStyleClass().addAll("bubble", "color-" + color);

        Label percent = new Label(formatPercent(share.getPercent()));
        percent.getStyleClass().addAll("bubble-percent", textClass);
        percent.setStyle(String.format("-fx-font-size: %.0fpx;",
                Math.max(11, Math.min(26, diameter * 0.26))));

        VBox text = new VBox(0, percent);
        text.setAlignment(Pos.CENTER);
        text.setMouseTransparent(true);
        if (diameter >= 72) {   // small bubbles only have room for the percentage
            Label name = new Label(share.getStatus());
            name.getStyleClass().addAll("bubble-name", textClass);
            name.setMaxWidth(diameter - 16);
            text.getChildren().add(name);
        }

        StackPane bubble = new StackPane(circle, text);
        bubble.setLayoutX(x);
        bubble.setLayoutY(y);
        int count = share.getCount();
        Tooltip.install(bubble, new Tooltip(share.getStatus() + ": " + count
                + (count == 1 ? " loan" : " loans") + " (" + formatPercent(share.getPercent()) + ")"));
        return bubble;
    }

    private static String formatPercent(double percent) {
        return percent > 0 && percent < 1 ? "<1%" : Math.round(percent) + "%";
    }

    /** 1234567 becomes "1.2M" so big totals still fit in the tile. */
    private static String compactAmount(double amount) {
        double abs = Math.abs(amount);
        if (abs >= 1_000_000_000) {
            return String.format("%.1fB", amount / 1_000_000_000);
        } else if (abs >= 1_000_000) {
            return String.format("%.1fM", amount / 1_000_000);
        } else if (abs >= 10_000) {
            return String.format("%.0fK", amount / 1_000);
        }
        return String.format("%,.0f", amount);
    }
}
