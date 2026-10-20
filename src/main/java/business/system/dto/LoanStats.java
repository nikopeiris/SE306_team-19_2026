package business.system.dto;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Headline numbers for the dashboard's Loan Summary card. */
public final class LoanStats {

    /** One status's slice of the loan portfolio. */
    public static final class StatusShare {
        private final String status;
        private final int count;
        private final double percent;   // 0-100

        StatusShare(String status, int count, double percent) {
            this.status = status;
            this.count = count;
            this.percent = percent;
        }

        public String getStatus()  { return status; }
        public int getCount()      { return count; }
        public double getPercent() { return percent; }
    }

    private final int total;
    private final int awaitingVerification;
    private final double totalPrincipal;
    private final Map<String, Integer> byStatus;
    private final List<StatusShare> shares;

    public LoanStats(int total, int awaitingVerification, double totalPrincipal,
                     Map<String, Integer> byStatus) {
        this.total = total;
        this.awaitingVerification = awaitingVerification;
        this.totalPrincipal = totalPrincipal;
        this.byStatus = Collections.unmodifiableMap(new LinkedHashMap<>(byStatus));

        List<StatusShare> list = new ArrayList<>();
        for (Map.Entry<String, Integer> entry : byStatus.entrySet()) {
            double percent = total == 0 ? 0 : entry.getValue() * 100.0 / total;
            list.add(new StatusShare(entry.getKey(), entry.getValue(), percent));
        }
        // biggest slice first, then by name
        list.sort(Comparator.comparingInt(StatusShare::getCount).reversed()
                .thenComparing(StatusShare::getStatus, String.CASE_INSENSITIVE_ORDER));
        this.shares = Collections.unmodifiableList(list);
    }

    public int getTotal()                { return total; }
    public int getAwaitingVerification() { return awaitingVerification; }
    /** Sum of the principal of every loan that has one. */
    public double getTotalPrincipal()    { return totalPrincipal; }
    /** Number of loans per status, sorted by status name. */
    public Map<String, Integer> getByStatus() { return byStatus; }
    /** Each status's share of all loans, biggest first (empty when there are no loans). */
    public List<StatusShare> getShares() { return shares; }

    /** Fraction (0-1) of loans that are no longer waiting to be verified; 0 when there are no loans. */
    public double getVerificationProgress() {
        return total == 0 ? 0 : (double) (total - awaitingVerification) / total;
    }
}