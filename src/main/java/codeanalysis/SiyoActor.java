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
    private volatile boolean _stopped = false;

    public SiyoActor(SiyoStruct state, String actorTypeName) {
        _state = state;
        _actorTypeName = actorTypeName;
        // Store self-reference so actor methods can pass "self" as actor handle
        _state.getFieldsMap().put("__handle__", this);
    }

    /** Constructor accepting LinkedHashMap (from bytecode struct literals) */
    public SiyoActor(java.util.LinkedHashMap<String, Object> stateMap, String actorTypeName) {
        _state = new SiyoStruct(stateMap);
        _actorTypeName = actorTypeName;
        stateMap.put("__handle__", this);
    }

    public SiyoStruct getState() { return _state; }
    public String getActorTypeName() { return _actorTypeName; }
    public LinkedBlockingQueue<ActorMessage> getMailbox() { return _mailbox; }

    public void setThread(Thread thread) { _thread = thread; }
    public Thread getThread() { return _thread; }
    public boolean isStopped() { return _stopped; }

    /**
     * Gracefully stop the actor. The event loop will drain the current message and exit.
     * Returns "stopped" for Siyo callers.
     */
    public String stop() {
        _stopped = true;
        // Send a poison pill to unblock the take()
        try { _mailbox.put(new ActorMessage("__stop__", new Object[0], null)); }
        catch (InterruptedException e) { Thread.currentThread().interrupt(); }
        return "stopped";
    }

    /**
     * Send a message and wait for reply (synchronous).
     */
    public Object call(String methodName, Object[] args) {
        LinkedBlockingQueue<Object> replyChannel = new LinkedBlockingQueue<>(1);
        try {
            _mailbox.put(new ActorMessage(methodName, deepCopyArgs(args), replyChannel));
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
            _mailbox.put(new ActorMessage(methodName, deepCopyArgs(args), null));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    /**
     * Deep-copy non-primitive args before passing to actor mailbox.
     * Primitives (int, long, bool, float, string) are immutable — pass through.
     * Structs (LinkedHashMap), arrays (SiyoArray/List), maps (SiyoMap), sets (SiyoSet)
     * are mutable — deep-copy to prevent shared state.
     */
    private static Object[] deepCopyArgs(Object[] args) {
        if (args == null || args.length == 0) return args;
        Object[] copied = new Object[args.length];
        for (int i = 0; i < args.length; i++) {
            copied[i] = deepCopyValue(args[i]);
        }
        return copied;
    }

    @SuppressWarnings("unchecked")
    private static Object deepCopyValue(Object val) {
        if (val == null) return null;
        // Primitives and strings are immutable — pass through
        if (val instanceof Integer || val instanceof Long || val instanceof Double
                || val instanceof Boolean || val instanceof String) {
            return val;
        }
        // SiyoActor handles — pass through (actors are meant to be shared by reference)
        if (val instanceof SiyoActor) return val;
        // SiyoChannel — pass through (channels are shared communication primitives)
        if (val instanceof SiyoChannel) return val;
        // SiyoStruct (LinkedHashMap) — deep copy all fields
        if (val instanceof SiyoStruct struct) {
            java.util.LinkedHashMap<String, Object> copy = new java.util.LinkedHashMap<>();
            for (var entry : struct.getFieldsMap().entrySet()) {
                if (entry.getKey().equals("__handle__")) {
                    copy.put(entry.getKey(), entry.getValue()); // don't copy actor handle ref
                } else {
                    copy.put(entry.getKey(), deepCopyValue(entry.getValue()));
                }
            }
            return new SiyoStruct(copy);
        }
        // LinkedHashMap (bytecode struct representation) — deep copy
        if (val instanceof java.util.LinkedHashMap<?,?> map) {
            java.util.LinkedHashMap<Object, Object> copy = new java.util.LinkedHashMap<>();
            for (var entry : map.entrySet()) {
                if ("__handle__".equals(entry.getKey())) {
                    copy.put(entry.getKey(), entry.getValue());
                } else {
                    copy.put(entry.getKey(), deepCopyValue(entry.getValue()));
                }
            }
            return copy;
        }
        // SiyoArray — deep copy elements
        if (val instanceof SiyoArray arr) {
            java.util.List<Object> elements = new java.util.ArrayList<>();
            for (int i = 0; i < arr.size(); i++) {
                elements.add(deepCopyValue(arr.get(i)));
            }
            return new SiyoArray(elements, Object.class);
        }
        // SiyoMap — deep copy entries
        if (val instanceof SiyoMap map) {
            SiyoMap copy = new SiyoMap();
            SiyoArray keys = map.keys();
            for (int i = 0; i < keys.size(); i++) {
                Object key = keys.get(i);
                copy.set(key, deepCopyValue(map.get(key)));
            }
            return copy;
        }
        // SiyoSet — deep copy elements
        if (val instanceof SiyoSet set) {
            SiyoSet copy = new SiyoSet();
            SiyoArray vals = set.values();
            for (int i = 0; i < vals.size(); i++) {
                copy.add(deepCopyValue(vals.get(i)));
            }
            return copy;
        }
        // Other Java objects — pass through (can't deep-copy arbitrary Java objects)
        return val;
    }

    /**
     * Start the actor's event loop on a virtual thread.
     * Called from generated bytecode.
     * @param actor the actor instance
     * @param hostClassName the class containing actor methods (e.g., "Main")
     */
    public static void startEventLoop(SiyoActor actor, String hostClassName) {
        // Capture current thread's classloader for use in virtual thread
        ClassLoader callerClassLoader = Thread.currentThread().getContextClassLoader();
        if (callerClassLoader == null) callerClassLoader = ClassLoader.getSystemClassLoader();
        final ClassLoader cl = callerClassLoader;

        Thread thread = Thread.startVirtualThread(() -> {
            while (!actor._stopped) {
                try {
                    ActorMessage msg = actor.getMailbox().take();
                    if (actor._stopped || msg.methodName.equals("__stop__")) break;
                    String methodName = actor.getActorTypeName() + "$" + msg.methodName;

                    Class<?> hostClass = cl.loadClass(hostClassName);
                    Object result = null;
                    boolean found = false;

                    for (var method : hostClass.getMethods()) {
                        if (method.getName().equals(methodName)) {
                            // Build args: [self_state_map, ...msg_args]
                            // Bytecode methods use Map (LinkedHashMap) for struct state
                            Object[] fullArgs = new Object[msg.args.length + 1];
                            fullArgs[0] = actor.getState().getFieldsMap();
                            System.arraycopy(msg.args, 0, fullArgs, 1, msg.args.length);

                            // Coerce args to match parameter types
                            Class<?>[] paramTypes = method.getParameterTypes();
                            for (int i = 0; i < fullArgs.length && i < paramTypes.length; i++) {
                                if (fullArgs[i] instanceof SiyoActor && paramTypes[i] == java.util.Map.class) {
                                    // Actor handle passed as struct field — keep as-is
                                } else if (fullArgs[i] instanceof Integer && paramTypes[i] == int.class) {
                                    // auto-unbox
                                }
                            }

                            result = method.invoke(null, fullArgs);
                            found = true;
                            break;
                        }
                    }

                    if (!found && msg.replyChannel != null) {
                        msg.replyChannel.put(new ActorError("Unknown method: " + msg.methodName));
                    } else if (msg.replyChannel != null) {
                        msg.replyChannel.put(result != null ? result : "");
                    }
                } catch (Exception e) {
                    System.err.println("[actor-err] " + actor.getActorTypeName() + ": " + e);
                    if (e.getCause() != null) e.getCause().printStackTrace(System.err);
                }
            }
        });
        actor.setThread(thread);

        // Auto-invoke run(self) if it exists
        try {
            Class<?> hostClass = cl.loadClass(hostClassName);
            for (var method : hostClass.getMethods()) {
                if (method.getName().equals(actor.getActorTypeName() + "$run")) {
                    actor.send("run", new Object[]{});
                    break;
                }
            }
        } catch (Exception e) {
            // ignore
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
