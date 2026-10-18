package business.system.dto;

/** Everything shown on the loan summary screen. Fields are null when the mainframe has no value yet. */
public record LoanDetail(
        String loanNumber,
        String borrowerName,
        String loanType,
        String term,
        String compoundType,
        Double amount,
        String paymentFrequency,
        boolean interestOnly,
        Double rate,
        Double nextPaymentAmount,
        String renewDate,
        Double totalInterestPaid,
        Double totalPrincipalPaid,
        String status) {}