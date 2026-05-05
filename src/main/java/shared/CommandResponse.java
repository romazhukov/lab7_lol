package shared;

import java.io.Serializable;

public class CommandResponse implements Serializable {
    private static final long serialVersionUID = 1L;

    private final boolean success;
    private final String message;
    private final Object data;

    public CommandResponse(boolean success, String message, Object data) {
        this.success = success;
        this.message = message;
        this.data = data;
    }

    public static CommandResponse ok(String message) {
        return new CommandResponse(true, message, null);
    }

    public static CommandResponse ok(String message, Object data) {
        return new CommandResponse(true, message, data);
    }

    public static CommandResponse error(String message) {
        return new CommandResponse(false, message, null);
    }

    public boolean isSuccess() {
        return success;
    }

    public String getMessage() {
        return message;
    }

    public Object getData() {
        return data;
    }
}