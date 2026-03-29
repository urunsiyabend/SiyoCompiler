# Siyo Actor Model - Design Document

## Overview

Siyo has two concurrency primitives:
- **scope/spawn** — batch processing. "Do N things, wait for all." Structured, finite.
- **actor** — server/daemon patterns. "Live forever, own state, communicate via messages." Unstructured, infinite.

Both are compile-time safe. No shared mutable state. No data races.

---

## 1. Syntax Design

### Option A: Erlang-style (module-based)
```siyo
actor StoreActor {
    state {
        data: map
    }

    receive Set(key: string, value: string) {
        self.data.set(key, value)
        reply("OK")
    }

    receive Get(key: string) -> string {
        reply(self.data.get(key))
    }
}

mut store = spawn StoreActor {}
store.send(Set("name", "siyo"))
mut val = store.ask(Get("name"))  // blocks
```
**Problem:** New keyword soup. `actor`, `state`, `receive`, `reply`. `send` vs `ask` distinction. Message types as implicit structs. Heavy syntax.

### Option B: Go-channel-style (explicit channels)
```siyo
fn storeActor(inbox: channel) {
    mut data = map()
    while true {
        mut msg = inbox.receive()
        // pattern match on msg...
    }
}

mut inbox = channel()
go storeActor(inbox)
inbox.send(["SET", "name", "siyo"])
```
**Problem:** No compile-time safety. Messages are untyped. No reply mechanism. `go` is just unscoped spawn.

### Option C: Rust/Swift hybrid (struct with actor keyword) ← **CHOSEN**
```siyo
actor Store {
    data: map
}

impl Store {
    fn new() -> Store {
        Store { data: map() }
    }

    fn set(self, key: string, value: string) -> string {
        self.data.set(key, value)
        "OK"
    }

    fn get(self, key: string) -> string {
        if self.data.has(key) { toString(self.data.get(key)) }
        else { "(nil)" }
    }

    fn del(self, key: string) -> string {
        if self.data.has(key) {
            self.data.remove(key)
            "(deleted)"
        } else { "(nil)" }
    }

    fn keys(self) -> string {
        mut result = ""
        imut k = self.data.keys()
        for i in range(0, len(k)) {
            if i > 0 { result += "," }
            result += toString(k[i])
        }
        result
    }
}

// Spawn actor — returns a handle, not the struct itself
mut store = spawn Store.new()

// Call methods — looks like regular method calls but:
// 1. Each call is a message sent to the actor's mailbox
// 2. The actor processes one message at a time (no data races)
// 3. Caller blocks until the actor returns the result
println(store.set("name", "siyo"))   // "OK"
println(store.get("name"))           // "siyo"
```

### Why Option C

1. **Minimal new syntax.** Only `actor` keyword replaces `struct`. `impl` stays the same. `spawn Actor.new()` reuses existing `spawn` keyword. Method calls look identical.

2. **Zero learning curve for actors.** If you know structs + impl, you know actors. The only difference: `actor` keyword tells the compiler "this state is owned by a single thread, all method calls are serialized messages."

3. **Compile-time safety falls out naturally.** Actor state fields are NEVER accessible from outside — only via method calls which are messages. The compiler already tracks struct field access. Adding `actor` just means: reject `store.data` (direct field access) and rewrite `store.set(...)` to message passing.

4. **Reply mechanism is implicit.** Method calls return values. No `send`/`ask` distinction. Every call is synchronous (blocks until processed). This matches how developers already think about method calls.

---

## 2. Safety Guarantees

### What the compiler enforces:

**Rule 1: Actor state is private.**
```siyo
actor Store { data: map }
mut store = spawn Store.new()
store.data  // COMPILE ERROR: Cannot access actor field directly
            // help: use a method to access actor state
```

**Rule 2: Actor methods are the only interface.**
All `impl` methods on an `actor` are message handlers. They execute one at a time on the actor's thread. No concurrent access to `self`.

**Rule 3: Actor handle is immutable.**
```siyo
mut store = spawn Store.new()
store = spawn Store.new()  // OK — reassign handle
// But the handle itself doesn't expose mutable state
```

**Rule 4: Actors can be passed to functions and captured by spawn.**
```siyo
scope {
    spawn { println(store.get("name")) }  // OK — actor handle is safe to share
    spawn { store.set("x", "1") }         // OK — calls are serialized by actor
}
```
Actor handles are like channel handles — thread-safe references. The compiler allows them to cross spawn boundaries.

