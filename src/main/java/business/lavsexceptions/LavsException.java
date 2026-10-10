package business.lavsexceptions;

import mainframe.client.Status;
import mainframe.client.StatusMessage;

// a failure reported by the business logic layer, suitable for showing to the user
public class LavsException extends Exception {
    private final Integer _errorCode;

    public LavsException(String message) {
        super(message);
        _errorCode = null;
    }

    public LavsException(String message, Throwable cause) {
        super(message, cause);
        _errorCode = null;
    }

    // creates an exception from an unsuccessful mainframe status
    public LavsException(Status status) {
        super(status.getErrorMessage());
        _errorCode = status.getErrorCode();
    }

    // gets the mainframe error code, or null if the failure did not come from the mainframe
    public Integer getErrorCode() {
        return _errorCode;
    }

    // true when the mainframe could not be reached, so the operation can be retried later
    public boolean isNetworkFailure() {
        return _errorCode != null
                && _errorCode.equals(StatusMessage.NETWORK_FAILURE_UNAVAILABLE.getCode());
    }
}
