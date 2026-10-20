package business.system;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import business.system.dto.LoanStats;
import business.system.dto.LoanSummary;

/**
 * Dashboard figures and filters that are worked out from the loans already loaded
 * (no extra mainframe requests).
 */
public class LoanStatsService {

    /** The status a loan has once it has been sent for checking. */
    static final String AWAITING_VERIFICATION_STATUS = "Pending";

    private static final String UNKNOWN_STATUS = "Unknown";

    /** Totals, loans awaiting verification, total principal, and counts per status. */
    public LoanStats summarise(List<LoanSummary> loans) {
        int awaiting = 0;
        double principal = 0;
        Map<String, Integer> byStatus = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);

        for (LoanSummary loan : loans) {
            String status = statusOf(loan);
            byStatus.merge(status, 1, Integer::sum);
            if (isAwaitingVerification(status)) {
                awaiting++;
            }
            if (loan.getPrincipal() != null) {
                principal += loan.getPrincipal();
            }
        }
        return new LoanStats(loans.size(), awaiting, principal, byStatus);
    }

    /** True when a loan with this status is waiting to be checked. */
    public boolean isAwaitingVerification(String status) {
        return status != null && AWAITING_VERIFICATION_STATUS.equalsIgnoreCase(status.trim());
    }

    /** Keeps only the loans with this status; a null or blank status returns the whole list. */
    public List<LoanSummary> filterByStatus(List<LoanSummary> loans, String status) {
        if (status == null || status.isBlank()) {
            return loans;
        }
        List<LoanSummary> matching = new ArrayList<>();
        for (LoanSummary loan : loans) {
            if (statusOf(loan).equalsIgnoreCase(status.trim())) {
                matching.add(loan);
            }
        }
        return matching;
    }

    /** The distinct statuses present in the loans, sorted by name. */
    public List<String> statusesIn(List<LoanSummary> loans) {
        Map<String, Integer> byStatus = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
        for (LoanSummary loan : loans) {
            byStatus.merge(statusOf(loan), 1, Integer::sum);
        }
        return new ArrayList<>(byStatus.keySet());
    }

    private static String statusOf(LoanSummary loan) {
        String status = loan.getStatus();
        return status == null || status.isBlank() ? UNKNOWN_STATUS : status.trim();
    }
}