package lavs.ui;

import java.util.ArrayList;
import java.util.List;

import business.system.Customer;
import business.system.LoginService;
import business.system.customer.CustomerEmailService;
import business.system.dto.CustomerDetails;
import business.system.dto.CustomerDetails.Address;
import business.system.dto.CustomerDetails.Email;
import business.system.dto.CustomerDetails.Phone;

/**
 * State shared by the customer screen's tabs: the business services, the customer
 * as last loaded from the mainframe, and the contact lists being edited.
 */
public class CustomerContext {

    /** What the tabs can ask the customer screen to do. */
    public interface Navigation {
        void showPersonalInfo();
        void showContactInfo();
        void save();
        void cancel();
    }

    private LoginService loginService;
    private Customer customerService;
    private CustomerEmailService emailService;
    private Navigation navigation;

    /** The customer as last loaded (or saved); null for a brand new customer. */
    private CustomerDetails original;

    private final List<Address> savedAddresses = new ArrayList<>();
    private final List<Phone> savedPhones = new ArrayList<>();
    private final List<Email> savedEmails = new ArrayList<>();

    public void setServices(LoginService loginService, Customer customerService,
            CustomerEmailService emailService) {
        this.loginService = loginService;
        this.customerService = customerService;
        this.emailService = emailService;
    }

    public void setNavigation(Navigation navigation) { this.navigation = navigation; }
    public Navigation getNavigation() { return navigation; }

    /** Replaces everything on screen with a customer loaded from the mainframe. */
    public void load(CustomerDetails details) {
        original = details;
        savedAddresses.clear();
        savedAddresses.addAll(details.addresses());
        savedPhones.clear();
        savedPhones.addAll(details.phones());
        savedEmails.clear();
        savedEmails.addAll(details.emails());
    }

    public CustomerDetails getOriginal() { return original; }

    public LoginService getLoginService() { return loginService; }
    public Customer getCustomerService() { return customerService; }
    public CustomerEmailService getEmailService() { return emailService; }

    public List<Address> getSavedAddresses() { return savedAddresses; }
    public List<Phone> getSavedPhones() { return savedPhones; }
    public List<Email> getSavedEmails() { return savedEmails; }
}