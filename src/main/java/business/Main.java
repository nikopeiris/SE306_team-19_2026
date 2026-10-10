package business;

import dataaccess.MainframeAuthAdapter;

import java.io.IOException;

import dataaccess.AuthAdapter;
import mainframe.client.Connection;
import mainframe.client.HttpConnection;
import business.system.LoginService;

public class Main {
  private String userName;
  private Connection connection = new HttpConnection("https://localhost:8080");

  public boolean login(String userName, String password) throws IOException {
    AuthAdapter adapter = new MainframeAuthAdapter(connection);
    LoginService loginService = new LoginService(adapter);
    if (loginService.attemptLogin(userName, password)){
      this.userName = userName;
      return true;
    }
    return false;
  }

  public String getUserName(){
    return userName;
  }
}
