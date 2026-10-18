package business.system.dto;

/** One loan as shown in the dashboard's loan list. */
public final class LoanSummary {
    private final String loanNumber;
    private final String customerNumber;
    private final String customerName;
    private final Double principal;   // null if the mainframe sent nothing usable
    private final String startDate;
    private final String status;

    public LoanSummary(String loanNumber, String customerNumber, String customerName,
                       Double principal, String startDate, String status) {
        this.loanNumber = loanNumber;
        this.customerNumber = customerNumber;
        this.customerName = customerName;
        this.principal = principal;
        this.startDate = startDate;
        this.status = status;
    }

    public String getLoanNumber()     { return loanNumber; }
    public String getCustomerNumber() { return customerNumber; }
    public String getCustomerName()   { return customerName; }
    public Double getPrincipal()      { return principal; }
    public String getStartDate()      { return startDate; }
    public String getStatus()         { return status; }
}