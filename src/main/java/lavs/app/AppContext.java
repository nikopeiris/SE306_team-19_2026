package lavs.app;

import lavs.service.AuthService;
import lavs.ui.SceneRouter;
import mainframe.client.Connection;

// holds the shared services so controllers can be given what they need
public final class AppContext {
    private final Connection _connection;
    private final AuthService _authService;
    private SceneRouter _router;

    public AppContext(Connection connection) {
        _connection = connection;
        _authService = new AuthService(connection);
    }

    public Connection getConnection() {
        return _connection;
    }

    public AuthService getAuthService() {
        return _authService;
    }

    public SceneRouter getRouter() {
        return _router;
    }

    void setRouter(SceneRouter router) {
        _router = router;
    }
}