### Binder changes:
- New `ActorSymbol` (like `StructSymbol` but with `isActor = true`)
- `bindMemberAccessExpression`: if target is actor instance → COMPILE ERROR for field access
- `bindMemberCallExpression`: if target is actor instance → rewrite to message passing
- `bindSpawnExpression`: actor handles are always safe to capture (like channels)

---

## 3. Reply Mechanism

### Synchronous (chosen)
```siyo
mut val = store.get("name")  // blocks until actor processes and returns
```

**Why synchronous:**
- **Simple mental model.** Looks like a function call, behaves like a function call. No callbacks, no futures, no `.await()`.
- **Backend languages need this.** HTTP handler: receive request → query store → build response → send. Every step is synchronous. async/await adds complexity without benefit when you have cheap virtual threads.
- **Virtual threads make it free.** Blocking a virtual thread costs nothing. JVM handles millions of blocked virtual threads. No need for async machinery.
- **Deadlock prevention:** Actor A calls actor B, B calls A → deadlock. This is a known limitation. For v1, we document this. For v2, we can add async calls. But in practice, well-designed actor systems rarely have circular calls.

### Under the hood:
```
store.get("name")  →  1. Create response channel
                       2. Send (method_id, args, response_channel) to actor mailbox
                       3. Block on response_channel.receive()
                       4. Actor processes, puts result on response channel
                       5. Caller unblocks with result
```

---

## 4. Actor Lifecycle

### Start
```siyo
mut store = spawn Store.new()  // actor starts, runs event loop
```
`spawn Actor.new()` does:
1. Create actor state via `new()` constructor
2. Start virtual thread with message processing loop
3. Return actor handle to caller

### Stop
```siyo
// Option 1: Method-based graceful shutdown
impl Store {
    fn shutdown(self) -> string {
        self.running = false  // actor state flag
        "shutting down"
    }
}
store.shutdown()

// Option 2: Handle goes out of scope → actor is garbage collected
// Virtual thread exits when no more references to the handle exist
```

For v1: actors run until the program exits or a shutdown method is called. No automatic cleanup. This matches Go goroutines (they run until main exits).

### Crash handling
```siyo
// If an actor method throws, the error is propagated to the caller:
try {
    mut result = store.get("key")
} catch e {
    println("Actor error: " + e)
}
```

The actor itself doesn't crash — only the current message fails. The actor continues processing subsequent messages. This is the Erlang "let it crash" philosophy applied to individual messages, not the entire actor.

---

## 5. JVM Mapping

### Actor = Virtual Thread + LinkedBlockingQueue
```java
class ActorHandle {
    Thread actorThread;                    // virtual thread running the event loop
    LinkedBlockingQueue<Message> mailbox;  // message queue
    Object state;                          // SiyoStruct (the actor's fields)
}

class Message {
    int methodId;          // which method to call
    Object[] args;         // method arguments
    LinkedBlockingQueue<Object> replyChannel;  // where to put the result
}
```

### Actor event loop (generated code):
```java
// Generated for each actor type
static void Store$eventLoop(Object state, LinkedBlockingQueue mailbox) {
    while (true) {
        Message msg = mailbox.take();  // blocks until message
        try {
            Object result = switch (msg.methodId) {
                case 0 -> Store$set(state, (String)msg.args[0], (String)msg.args[1]);
                case 1 -> Store$get(state, (String)msg.args[0]);
                case 2 -> Store$del(state, (String)msg.args[0]);
                case 3 -> Store$keys(state);
                default -> null;
            };
            msg.replyChannel.put(result);
        } catch (Exception e) {
            msg.replyChannel.put(e);  // propagate error to caller
        }
    }
}
```

### Message send (at call site):
```java
// store.get("name") compiles to:
LinkedBlockingQueue replyChannel = new LinkedBlockingQueue(1);
storeMailbox.put(new Message(1, new Object[]{"name"}, replyChannel));
Object result = replyChannel.take();  // block until reply
if (result instanceof Exception) throw (Exception)result;
return (String)result;
```

### Memory cost per actor:
- 1 virtual thread: ~1KB (JVM manages actual OS thread pool)
- 1 LinkedBlockingQueue: ~64 bytes
- State: whatever the struct fields hold
- **Total: ~1-2KB per actor** → millions of actors possible

---

## 6. SiyoDB Rewrite with Actors

### Current (single-threaded, one client at a time):
```siyo
mut store = map()
mut server = ServerSocket.new(PORT)
while true {
    mut socket = server.accept()
    // handle ONE client, others wait
    while connected {
        // read command, process, respond
    }
    socket.close()
}
```

