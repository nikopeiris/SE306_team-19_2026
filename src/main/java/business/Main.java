package business;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

import business.lavsexceptions.LavsException;
import mainframe.client.Status;

public class Main {
  /**
   * Validates the status of a mainframe response.
   * @param status the status to validate
   * @throws LavsException if the status is not successful
   */
  public static boolean isValidStatus(Status status) throws LavsException {
        if (!status.getWasSuccessful()) {
          throw new LavsException(status);
        }
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