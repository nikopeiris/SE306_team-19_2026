package business.system;

import java.io.IOException;
import java.lang.reflect.Field;
import java.util.List;

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
import business.validation.ValidationException;
import dataaccess.systemadapters.MainframeAdapter;
import mainframe.client.messages.customer.Create;
import mainframe.client.messages.customer.Retrieve;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.junit.jupiter.api.extension.ExtendWith;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CustomerDetailsServiceTest {
    @Mock Customer customer;
    @Mock MainframeAdapter adapter;
    @Mock CustomerAddressService addresses;
    @Mock CustomerPhoneNumberService phones;
    @Mock CustomerEmailService emails;
    @Mock CustomerEmployerService employers;

    private CustomerDetailsService service;

    @BeforeEach
    void setUp() throws Exception {
        service = new CustomerDetailsService(customer, adapter);
        replace("addressService", addresses);
        replace("phoneService", phones);
        replace("emailService", emails);
        replace("employerService", employers);
    }

    @Test
    void validationRejectsDuplicatePrimaryAndEmployerTypes() {
        Address first = address(1L, true, true);
        Address second = address(2L, true, true);
        assertThrows(ValidationException.class, () -> service.validateAddresses(List.of(first, second)));
        assertThrows(ValidationException.class, () -> service.validatePhones(List.of(phone(1L, true), phone(2L, true))));
        assertThrows(ValidationException.class, () -> service.validateEmails(List.of(email(1L, true), email(2L, true))));
        assertThrows(ValidationException.class, () -> service.validateEmployers(List.of(
                employer(1L, CustomerOptions.EMPLOYER_PRIMARY),
                employer(2L, CustomerOptions.EMPLOYER_PRIMARY))));
        assertDoesNotThrow(() -> service.validateAll(CustomerDetails.empty()));
    }

    @Test
    void validationAcceptsValidPersonalAndContactDetails() {
        assertDoesNotThrow(() -> service.validatePersonal(
                new Personal("C1", "Mr", "Jane", "Smith", "2000-01-01",
                        "New Zealand", 2L, "Active")));
        assertDoesNotThrow(() -> service.validateAddresses(List.of(address(1L, true, true))));
        assertDoesNotThrow(() -> service.validatePhones(List.of(phone(1L, true))));
        assertDoesNotThrow(() -> service.validateEmails(List.of(email(1L, true))));
        assertDoesNotThrow(() -> service.validateEmployers(List.of(
                employer(1L, CustomerOptions.EMPLOYER_PRIMARY),
                employer(2L, CustomerOptions.EMPLOYER_SECONDARY))));
    }

    @Test
    void loadRetrievesEveryListedContactAndMapsFullDetails() throws Exception {
        Retrieve customerRetrieve = mock(Retrieve.class);
        when(customerRetrieve.getNumber()).thenReturn("C1");
        when(customerRetrieve.getTitle()).thenReturn("Mr");
        when(customerRetrieve.getFirstName()).thenReturn("Jane");
        when(customerRetrieve.getFamilyName()).thenReturn("Smith");
        when(customerRetrieve.getDateOfBirth()).thenReturn("2000-01-01");
        when(customerRetrieve.getNationality()).thenReturn("New Zealand");
        when(customerRetrieve.getStatus()).thenReturn("Active");
        when(customer.retrieveCustomer("C1")).thenReturn(customerRetrieve);

        var addressItem = mock(mainframe.client.messages.customerAddress.List.Item.class);
        when(addressItem.getId()).thenReturn(11L);
        var addressRetrieve = mock(mainframe.client.messages.customerAddress.Retrieve.class);
        when(addressRetrieve.getType()).thenReturn("Residential");
        when(addressRetrieve.getOtherType()).thenReturn("");
        when(addressRetrieve.getLine1()).thenReturn("1 Main");
        when(addressRetrieve.getLine2()).thenReturn("Unit");
        when(addressRetrieve.getSuburb()).thenReturn("Suburb");
        when(addressRetrieve.getCity()).thenReturn("City");
        when(addressRetrieve.getCountry()).thenReturn("NZ");
        when(addressRetrieve.getPostCode()).thenReturn("6011");
        when(addressRetrieve.getIsPrimary()).thenReturn(true);
        when(addressRetrieve.getIsMailing()).thenReturn(false);
        when(addresses.listAddresses("C1", 1L, 100L)).thenReturn(
                new mainframe.client.messages.customerAddress.List.Item[] {addressItem});
        when(addresses.retrieveAddress("C1", 11L)).thenReturn(addressRetrieve);

        var phoneItem = mock(mainframe.client.messages.customerPhoneNumber.List.Item.class);
        when(phoneItem.getId()).thenReturn(12L);
        var phoneRetrieve = mock(mainframe.client.messages.customerPhoneNumber.Retrieve.class);
        when(phoneRetrieve.getType()).thenReturn("Mobile");
        when(phoneRetrieve.getOtherType()).thenReturn("");
        when(phoneRetrieve.getCountryCode()).thenReturn("+64");
        when(phoneRetrieve.getNumber()).thenReturn("123");
        when(phoneRetrieve.getCanTxt()).thenReturn(true);
        when(phoneRetrieve.getIsPrimary()).thenReturn(true);
        when(phones.listPhoneNumbers("C1", 1L, 100L)).thenReturn(
                new mainframe.client.messages.customerPhoneNumber.List.Item[] {phoneItem});
        when(phones.retrievePhoneNumber("C1", 12L)).thenReturn(phoneRetrieve);

        var emailItem = mock(mainframe.client.messages.customerEmail.List.Item.class);
        when(emailItem.getId()).thenReturn(13L);
        var emailRetrieve = mock(mainframe.client.messages.customerEmail.Retrieve.class);
        when(emailRetrieve.getType()).thenReturn("Personal");
        when(emailRetrieve.getOtherType()).thenReturn("");
        when(emailRetrieve.getAddress()).thenReturn("jane@example.com");
        when(emailRetrieve.getIsPrimary()).thenReturn(false);
        when(emails.listEmails("C1", 1L, 100L)).thenReturn(
                new mainframe.client.messages.customerEmail.List.Item[] {emailItem});
        when(emails.retrieveEmail("C1", 13L)).thenReturn(emailRetrieve);

        var employerItem = mock(mainframe.client.messages.customerEmployer.List.Item.class);
        when(employerItem.getId()).thenReturn(14L);
        var employerRetrieve = mock(mainframe.client.messages.customerEmployer.Retrieve.class);
        when(employerRetrieve.getType()).thenReturn(CustomerOptions.EMPLOYER_PRIMARY);
        when(employerRetrieve.getName()).thenReturn("Acme");
        when(employerRetrieve.getPosition()).thenReturn("Engineer");
        when(employerRetrieve.getSalary()).thenReturn(100.0);
        when(employers.listEmployers("C1", 1L, 100L)).thenReturn(
                new mainframe.client.messages.customerEmployer.List.Item[] {employerItem});
        when(employers.retrieveEmployer("C1", 14L)).thenReturn(employerRetrieve);

        CustomerDetails result = service.load("C1");

        assertEquals("C1", result.personal().number());
        assertEquals("Jane", result.personal().firstName());
        assertEquals(11L, result.addresses().get(0).id());
        assertEquals("1 Main", result.addresses().get(0).line1());
        assertEquals("123", result.phones().get(0).number());
        assertEquals("jane@example.com", result.emails().get(0).address());
        assertEquals("Engineer", result.employers().get(0).position());
    }

    @Test
    void loadTreatsNullContactListsAsEmpty() throws Exception {
        Retrieve retrieved = mock(Retrieve.class);
        when(retrieved.getNumber()).thenReturn("C1");
        when(customer.retrieveCustomer("C1")).thenReturn(retrieved);
        when(addresses.listAddresses(anyString(), anyLong(), anyLong())).thenReturn(null);
        when(phones.listPhoneNumbers(anyString(), anyLong(), anyLong())).thenReturn(null);
        when(emails.listEmails(anyString(), anyLong(), anyLong())).thenReturn(null);
        when(employers.listEmployers(anyString(), anyLong(), anyLong())).thenReturn(null);

        CustomerDetails result = service.load("C1");

        assertTrue(result.addresses().isEmpty());
        assertTrue(result.phones().isEmpty());
        assertTrue(result.emails().isEmpty());
        assertTrue(result.employers().isEmpty());
    }

    @Test
    void saveCreatesNewCustomerAndUsesDefaultOrganisation() throws Exception {
        Create created = mock(Create.class);
        when(created.getNumber()).thenReturn("C100");
        when(customer.addCustomer(anyString(), anyString(), anyString(), anyString(), anyLong(), anyString()))
                .thenReturn(created);

        CustomerDetails edited = new CustomerDetails(
                new Personal("", " Mr ", " Jane ", " Smith ", "", "", null, ""),
                List.of(), List.of(), List.of(), List.of());

        CustomerDetails.SaveResult result = service.save(null, edited);

        assertEquals("C100", result.customerNumber());
        assertFalse(result.hasProblems());
        verify(customer).addCustomer("", "Smith", "Jane", "", 1L, "Mr");
        verifyNoInteractions(addresses, phones, emails, employers);
    }

    @Test
    void saveUpdatesChangedPersonalDetailsAndCollectsContactFailures() throws Exception {
        Personal originalPersonal = new Personal("C1", "Mr", "Jane", "Smith", "", "", 1L, "");
        Personal editedPersonal = new Personal("C1", "Ms", "Jane", "Smith", "", "", 1L, "");
        CustomerDetails original = new CustomerDetails(originalPersonal,
                List.of(address(7L, false, false)), List.of(), List.of(), List.of());
        CustomerDetails edited = new CustomerDetails(editedPersonal,
                List.of(address(null, false, false)), List.of(), List.of(), List.of());
        doThrow(new IOException("offline")).when(addresses).deleteAddress("C1", 7L);
        doThrow(new LavsException(new mainframe.client.Status(9, "bad", 1)))
                .when(addresses).addAddress(anyString(), anyString(), eq("C1"), anyBoolean(),
                        anyBoolean(), anyString(), any(), any(), anyString(), anyString(), anyString());

        CustomerDetails.SaveResult result = service.save(original, edited);

        verify(customer).updateCustomer("C1", "", "Smith", "Jane", "", "Ms");
        assertEquals("C1", result.customerNumber());
        assertEquals(2, result.problems().size());
        assertTrue(result.problems().get(0).contains("could not be deleted"));
        assertTrue(result.problems().get(1).contains("could not be add"));
    }

    @Test
    void saveSynchronisesSuccessfulAddsUpdatesAndDeletesForAllContactTypes() throws Exception {
        Personal personal = new Personal("C1", "Mr", "Jane", "Smith", "", "", 1L, "");
        CustomerDetails original = new CustomerDetails(personal,
                List.of(address(1L, false, false), address(2L, false, false)),
                List.of(phone(3L, false), phone(4L, false)),
                List.of(email(5L, false), email(6L, false)),
                List.of(employer(7L, CustomerOptions.EMPLOYER_PRIMARY),
                        employer(8L, CustomerOptions.EMPLOYER_SECONDARY)));
        CustomerDetails edited = new CustomerDetails(personal,
                List.of(address(1L, true, false), address(null, false, false)),
                List.of(phone(3L, true), phone(null, false)),
                List.of(email(5L, true), email(null, false)),
                List.of(employer(7L, CustomerOptions.EMPLOYER_PRIMARY),
                        employer(null, CustomerOptions.EMPLOYER_SECONDARY)));

        CustomerDetails.SaveResult result = service.save(original, edited);

        assertEquals("C1", result.customerNumber());
        assertFalse(result.hasProblems());
        verify(addresses).deleteAddress("C1", 2L);
        verify(addresses).updateAddress(anyString(), anyString(), eq("C1"), eq(1L),
                eq(false), eq(true), anyString(), any(), any(), anyString(), anyString(), anyString());
        verify(addresses).addAddress(anyString(), anyString(), eq("C1"), anyBoolean(),
                anyBoolean(), anyString(), any(), any(), anyString(), anyString(), anyString());
        verify(phones).deletePhoneNumber("C1", 4L);
        verify(phones).updatePhoneNumber(anyBoolean(), anyString(), eq("C1"), eq(3L),
                anyBoolean(), anyString(), any(), anyString());
        verify(phones).addPhoneNumber(anyBoolean(), anyString(), eq("C1"), anyBoolean(),
                anyString(), any(), anyString());
        verify(emails).deleteEmail("C1", 6L);
        verify(emails).updateEmail(anyString(), eq("C1"), eq(5L), anyBoolean(), any(), anyString());
        verify(emails).addEmail(anyString(), eq("C1"), anyBoolean(), any(), anyString());
        verify(employers).deleteEmployer("C1", 8L);
        verify(employers).addEmployer(eq("C1"), anyString(), anyString(), anyDouble(), anyString());
    }

    @Test
    void saveReportsFailuresFromUpdateAndUsesExceptionTypeWhenMessageMissing() throws Exception {
        Personal personal = new Personal("C1", "Mr", "Jane", "Smith", "", "", 1L, "");
        CustomerDetails original = new CustomerDetails(personal,
                List.of(address(1L, false, false)), List.of(), List.of(), List.of());
        CustomerDetails edited = new CustomerDetails(personal,
                List.of(address(1L, true, false)), List.of(), List.of(), List.of());
        doThrow(new RuntimeException()).when(addresses).updateAddress(anyString(), anyString(),
                eq("C1"), eq(1L), anyBoolean(), anyBoolean(), anyString(), any(), any(),
                anyString(), anyString(), anyString());

        CustomerDetails.SaveResult result = service.save(original, edited);

        assertTrue(result.hasProblems());
        assertTrue(result.problems().get(0).contains("RuntimeException"));
    }

    private Address address(Long id, boolean primary, boolean mailing) {
        return new Address(id, "Residential", "", "1 Main", "", "City", "NZ", "NZ",
                "6011", primary, mailing);
    }

    private Phone phone(Long id, boolean primary) {
        return new Phone(id, "Mobile", "", "+64", "123", false, primary);
    }

    private Email email(Long id, boolean primary) {
        return new Email(id, "Personal", "", "a@example.com", primary);
    }

    private Employer employer(Long id, String type) {
        return new Employer(id, type, "Acme", "Role", 1.0);
    }

    private void replace(String name, Object value) throws Exception {
        Field field = CustomerDetailsService.class.getDeclaredField(name);
        field.setAccessible(true);
        field.set(service, value);
    }
}
