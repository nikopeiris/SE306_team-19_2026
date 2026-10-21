package business.system.dto;

/**
 * A co-borrower (or guarantor) on a loan, formatted for the UI layer. {@code id} identifies the
 * link between the loan and the customer; {@code customerNumber} identifies the customer.
 */
public record CoborrowerRow(long id, String customerNumber, String name, String type) {}
