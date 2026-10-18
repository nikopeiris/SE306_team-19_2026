package business.system;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.function.Predicate;

import business.lavsexceptions.LavsException;
import business.system.customer.CustomerAddressService;
import business.system.customer.CustomerEmailService;
import business.system.customer.CustomerEmployerService;
import business.system.customer.CustomerOptions;
import business.system.customer.CustomerPhoneNumberService;
import business.system.dto.CustomerDetails;
import business.system.dto.CustomerDetails.Address;
import business.system.dto.CustomerDetails.Email;
import business.system.dto.CustomerDetails.Employer;
import business.system.dto.CustomerDetails.Personal;
import business.system.dto.CustomerDetails.Phone;
import business.system.dto.CustomerDetails.SaveResult;
import business.validation.ValidationException;
import business.validation.ValidationResult;
import dataaccess.systemadapters.MainframeAdapter;
import mainframe.client.messages.customer.Retrieve;

/**
 * Loads, validates and saves a whole customer (personal details, addresses, phone
 * numbers, emails and employers) so the UI only has to deal with one object.
 */
public class CustomerDetailsService {
    private static final long PAGE_SIZE = 100L;

    /** Stands in for the customer number while validating a form before the customer exists. */
    private static final String NEW_CUSTOMER = "NEW";

    private static final long DEFAULT_ORGANISATION = 1L;

    private final Customer customer;
    private final CustomerAddressService addressService;
    private final CustomerPhoneNumberService phoneService;
    private final CustomerEmailService emailService;
    private final CustomerEmployerService employerService;

    CustomerDetailsService(Customer customer, MainframeAdapter adapter) {
        this.customer = customer;
        this.addressService = new CustomerAddressService(adapter);
        this.phoneService = new CustomerPhoneNumberService(adapter);
        this.emailService = new CustomerEmailService(adapter);
        this.employerService = new CustomerEmployerService(adapter);
    }

    // ------------------------------------------------------------------
    // Loading
    // ------------------------------------------------------------------

    /**
     * Loads a customer with all their details.
     *
     * The mainframe's list operations only return a few fields per item (an address list
     * has no street or postcode, an employer list has no position or salary, and the phone
     * list's number getter is broken), so every item is also retrieved individually.
     */
    public CustomerDetails load(String number) throws IOException, LavsException {
        Retrieve r = customer.retrieveCustomer(number);
        // The organisation is not returned by the mainframe, so it stays unknown here.
        Personal personal = new Personal(
                r.getNumber(), r.getTitle(), r.getFirstName(), r.getFamilyName(),
                r.getDateOfBirth(), r.getNationality(), null, r.getStatus());

        List<Address> addresses = new ArrayList<>();
        var addressItems = addressService.listAddresses(number, 1L, PAGE_SIZE);
        if (addressItems != null) {
            for (var item : addressItems) {
                var full = addressService.retrieveAddress(number, item.getId());
                addresses.add(new Address(
                        item.getId(),
                        full.getType(),
                        full.getOtherType(),
                        full.getLine1(),
                        full.getLine2(),
                        full.getSuburb(),
                        full.getCity(),
                        full.getCountry(),
                        full.getPostCode(),
                        Boolean.TRUE.equals(full.getIsPrimary()),
                        Boolean.TRUE.equals(full.getIsMailing())));
            }
        }

        List<Phone> phones = new ArrayList<>();
        var phoneItems = phoneService.listPhoneNumbers(number, 1L, PAGE_SIZE);
        if (phoneItems != null) {
            for (var item : phoneItems) {
                // The number comes from the retrieve: the list item's getNumber() is broken.
                var full = phoneService.retrievePhoneNumber(number, item.getId());
                phones.add(new Phone(
                        item.getId(),
                        full.getType(),
                        full.getOtherType(),
                        full.getCountryCode(),
                        full.getNumber(),
                        Boolean.TRUE.equals(full.getCanTxt()),
                        Boolean.TRUE.equals(full.getIsPrimary())));
            }
        }

        List<Email> emails = new ArrayList<>();
        var emailItems = emailService.listEmails(number, 1L, PAGE_SIZE);
        if (emailItems != null) {
            for (var item : emailItems) {
                var full = emailService.retrieveEmail(number, item.getId());
                emails.add(new Email(
                        item.getId(),
                        full.getType(),
                        full.getOtherType(),
                        full.getAddress(),
                        Boolean.TRUE.equals(full.getIsPrimary())));
            }
        }

        List<Employer> employers = new ArrayList<>();
        var employerItems = employerService.listEmployers(number, 1L, PAGE_SIZE);
        if (employerItems != null) {
            for (var item : employerItems) {
                var full = employerService.retrieveEmployer(number, item.getId());
                employers.add(new Employer(
                        item.getId(),
                        full.getType(),
                        full.getName(),
                        full.getPosition(),
                        full.getSalary()));
            }
        }

        return new CustomerDetails(personal, addresses, phones, emails, employers);
    }

