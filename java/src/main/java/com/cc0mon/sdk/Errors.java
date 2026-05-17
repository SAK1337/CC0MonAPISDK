package com.cc0mon.sdk;

/**
 * Exception hierarchy for the cc0mon SDK.
 *
 * <p>All SDK exceptions inherit from {@link CC0MonException} so callers can
 * catch the family with a single {@code catch} clause.
 */
public final class Errors {

    private Errors() {}

    /** Base type for every checked failure produced by the SDK. */
    public static class CC0MonException extends RuntimeException {
        public CC0MonException(String message) { super(message); }
        public CC0MonException(String message, Throwable cause) { super(message, cause); }
    }

    /** Raised before any HTTP call when arguments fail local validation. */
    public static class ValidationException extends CC0MonException {
        public ValidationException(String message) { super(message); }
    }

    /** DNS, TCP, TLS, timeout, or other transport-level failure. */
    public static class NetworkException extends CC0MonException {
        public NetworkException(String message, Throwable cause) { super(message, cause); }
    }

    /** An HTTP response was received with status &gt;= 400. */
    public static class ApiException extends CC0MonException {
        private final int statusCode;
        private final String body;
        public ApiException(int statusCode, String body) {
            super("HTTP " + statusCode + ": " + truncate(body));
            this.statusCode = statusCode;
            this.body = body;
        }
        public int getStatusCode() { return statusCode; }
        public String getBody() { return body; }
        private static String truncate(String b) {
            if (b == null) return "";
            return b.length() > 200 ? b.substring(0, 200) : b;
        }
    }

    /** HTTP 429. Carries the parsed {@code Retry-After} value when present. */
    public static class RateLimitException extends ApiException {
        private final Double retryAfterSeconds;
        public RateLimitException(int statusCode, String body, Double retryAfterSeconds) {
            super(statusCode, body);
            this.retryAfterSeconds = retryAfterSeconds;
        }
        public Double getRetryAfterSeconds() { return retryAfterSeconds; }
    }

    /** HTTP 4xx other than 429. */
    public static class ClientApiException extends ApiException {
        public ClientApiException(int statusCode, String body) { super(statusCode, body); }
    }

    /** HTTP 5xx. */
    public static class ServerApiException extends ApiException {
        public ServerApiException(int statusCode, String body) { super(statusCode, body); }
    }
}
