package planesim.core.server;

/** Signals a client-supplied request body failed validation; mapped to HTTP 400 by {@link AbstractJsonHandler}. */
final class BadRequestException extends RuntimeException {
    BadRequestException(String message) {
        super(message);
    }
}
