package business.system.dto;

import java.util.List;

/**
 * Everything the UI shows and edits for one customer, as plain data.
 * The UI builds one of these from its forms and hands it to the business layer.
 * An item with a null id has not been saved yet.
 */
public record CustomerDetails(
        Personal personal,
        List<Address> addresses,
        List<Phone> phones,
        List<Email> emails,
        List<Employer> employers) {

    public CustomerDetails {
        if (personal == null) {
            throw new IllegalArgumentException("Personal details cannot be null");
        }
        addresses = copy(addresses);
        phones = copy(phones);
        emails = copy(emails);
        employers = copy(employers);
    }

    /** A customer with no contact details or employers (used for a brand new customer). */
    public static CustomerDetails empty() {
        return new CustomerDetails(
                new Personal("", "", "", "", "", "", null, ""),
                List.of(), List.of(), List.of(), List.of());
    }

    private static <T> List<T> copy(List<T> items) {
        return items == null ? List.of() : List.copyOf(items);
    }

    private static String clean(String value) {
        return value == null ? "" : value.trim();
    }

    public record Personal(
            String number,
            String title,
            String firstName,
            String familyName,
            String dateOfBirth,
            String nationality,
            Long organisation,
            String status) {
        public Personal {
            number = clean(number);
            title = clean(title);
            firstName = clean(firstName);
            familyName = clean(familyName);
            dateOfBirth = clean(dateOfBirth);
            nationality = clean(nationality);
            status = clean(status);
        }

        /** True until the customer has been created in the mainframe. */
        public boolean isNew() {
            return number.isEmpty();
        }
    }

    public record Address(
            Long id,
            String type,
            String otherType,
            String line1,
            String line2,
            String suburb,
            String city,
            String country,
            String postCode,
            boolean primary,
            boolean mailing) {
        public Address {
            type = clean(type);
            otherType = clean(otherType);
            line1 = clean(line1);
            line2 = clean(line2);
            suburb = clean(suburb);
            city = clean(city);
            country = clean(country);
            postCode = clean(postCode);
        }
    }

    public record Phone(
            Long id,
            String type,
            String otherType,
            String countryCode,
            String number,
            boolean canTxt,
            boolean primary) {
        public Phone {
            type = clean(type);
            otherType = clean(otherType);
            countryCode = clean(countryCode);
            number = clean(number);
        }
    }

    public record Email(
            Long id,
            String type,
            String otherType,
            String address,
            boolean primary) {
        public Email {
            type = clean(type);
            otherType = clean(otherType);
            address = clean(address);
        }
    }

    public record Employer(
            Long id,
            String type,
            String name,
            String position,
            Double salary) {
        public Employer {
            type = clean(type);
            name = clean(name);
            position = clean(position);
        }
    }

    /**
     * Outcome of saving a customer. The customer itself was saved, but individual
     * contact/employer changes can fail; those are listed in {@code problems}.
     */
    public record SaveResult(String customerNumber, List<String> problems) {
        public SaveResult {
            problems = copy(problems);
        }

        public boolean hasProblems() {
            return !problems.isEmpty();
        }
    }
}
