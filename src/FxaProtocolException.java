import java.io.IOException;

public class FxaProtocolException extends IOException {

    FxaProtocolException(String message) {
        super(message);
    }

    FxaProtocolException(Exception e) {
        super(e);
    }
}