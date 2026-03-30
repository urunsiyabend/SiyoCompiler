package codeanalysis;

import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.SynchronousQueue;
import java.util.concurrent.TimeUnit;

/**
 * Thread-safe channel for concurrent communication.
 * channel()  → unbuffered (SynchronousQueue, true rendezvous — sender blocks until receiver takes)
 * channel(n) → buffered with capacity n (LinkedBlockingQueue)
 */
public class SiyoChannel {
    private static final Object CLOSED_SENTINEL = new Object();

    private final BlockingQueue<Object> _queue;
    private final int _capacity;
    private volatile boolean _closed = false;

    /** Unbuffered channel — true rendezvous like Go's make(chan T) */
    public SiyoChannel() {
        _capacity = 0;
        _queue = new SynchronousQueue<>();
    }

    /** Buffered channel with given capacity */
    public SiyoChannel(int capacity) {
        _capacity = capacity;
        _queue = new LinkedBlockingQueue<>(capacity);
    }

    /** Send a value. Blocks until a receiver takes it (unbuffered) or space is available (buffered). */
    public void send(Object value) {
        if (_closed) throw new RuntimeException("send on closed channel");
        try {
            _queue.put(value);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("Channel send interrupted");
        }
    }

    /** Receive a value. Blocks until a value is available. Returns null if channel is closed and empty. */
    public Object receive() {
        try {
            if (_closed && _queue.isEmpty()) return null;
            Object val = _queue.poll(50, TimeUnit.MILLISECONDS);
            while (val == null) {
                if (_closed && _queue.isEmpty()) return null;
                val = _queue.poll(50, TimeUnit.MILLISECONDS);
            }
            if (val == CLOSED_SENTINEL) return null;
            return val;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return null;
        }
    }

    /** Close the channel. Receivers will get null after all buffered values are consumed. */
    public void close() {
        _closed = true;
        // Push a sentinel to wake up any blocked receiver on SynchronousQueue
        try { _queue.offer(CLOSED_SENTINEL, 10, TimeUnit.MILLISECONDS); }
        catch (InterruptedException ignored) {}
    }

    /** Check if the channel is closed. */
    public boolean isClosed() { return _closed; }

    /** Try to receive with timeout. Returns null if timeout expires or channel is closed. */
    public Object tryReceive(long timeoutMs) {
        try {
            if (_closed && _queue.isEmpty()) return null;
            Object val = _queue.poll(timeoutMs, TimeUnit.MILLISECONDS);
            if (val == CLOSED_SENTINEL) return null;
            return val;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return null;
        }
    }

    public int size() { return _queue.size(); }
    public boolean isEmpty() { return _queue.isEmpty(); }

    @Override
    public String toString() {
        return "channel(capacity=" + _capacity + (_closed ? ", closed" : "") + ")";
    }
}
