package codeanalysis;

/**
 * A Siyo-raised error. Carries an arbitrary payload rather than only text, so
 * {@code throw NotFound(404)} can be matched on by variant in a catch block
 * while {@code error("boom")} keeps carrying its message.
 *
 * <p>The payload is what {@code catch e} binds. A Java exception crossing the
 * same boundary has no payload, so {@link SiyoRuntime#errorPayload} falls back
 * to the message and then to the exception type — a message-less Java exception
 * used to bind null.
 *
 * @see <a href="https://github.com/urunsiyabend">GitHub Profile</a>
 * @author Siyabend Urun
 */
public class SiyoThrow extends RuntimeException {
    private final Object _payload;

    public SiyoThrow(Object payload) {
        super(payload == null ? "null" : payload.toString());
        _payload = payload;
    }

    /** The value that was thrown. */
    public Object getPayload() {
        return _payload;
    }
}
