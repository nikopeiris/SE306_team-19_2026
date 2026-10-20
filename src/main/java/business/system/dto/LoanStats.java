package business.system.dto;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/** Headline numbers for the dashboard's Loan Summary card. */
public final class LoanStats {
    private final int total;
    private final int awaitingVerification;
    private final int incompleteInformation;
    private final Map<String, Integer> byStatus;

    public LoanStats(int total, int awaitingVerification, int incompleteInformation,
                     Map<String, Integer> byStatus) {
        this.total = total;
        this.awaitingVerification = awaitingVerification;
        this.incompleteInformation = incompleteInformation;
        this.byStatus = Collections.unmodifiableMap(new LinkedHashMap<>(byStatus));
    }

    public int getTotal()                 { return total; }
    public int getAwaitingVerification()  { return awaitingVerification; }
    public int getIncompleteInformation() { return incompleteInformation; }
    /** Number of loans per status, sorted by status name. */
    public Map<String, Integer> getByStatus() { return byStatus; }
}
