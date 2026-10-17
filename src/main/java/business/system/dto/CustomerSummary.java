package business.system.dto;

/**
 * Lightweight view of a customer used for the customer list:
 * just the fields the dashboard shows. The primary email is looked up
 * separately (see Customer.findPrimaryEmail) because it needs its own request.
 */
public class CustomerSummary {
    private final String number;
    private final String firstName;
    private final String familyName;

    public CustomerSummary(String number, String firstName, String familyName) {
        this.number = number == null ? "" : number.trim();
        this.firstName = firstName == null ? "" : firstName.trim();
        this.familyName = familyName == null ? "" : familyName.trim();
    }

    /** The customer's unique customer number (the "customer id"). */
    public String getNumber() {
        return number;
    }

    public String getFirstName() {
        return firstName;
    }

    public String getFamilyName() {
        return familyName;
    }

    public String getFullName() {
        return (firstName + " " + familyName).trim();
    }
}