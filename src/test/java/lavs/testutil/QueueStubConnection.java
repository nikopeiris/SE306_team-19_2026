package lavs.testutil;

import mainframe.client.Connection;
import mainframe.client.MessageRequest;
import mainframe.client.MessageResponse;
import mainframe.client.Status;
import mainframe.client.StatusMessage;
import mainframe.client.messages.Login;

import java.io.IOException;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

// a fake mainframe connection for unit tests: returns queued responses in order and
// records every request sent, so multi-step service operations can be tested offline
public class QueueStubConnection implements Connection {
    private final Deque<MessageResponse> _responses = new ArrayDeque<>();
    private final List<MessageRequest> _sentRequests = new ArrayList<>();
    private boolean _isConnected = true;
    private boolean _loggedOut = false;

    // queues a successful response containing the given values
    public QueueStubConnection respondWith(Map<String, String> values) {
        _responses.add(new MessageResponse(new Status(_responses.size() + 1), new HashMap<>(values)));
        return this;
    }

    // queues a failure response from the mainframe
    public QueueStubConnection respondWithError(int errorCode, String errorMessage) {
        _responses.add(new MessageResponse(
                new Status(errorCode, errorMessage, _responses.size() + 1), new HashMap<>()));
        return this;
    }

    // queues a network dropout
    public QueueStubConnection respondWithNetworkFailure() {
        _responses.add(StatusMessage.NETWORK_FAILURE_UNAVAILABLE.generateMessageResponse(_responses.size() + 1));
        return this;
    }

    // queues a successful login response
    public QueueStubConnection respondWithLogin(String userName) {
        return respondWith(Map.of("token", "test-token", "userName", userName, "lastNumber", "1"));
    }

    public void setIsConnected(boolean isConnected) {
        _isConnected = isConnected;
    }

    public List<MessageRequest> getSentRequests() {
        return _sentRequests;
    }

    public MessageRequest getLastRequest() {
        return _sentRequests.isEmpty() ? null : _sentRequests.get(_sentRequests.size() - 1);
    }

    public boolean isLoggedOut() {
        return _loggedOut;
    }

    @Override
    public MessageResponse send(MessageRequest request) {
        _sentRequests.add(request);
        if (_responses.isEmpty()) {
            throw new IllegalStateException("No response queued for request " + request.getRequestType());
        }
        return _responses.poll();
    }

    @Override
    public Status Login(String username, String password) throws IOException {
        Login message = new Login();
        message.setUserName(username);
        message.setpassword(password);
        Status status = message.send(this);
        if (status.getWasSuccessful()) {
            _loggedOut = false;
        }
        return status;
    }

    @Override
    public void Logout() {
        _loggedOut = true;
    }

    @Override
    public boolean isConnected() {
        return _isConnected;
    }

    @Override
    public void close() {
        // nothing to clean up
    }
}
