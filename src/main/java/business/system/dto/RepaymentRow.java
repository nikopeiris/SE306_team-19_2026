package business.system.dto;

/** One line of a loan's repayment schedule. */
public record RepaymentRow(
        Long paymentNumber,
        String date,
        Double amountPaid,
        Double principal,
        Double interest,
        Double remainingBalance) {}