    // ------------------------------------------------------------------
    // Validation (all rules live here; the UI just shows the message)
    // ------------------------------------------------------------------

    public void validatePersonal(Personal p) throws ValidationException {
        customer.validateCustomerDetails(
                p.dateOfBirth(), p.familyName(), p.firstName(), p.nationality(),
                organisationOf(p), p.title());
    }

    public void validateAddresses(List<Address> addresses) throws ValidationException {
        for (Address a : addresses) {
            CustomerAddressService.validateAddressDetails(
                    a.city(), a.country(), NEW_CUSTOMER, a.line1(), a.otherType(),
                    a.postCode(), a.suburb(), a.type());
        }
        ValidationResult result = new ValidationResult();
        if (count(addresses, Address::primary) > 1) {
            result.addError("primary", "Only one address can be the primary address");
        }
        if (count(addresses, Address::mailing) > 1) {
            result.addError("mailing", "Only one address can be the mailing address");
        }
        result.throwIfInvalid();
    }

    public void validatePhones(List<Phone> phones) throws ValidationException {
        for (Phone p : phones) {
            CustomerPhoneNumberService.validatePhoneNumberDetails(
                    p.countryCode(), NEW_CUSTOMER, p.number(), p.otherType(), p.type());
        }
        ValidationResult result = new ValidationResult();
        if (count(phones, Phone::primary) > 1) {
            result.addError("primary", "Only one phone number can be the primary phone number");
        }
        result.throwIfInvalid();
    }

    public void validateEmails(List<Email> emails) throws ValidationException {
        for (Email e : emails) {
            CustomerEmailService.validateEmailDetails(
                    e.address(), NEW_CUSTOMER, e.otherType(), e.type());
        }
        ValidationResult result = new ValidationResult();
        if (count(emails, Email::primary) > 1) {
            result.addError("primary", "Only one email address can be the primary email");
        }
        result.throwIfInvalid();
    }

    public void validateEmployers(List<Employer> employers) throws ValidationException {
        for (Employer e : employers) {
            CustomerEmployerService.validateEmployerDetails(
                    NEW_CUSTOMER, e.name(), e.position(), e.salary(), e.type());
        }
        ValidationResult result = new ValidationResult();
        if (count(employers, e -> CustomerOptions.EMPLOYER_PRIMARY.equals(e.type())) > 1) {
            result.addError("employer", "A customer can only have one primary employer");
        }
        if (count(employers, e -> CustomerOptions.EMPLOYER_SECONDARY.equals(e.type())) > 1) {
            result.addError("employer", "A customer can only have one secondary employer");
        }
        result.throwIfInvalid();
    }

    public void validateAll(CustomerDetails details) throws ValidationException {
        validatePersonal(details.personal());
        validateEmployers(details.employers());
        validateAddresses(details.addresses());
        validatePhones(details.phones());
        validateEmails(details.emails());
    }

    // ------------------------------------------------------------------
    // Saving
    // ------------------------------------------------------------------

