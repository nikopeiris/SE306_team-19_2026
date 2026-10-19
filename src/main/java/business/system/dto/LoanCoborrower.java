package business.system.dto;

/**
 * One co-borrower or guarantor on a loan, as plain data for the UI.
 *
 * @param id the mainframe's id for this link between the loan and the customer
 * @param customerNumber the co-borrower's customer number
 * @param name the co-borrower's name
 * @param type "Coborrower" or "Guarantor"
 */
public record LoanCoborrower(Long id, String customerNumber, String name, String type) {
}
