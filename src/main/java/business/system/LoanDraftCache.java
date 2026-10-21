package business.system;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import business.system.dto.LoanApplication;

/**
 * Keeps the most recent loan application draft for each customer.
 *
 */
public class LoanDraftCache {
    private final ConcurrentMap<String, LoanApplication> drafts = new ConcurrentHashMap<>();

    public void save(String customerNumber, LoanApplication application) {
        drafts.put(requireCustomerNumber(customerNumber), requireApplication(application));
    }

    public LoanApplication get(String customerNumber) {
        if (customerNumber == null || customerNumber.isBlank()) {
            return null;
        }
        return drafts.get(customerNumber.trim());
    }

    public void clear(String customerNumber) {
        if (customerNumber != null && !customerNumber.isBlank()) {
            drafts.remove(customerNumber.trim());
        }
    }

    private String requireCustomerNumber(String customerNumber) {
        if (customerNumber == null || customerNumber.isBlank()) {
            throw new IllegalArgumentException("Customer number cannot be null or empty");
        }
        return customerNumber.trim();
    }

    private LoanApplication requireApplication(LoanApplication application) {
        if (application == null) {
            throw new IllegalArgumentException("Loan application cannot be null");
        }
        return application;
    }
}
