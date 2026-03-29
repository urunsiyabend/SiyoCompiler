package codeanalysis;

import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

/**
 * Thread-safe channel for concurrent communication between spawn blocks.
 * Backed by LinkedBlockingQueue. send() blocks if full, receive() blocks if empty.
 */
public class SiyoChannel {
    private final LinkedBlockingQueue<Object> _queue;
    private final int _capacity;

    public SiyoChannel() {
        this(1); // default: rendezvous channel (capacity 1)
    }

    public SiyoChannel(int capacity) {
        _capacity = capacity;
        _queue = new LinkedBlockingQueue<>(capacity);
    }

    /** Send a value. Blocks until space is available. */
    public void send(Object value) {
        try {
            _queue.put(value);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("Channel send interrupted");
        }
    }

    /** Receive a value. Blocks until a value is available. */
    public Object receive() {
        try {
            return _queue.take();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("Channel receive interrupted");
        }
    }

    /** Try to receive with timeout. Returns null if timeout expires. */
    public Object tryReceive(long timeoutMs) {
        try {
            return _queue.poll(timeoutMs, TimeUnit.MILLISECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return null;
        }
    }

    public int size() { return _queue.size(); }
    public boolean isEmpty() { return _queue.isEmpty(); }

    @Override
    public String toString() {
        return "channel(capacity=" + _capacity + ", buffered=" + _queue.size() + ")";
    }
}
