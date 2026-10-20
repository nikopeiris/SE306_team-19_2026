package business.system.dto;

/** A payment recorded against a loan, formatted for the UI layer. */
public record PaymentRow(long id, Double amountPaid, String date) {}