    /**
     * Creates the customer (when {@code original} is null or new) or updates it, then
     * brings the mainframe's addresses, phones, emails and employers in line with
     * {@code edited}: items removed from the form are deleted, new items are added and
     * changed items are updated.
     *
     * Failure to save the customer itself throws. Failures on individual contact items
     * are collected in the returned result so one bad item does not hide the rest.
     */
    public SaveResult save(CustomerDetails original, CustomerDetails edited)
            throws IOException, LavsException {
        validateAll(edited);

        Personal p = edited.personal();
        boolean creating = original == null || original.personal().isNew();
        String number;
        if (creating) {
            number = customer.addCustomer(
                    p.dateOfBirth(), p.familyName(), p.firstName(), p.nationality(),
                    organisationOf(p), p.title()).getNumber();
        } else {
            number = original.personal().number();
            if (!p.equals(original.personal())) {
                customer.updateCustomer(
                        number, p.dateOfBirth(), p.familyName(), p.firstName(),
                        p.nationality(), p.title());
            }
        }

        CustomerDetails before = creating ? CustomerDetails.empty() : original;
        List<String> problems = new ArrayList<>();
        final String customerNumber = number;

        sync("Address", before.addresses(), edited.addresses(), Address::id,
                a -> a.line1() + ", " + a.city(),
                Comparator.comparing((Address a) -> a.primary() || a.mailing()),
                a -> addressService.deleteAddress(customerNumber, a.id()),
                a -> addressService.updateAddress(
                        a.city(), a.country(), customerNumber, a.id(), a.mailing(), a.primary(),
                        a.line1(), blankToNull(a.line2()),
                        otherTypeFor(a.type(), CustomerOptions.ADDRESS_TYPE_OTHER, a.otherType()),
                        a.postCode(), a.suburb(), a.type()),
                a -> addressService.addAddress(
                        a.city(), a.country(), customerNumber, a.mailing(), a.primary(),
                        a.line1(), blankToNull(a.line2()),
                        otherTypeFor(a.type(), CustomerOptions.ADDRESS_TYPE_OTHER, a.otherType()),
                        a.postCode(), a.suburb(), a.type()),
                problems);

        sync("Phone number", before.phones(), edited.phones(), Phone::id,
                p2 -> p2.countryCode() + " " + p2.number(),
                Comparator.comparing(Phone::primary),
                ph -> phoneService.deletePhoneNumber(customerNumber, ph.id()),
                ph -> phoneService.updatePhoneNumber(
                        ph.canTxt(), ph.countryCode(), customerNumber, ph.id(), ph.primary(),
                        ph.number(),
                        otherTypeFor(ph.type(), CustomerOptions.PHONE_TYPE_OTHER, ph.otherType()),
                        ph.type()),
                ph -> phoneService.addPhoneNumber(
                        ph.canTxt(), ph.countryCode(), customerNumber, ph.primary(), ph.number(),
                        otherTypeFor(ph.type(), CustomerOptions.PHONE_TYPE_OTHER, ph.otherType()),
                        ph.type()),
                problems);

        sync("Email", before.emails(), edited.emails(), Email::id,
                Email::address,
                Comparator.comparing(Email::primary),
                e -> emailService.deleteEmail(customerNumber, e.id()),
                e -> emailService.updateEmail(
                        e.address(), customerNumber, e.id(), e.primary(),
                        otherTypeFor(e.type(), CustomerOptions.EMAIL_TYPE_OTHER, e.otherType()),
                        e.type()),
                e -> emailService.addEmail(
                        e.address(), customerNumber, e.primary(),
                        otherTypeFor(e.type(), CustomerOptions.EMAIL_TYPE_OTHER, e.otherType()),
                        e.type()),
                problems);

        sync("Employer", before.employers(), edited.employers(), Employer::id,
                Employer::name,
                Comparator.comparing((Employer e) -> 0),
                e -> employerService.deleteEmployer(customerNumber, e.id()),
                e -> employerService.updateEmployer(
                        customerNumber, e.id(), e.name(), e.position(), e.salary(), e.type()),
                e -> employerService.addEmployer(
                        customerNumber, e.name(), e.position(), e.salary(), e.type()),
                problems);

        return new SaveResult(customerNumber, problems);
    }

    @FunctionalInterface
    private interface Action<T> {
        void run(T item) throws Exception;
    }

    /**
     * Deletes items that disappeared, updates changed ones and adds new ones.
     * Deletes run first and non-primary items are written before primary ones, so the
     * mainframe never sees two primaries while the primary moves from one item to another.
     */
    private <T> void sync(
            String label,
            List<T> before,
            List<T> after,
            Function<T, Long> idOf,
            Function<T, String> describe,
            Comparator<T> order,
            Action<T> delete,
            Action<T> update,
            Action<T> add,
            List<String> problems) {
        Set<Long> keptIds = new HashSet<>();
        for (T item : after) {
            if (idOf.apply(item) != null) {
                keptIds.add(idOf.apply(item));
            }
        }
        Map<Long, T> beforeById = new HashMap<>();
        for (T item : before) {
            if (idOf.apply(item) != null) {
                beforeById.put(idOf.apply(item), item);
            }
        }

        for (T old : before) {
            Long id = idOf.apply(old);
            if (id != null && keptIds.contains(id)) {
                continue;
            }
            if (id == null) {
                problems.add(label + " '" + describe.apply(old) + "' could not be deleted (no id from server)");
                continue;
            }
            attempt(label, "delete", old, delete, describe, problems);
        }

        List<T> ordered = new ArrayList<>(after);
        ordered.sort(order);
        for (T item : ordered) {
            Long id = idOf.apply(item);
            if (id == null) {
                attempt(label, "add", item, add, describe, problems);
            } else if (!item.equals(beforeById.get(id))) {
                attempt(label, "update", item, update, describe, problems);
            }
        }
    }

    private <T> void attempt(String label, String verb, T item, Action<T> action,
            Function<T, String> describe, List<String> problems) {
        try {
            action.run(item);
        } catch (Exception ex) {
            String reason = ex.getMessage() != null ? ex.getMessage() : ex.getClass().getSimpleName();
            problems.add(label + " '" + describe.apply(item) + "' could not be " + verb + "d: " + reason);
        }
    }

    // ------------------------------------------------------------------
    // Small helpers
    // ------------------------------------------------------------------

    private static long organisationOf(Personal p) {
        return p.organisation() == null ? DEFAULT_ORGANISATION : p.organisation();
    }

    private static <T> long count(List<T> items, Predicate<T> test) {
        return items.stream().filter(test).count();
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    /** The "other" description only applies when the type is the "other" type. */
    private static String otherTypeFor(String type, String otherConstant, String otherType) {
        return otherConstant.equals(type) ? blankToNull(otherType) : null;
    }
}