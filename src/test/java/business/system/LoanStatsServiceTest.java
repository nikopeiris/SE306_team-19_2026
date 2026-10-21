package business.system;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;

import business.system.dto.LoanStats;
import business.system.dto.LoanSummary;

class LoanStatsServiceTest {
    private final LoanStatsService service = new LoanStatsService();

    @Test
    void summariseTracksStatusesPrincipalAndVerificationProgress() {
        List<LoanSummary> loans = List.of(
                new LoanSummary("L1", "C1", "Jane", 100.0, null, "Pending"),
                new LoanSummary("L2", "C1", "Jane", null, null, " active "),
                new LoanSummary("L3", "C1", "Jane", 50.0, null, null));

        LoanStats stats = service.summarise(loans);

        assertEquals(3, stats.getTotal());
        assertEquals(1, stats.getAwaitingVerification());
        assertEquals(150.0, stats.getTotalPrincipal());
        assertEquals(3, stats.getByStatus().size());
        assertEquals(1, stats.getShares().get(0).getCount());
        assertEquals(2.0 / 3.0, stats.getVerificationProgress());
    }

    @Test
    void filtersStatusesAndHandlesEmptyPortfolio() {
        List<LoanSummary> loans = List.of(
                new LoanSummary("L1", "C1", "Jane", 100.0, null, "Pending"),
                new LoanSummary("L2", "C1", "Jane", 50.0, null, "Active"));

        assertEquals(2, service.filterByStatus(loans, null).size());
        assertEquals(1, service.filterByStatus(loans, " pending ").size());
        assertEquals(2, service.statusesIn(loans).size());
        assertTrue(service.isAwaitingVerification(" PENDING "));
        assertFalse(service.isAwaitingVerification(null));

        LoanStats empty = service.summarise(List.of());
        assertEquals(0, empty.getVerificationProgress());
        assertTrue(empty.getShares().isEmpty());
    }
}
