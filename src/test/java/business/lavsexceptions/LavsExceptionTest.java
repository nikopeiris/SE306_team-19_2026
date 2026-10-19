package business.lavsexceptions;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

import mainframe.client.Status;
import mainframe.client.StatusMessage;

class LavsExceptionTest {
    @Test
    void constructorsExposeMessageCauseAndErrorCode() {
        LavsException plain = new LavsException("failed");
        Throwable cause = new IllegalStateException("cause");
        LavsException caused = new LavsException("failed", cause);
        LavsException remote = new LavsException(new Status(500, "rejected", 7));

        assertEquals("failed", plain.getMessage());
        assertNull(plain.getErrorCode());
        assertFalse(plain.isNetworkFailure());
        assertSame(cause, caused.getCause());
        assertEquals(Integer.valueOf(500), remote.getErrorCode());
        assertFalse(remote.isNetworkFailure());
    }

    @Test
    void networkFailureStatusIsDetected() {
        Status status = new Status(StatusMessage.NETWORK_FAILURE_UNAVAILABLE.getCode(),
                "offline", StatusMessage.NETWORK_FAILURE_UNAVAILABLE.getCode());
        LavsException exception = new LavsException(status);

        assertTrue(exception.isNetworkFailure());
    }
}
