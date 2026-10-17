package lavs.ui;

import java.util.ArrayList;
import java.util.List;

import business.system.Customer;
import business.system.LoginService;

public class CustomerContext {
    private LoginService loginService;
    private Customer customerService;

    // Data Transfer Objects shared across sub-controllers
    public record AddressDto(String type, String line1, String line2, String suburb, String city, String country, String postCode, boolean isPrimary, boolean isMailing) {}
    public record PhoneDto(String type, String countryCode, String number, boolean isPrimary) {}
    public record EmailDto(String type, String email, boolean isPrimary) {}

    private final List<AddressDto> savedAddresses = new ArrayList<>();
    private final List<PhoneDto> savedPhones = new ArrayList<>();
    private final List<EmailDto> savedEmails = new ArrayList<>();

    public void setServices(LoginService loginService, Customer customerService) {
        this.loginService = loginService;
        this.customerService = customerService;
    }

    public LoginService getLoginService() { return loginService; }
    public Customer getCustomerService() { return customerService; }

    public List<AddressDto> getSavedAddresses() { return savedAddresses; }
    public List<PhoneDto> getSavedPhones() { return savedPhones; }
    public List<EmailDto> getSavedEmails() { return savedEmails; }
}