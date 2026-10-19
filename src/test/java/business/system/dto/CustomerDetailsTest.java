package business.system.dto;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class CustomerDetailsTest {

    @Test
    void emptyContainsNewPersonalDetailsAndIndependentLists() {
        CustomerDetails details = CustomerDetails.empty();

        assertTrue(details.personal().isNew());
        assertEquals("", details.personal().firstName());
        assertTrue(details.addresses().isEmpty());
        assertTrue(details.phones().isEmpty());
        assertTrue(details.emails().isEmpty());
        assertTrue(details.employers().isEmpty());
    }

    @Test
    void constructorNormalisesPersonalAndNestedValues() {
        CustomerDetails.Personal personal = new CustomerDetails.Personal(
                " C1 ", " Mr ", " Jane ", " Smith ", " 2000-01-01 ",
                " NZ ", 4L, " Active ");
        CustomerDetails.Address address = new CustomerDetails.Address(
                1L, " Home ", " Other ", " 1 Main ", null, " Suburb ",
                " City ", " NZ ", " 6011 ", true, false);
        CustomerDetails.Phone phone = new CustomerDetails.Phone(
                2L, " Mobile ", " Other ", " +64 ", " 123 ", true, true);
        CustomerDetails.Email email = new CustomerDetails.Email(
                3L, " Personal ", " Other ", " jane@example.com ", false);
        CustomerDetails.Employer employer = new CustomerDetails.Employer(
                4L, " Primary ", " Acme ", " Engineer ", 100.0);

        CustomerDetails details = new CustomerDetails(personal, List.of(address),
                List.of(phone), List.of(email), List.of(employer));

        assertEquals("C1", details.personal().number());
        assertEquals("Jane", details.personal().firstName());
        assertEquals("Smith", details.personal().familyName());
        assertEquals(4L, details.personal().organisation());
        assertEquals("1 Main", details.addresses().get(0).line1());
        assertEquals("", details.addresses().get(0).line2());
        assertEquals("+64", details.phones().get(0).countryCode());
        assertEquals("jane@example.com", details.emails().get(0).address());
        assertEquals("Acme", details.employers().get(0).name());
        assertFalse(details.personal().isNew());
    }

    @Test
    void nullPersonalIsRejectedAndNullListsBecomeEmpty() {
        assertThrows(IllegalArgumentException.class,
                () -> new CustomerDetails(null, null, null, null, null));

        CustomerDetails details = new CustomerDetails(
                new CustomerDetails.Personal(null, null, null, null, null, null, null, null),
                null, null, null, null);
        assertEquals(List.of(), details.addresses());
        assertEquals(List.of(), details.phones());
        assertEquals(List.of(), details.emails());
        assertEquals(List.of(), details.employers());
    }

    @Test
    void listsAndSaveProblemsAreDefensivelyCopied() {
        List<CustomerDetails.Address> addresses = new ArrayList<>();
        addresses.add(new CustomerDetails.Address(null, "Home", null, "1", null,
                "S", "C", "NZ", "1", false, false));
        CustomerDetails details = new CustomerDetails(
                new CustomerDetails.Personal("C1", null, "Jane", "Smith",
                        null, null, null, null), addresses, null, null, null);
        addresses.clear();

        assertEquals(1, details.addresses().size());
        assertThrows(UnsupportedOperationException.class, () -> details.addresses().clear());

        List<String> problems = new ArrayList<>(List.of("failed"));
        CustomerDetails.SaveResult result = new CustomerDetails.SaveResult("C1", problems);
        problems.clear();
        assertTrue(result.hasProblems());
        assertEquals(List.of("failed"), result.problems());
        assertThrows(UnsupportedOperationException.class, () -> result.problems().clear());
    }
}
