package business.system.dto;

/** One loan as shown in the loan tab's table. Any field may be null if the mainframe sent nothing usable. */
public record LoanRow(
        String loanNumber,
        String type,
        Double amount,
        String dateCreated,
        String status) {}