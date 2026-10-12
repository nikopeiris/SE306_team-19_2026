package business;

import business.lavsexceptions.LavsException;
import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

import dataaccess.systemadapters.MainframeAdapter;
import mainframe.client.Connection;
import mainframe.client.HttpConnection;
import business.system.LoginService;
import business.system.SignupService;
import business.system.Customer;

public class Main {
  private String userName;
  private Connection connection = new HttpConnection("https://animating-eraser-arrogance.ngrok-free.dev/");
  private MainframeAdapter adapter = new MainframeAdapter(connection);

  public boolean login(String userName, String password) throws IOException, LavsException {
    LoginService loginService = new LoginService(adapter);
    if (loginService.attemptLogin(userName, password)){
      this.userName = userName;
      return true;
    }
    return false;
  }

  /**
   * Signs up a new user via admin credentials, then logs in as that user.
   *
   * @param login        the login/username for the new user
   * @param name         the display name for the new user
   * @param password     the password for the new user
   * @param organisation the organisation id
   * @param permissions  the permissions level
   * @throws IOException if network or mainframe connection fails
   */
  public boolean signup(String login, String name, String password, long organisation, long permissions)
      throws IOException, LavsException {
    // Create the user on the mainframe (logs in as admin internally)
    SignupService signupService = new SignupService(adapter);
    signupService.signup(login, name, password, organisation, permissions);

    // Log out the admin session so we can log in as the new user
    adapter.logOut();

    // Log in as the newly created user using the existing attemptLogin flow
    LoginService loginService = new LoginService(adapter);
    if (loginService.attemptLogin(login, password)) {
      this.userName = login;
      return true;
    }
    return false;
  }

  public boolean logOut(){
    adapter.logOut();
    userName = null;
    return true;
  }

  public String getUserName(){
    return userName;
  }

  /**
   * Adds a customer via the mainframe adapter after validating all customer fields.
   *
   * @param dateOfBirth the date of birth of the customer
   * @param familyName the family name of the customer
   * @param firstName the first name of the customer
   * @param nationality the nationality of the customer
   * @param organisation the organisation id of the customer
   * @param title the title of the customer
   * @return true if the customer was added successfully
   * @throws IOException if network or mainframe connection fails
   * @throws LavsException if the mainframe rejects the request
   */
  public boolean addCustomer(String dateOfBirth, String familyName, String firstName, String nationality, Long organisation, String title) throws IOException, LavsException {
    Customer customerService = new Customer(adapter);
    customerService.addCustomer(dateOfBirth, familyName, firstName, nationality, organisation, title);
    return true;
  }

  /**
   * Adds an address to an existing customer via the mainframe adapter after validating the address fields.
   * @param city city of the address
   * @param country country of the address
   * @param customer customer number to which the address belongs
   * @param isMailing true if the address is a mailing address
   * @param isPrimary true if the address is the primary address
   * @param line1 first line of the address
   * @param line2 second line of the address
   * @param otherType other type of the address
   * @param postCode postcode of the address
   * @param suburb suburb of the address
   * @param type type of the address
   * @return true if the address was added successfully
   * @throws ValidationException  if a required field or address type is invalid
   * @throws IOException if network or mainframe connection fails
   * @throws LavsException if the mainframe rejects the request
   */
  public boolean addAddress(String city,
            String country,
            String customer,
            boolean isMailing,
            boolean isPrimary,
            String line1,
            String line2,
            String otherType,
            String postCode,
            String suburb,
            String type) throws IOException, LavsException {
    Customer customerService = new Customer(adapter);
    customerService.addAddress(city,  country,  customer,  isMailing,  isPrimary,  line1,  line2,  otherType,  postCode,  suburb,  type);
    return true;
  }
  /**
     * Loads key-value pairs from the .env file in the project root.
     *
     * @return a map of environment variable names to values
     * @throws IOException if the .env file cannot be read
     */
    public static Map<String, String> loadEnv() throws IOException {
        Map<String, String> envMap = new HashMap<>();
        try (BufferedReader reader = new BufferedReader(new FileReader(".env"))) {
            String line;
            while ((line = reader.readLine()) != null) {
                line = line.trim();
                if (line.isEmpty() || line.startsWith("#")) {
                    continue;
                }
                int equalsIndex = line.indexOf('=');
                if (equalsIndex > 0) {
                    String key = line.substring(0, equalsIndex).trim();
                    String value = line.substring(equalsIndex + 1).trim();
                    envMap.put(key, value);
                }
            }
        }
        return envMap;
    }
}