### With actors (multi-client, concurrent):
```siyo
actor Store {
    data: map
}

impl Store {
    fn new() -> Store { Store { data: map() } }

    fn set(self, key: string, value: string) -> string {
        self.data.set(key, value)
        "OK"
    }

    fn get(self, key: string) -> string {
        if self.data.has(key) { toString(self.data.get(key)) }
        else { "(nil)" }
    }

    fn del(self, key: string) -> string {
        if self.data.has(key) {
            self.data.remove(key)
            "(deleted)"
        } else { "(nil)" }
    }

    fn keys(self) -> string {
        mut result = ""
        imut k = self.data.keys()
        for i in range(0, len(k)) {
            if i > 0 { result += "," }
            result += toString(k[i])
        }
        result
    }
}

actor ClientHandler {
    socket: fn,      // Java Socket (opaque handle)
    store: Store,    // actor handle — safe to share
    clientId: int
}

impl ClientHandler {
    fn new(socket: fn, store: Store, clientId: int) -> ClientHandler {
        ClientHandler { socket: socket, store: store, clientId: clientId }
    }

    fn handle(self) -> string {
        mut reader = BufferedReader.new(InputStreamReader.new(self.socket.getInputStream()))
        mut writer = PrintWriter.new(self.socket.getOutputStream(), true)
        writer.println("WELCOME to SiyoDB")

        mut connected = true
        while connected {
            imut line = reader.readLine()
            if line == null { connected = false }
            else {
                imut cmd = trim(line)
                if cmd == "PING" { writer.println("PONG") }
                else if cmd == "QUIT" { writer.println("BYE"); connected = false }
                else if cmd == "KEYS" { writer.println(self.store.keys()) }
                else if startsWith(cmd, "GET ") {
                    writer.println(self.store.get(substring(cmd, 4, len(cmd))))
                } else if startsWith(cmd, "SET ") {
                    imut rest = substring(cmd, 4, len(cmd))
                    imut sp = indexOf(rest, " ")
                    if sp < 0 { writer.println("ERR: SET key value") }
                    else {
                        writer.println(self.store.set(
                            substring(rest, 0, sp),
                            substring(rest, sp + 1, len(rest))
                        ))
                    }
                } else if startsWith(cmd, "DEL ") {
                    writer.println(self.store.del(substring(cmd, 4, len(cmd))))
                } else {
                    writer.println("ERR: unknown command")
                }
            }
        }
        self.socket.close()
        "disconnected"
    }
}

// === Main ===
mut store = spawn Store.new()
mut server = ServerSocket.new(7777)
mut clientCount = 0

println("SiyoDB listening on port 7777")

while true {
    mut socket = server.accept()
    clientCount += 1
    // Each client gets its own actor — runs on its own virtual thread
    // store actor handle is shared — all calls are serialized via mailbox
    mut handler = spawn ClientHandler.new(socket, store, clientCount)
    handler.handle()  // fire-and-forget — handler runs independently
    // NOTE: handler.handle() returns immediately because it's an actor call
    // The actual handling happens on the actor's thread
}
```

### What changed:
1. **`Store` is now an actor** — concurrent access from multiple clients is safe. All SET/GET/DEL calls are serialized by the actor's mailbox.
2. **Each client gets a `ClientHandler` actor** — runs on its own virtual thread. Multiple clients handled concurrently.
3. **No scope/spawn needed** — actors are inherently unscoped (live until stopped).
4. **Store actor handle shared across client handlers** — compiler allows this because actor handles are safe.
5. **Zero mutex, zero lock, zero race condition** — guaranteed by the actor model.

### Litmus test result:
The syntax is **clean**. Actor definition looks like struct + impl with one keyword change. Spawning looks natural. Method calls look like regular calls. No message type boilerplate. No send/ask distinction.

The model **fits server patterns perfectly**. Accept loop + per-client handlers + shared store — exactly what scope/spawn couldn't express.

---

## 7. Implementation Phases

### Phase 1: Core
- `actor` keyword (parser, syntax, binder)
- `spawn Actor.new()` → virtual thread + mailbox
- Method calls → message passing + synchronous reply
- Compile-time field access rejection

### Phase 2: Integration
- Actor handles safe for spawn capture (like channels)
- Error propagation from actor methods
- Actor state type tracking (nested actor fields)

### Phase 3: Polish
- Graceful shutdown mechanism
- Actor supervision (optional)
- Async calls (`actor.send(...)` without waiting for reply)
