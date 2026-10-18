package business.system.customer;

import java.util.List;

import mainframe.client.messages.customer.AddAddress;
import mainframe.client.messages.customer.AddEmail;
import mainframe.client.messages.customer.AddEmployer;
import mainframe.client.messages.customer.AddPhoneNumber;

/**
 * The choices the UI offers in its dropdowns. They live here so the UI never
 * hard-codes values the mainframe has to accept.
 */
public final class CustomerOptions {
    private CustomerOptions() {
    }

    public static final List<String> TITLES = List.of("Mr", "Ms", "Mrs", "Miss", "Dr", "Prof");
    public static final List<String> NATIONALITIES =
            List.of("New Zealand", "British", "Australian", "American", "Other");
    public static final List<String> COUNTRY_CODES = List.of("+64", "+61", "+1", "+44");

    public static final String ADDRESS_TYPE_OTHER = AddAddress.TYPE_OTHER;
    public static final List<String> ADDRESS_TYPES = List.of(
            AddAddress.TYPE_RESIDENTIAL,
            AddAddress.TYPE_POSTAL,
            AddAddress.TYPE_FORWARDING,
            AddAddress.TYPE_DELEGATED,
            AddAddress.TYPE_OTHER);

    public static final String PHONE_TYPE_OTHER = AddPhoneNumber.TYPE_OTHER;
    public static final List<String> PHONE_TYPES = List.of(
            AddPhoneNumber.TYPE_MOBILE,
            AddPhoneNumber.TYPE_HOME,
            AddPhoneNumber.TYPE_WORK,
            AddPhoneNumber.TYPE_OTHER);

    public static final String EMAIL_TYPE_OTHER = AddEmail.TYPE_OTHER;
    public static final List<String> EMAIL_TYPES = List.of(
            AddEmail.TYPE_PERSONAL,
            AddEmail.TYPE_WORK,
            AddEmail.TYPE_OTHER);

    public static final String EMPLOYER_PRIMARY = AddEmployer.TYPE_PRIMARY;
    public static final String EMPLOYER_SECONDARY = AddEmployer.TYPE_SECONDARY;
}
