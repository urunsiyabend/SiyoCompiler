package codeanalysis;

import java.util.concurrent.LinkedBlockingQueue;

/**
 * Runtime representation of a Siyo actor.
 * Owns a virtual thread, a mailbox, and a state (SiyoStruct).
 * All method calls are serialized through the mailbox.
 */
public class SiyoActor {
    private final SiyoStruct _state;
    private final String _actorTypeName;
    private final LinkedBlockingQueue<ActorMessage> _mailbox = new LinkedBlockingQueue<>();
    private Thread _thread;

    public SiyoActor(SiyoStruct state, String actorTypeName) {
        _state = state;
        _actorTypeName = actorTypeName;
    }

    public SiyoStruct getState() { return _state; }
    public String getActorTypeName() { return _actorTypeName; }
    public LinkedBlockingQueue<ActorMessage> getMailbox() { return _mailbox; }

    public void setThread(Thread thread) { _thread = thread; }
    public Thread getThread() { return _thread; }

    /**
     * Send a message and wait for reply (synchronous).
     */
    public Object call(String methodName, Object[] args) {
        LinkedBlockingQueue<Object> replyChannel = new LinkedBlockingQueue<>(1);
        try {
            _mailbox.put(new ActorMessage(methodName, args, replyChannel));
            Object result = replyChannel.take();
            if (result instanceof ActorError err) {
                throw new RuntimeException(err.getMessage());
            }
            return result;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("Actor call interrupted");
        }
    }

    /**
     * Send a message without waiting for reply (fire-and-forget).
     */
    public void send(String methodName, Object[] args) {
        try {
            _mailbox.put(new ActorMessage(methodName, args, null));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    @Override
    public String toString() {
        return "actor<" + _actorTypeName + ">";
    }

    // Message type
    public static class ActorMessage {
        public final String methodName;
        public final Object[] args;
        public final LinkedBlockingQueue<Object> replyChannel; // null for fire-and-forget

        public ActorMessage(String methodName, Object[] args, LinkedBlockingQueue<Object> replyChannel) {
            this.methodName = methodName;
            this.args = args;
            this.replyChannel = replyChannel;
        }
    }

    // Error wrapper for propagation
    public static class ActorError {
        private final String _message;
        public ActorError(String message) { _message = message; }
        public String getMessage() { return _message; }
    }
}
