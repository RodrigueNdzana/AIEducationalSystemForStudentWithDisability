package com.aiadaptive.exception;

/** A controlled error that maps to an HTTP status and a JSON body. */
public class ApiException extends RuntimeException {
    private final int status;
    private final transient Object details;

    public ApiException(int status, String message) { this(status, message, null); }

    public ApiException(int status, String message, Object details) {
        super(message);
        this.status = status;
        this.details = details;
    }

    public int getStatus() { return status; }
    public Object getDetails() { return details; }

    public static ApiException notFound(String what) { return new ApiException(404, what + " not found"); }
    public static ApiException badRequest(String msg) { return new ApiException(400, msg); }
    public static ApiException forbidden(String msg) { return new ApiException(403, msg); }
    public static ApiException unauthorized(String msg) { return new ApiException(401, msg); }
}
