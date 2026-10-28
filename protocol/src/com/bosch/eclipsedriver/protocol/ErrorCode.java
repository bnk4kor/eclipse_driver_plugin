package com.bosch.eclipsedriver.protocol;

public final class ErrorCode {
    public static final String NOT_FOUND = "not-found";
    public static final String AMBIGUOUS_MATCH = "ambiguous-match";
    public static final String DISABLED = "disabled";
    public static final String TIMEOUT = "timeout";
    public static final String STALE_REF = "stale-ref";
    public static final String UI_THREAD_BLOCKED = "ui-thread-blocked";
    public static final String NOT_CONNECTED = "not-connected";
    public static final String UNKNOWN_TOOL = "unknown-tool";
    public static final String INVALID_ARGS = "invalid-args";
    public static final String INSTALL_NOT_WRITABLE = "install-not-writable";
    public static final String RESTART_NOT_CONFIRMED = "restart-not-confirmed";
    public static final String LAUNCH_FAILED = "launch-failed";
    public static final String INTERNAL_ERROR = "internal-error";

    private ErrorCode() {
    }
}
