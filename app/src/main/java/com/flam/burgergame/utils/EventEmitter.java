package com.flam.burgergame.utils;

import android.os.Handler;
import android.os.Looper;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class EventEmitter {
    private static volatile EventEmitter instance;
    private final Map<String, List<EventListener>> listeners;
    private final Handler mainHandler;

    public interface EventListener {
        void onEvent(@NonNull String eventName, @Nullable Object data);
    }

    private EventEmitter() {
        this.listeners = new ConcurrentHashMap<>();
        this.mainHandler = new Handler(Looper.getMainLooper());
    }

    public static EventEmitter getInstance() {
        if (instance == null) {
            synchronized (EventEmitter.class) {
                if (instance == null) {
                    instance = new EventEmitter();
                }
            }
        }
        return instance;
    }

    /**
     * Subscribe to an event
     * @param eventName Name of the event to listen to
     * @param listener Listener to be called when event is emitted
     * @return boolean indicating if the listener was added (true) or skipped because it was a duplicate (false)
     */
    public boolean on(@NonNull String eventName, @NonNull EventListener listener) {
        synchronized (listeners) {
            List<EventListener> eventListeners = listeners.computeIfAbsent(eventName, k -> new ArrayList<>());

            // Check if this listener is already registered for this event
            if (eventListeners.contains(listener)) {
                return false; // Listener already exists, don't add it again
            }

            // Add the new listener
            eventListeners.add(listener);
            return true;
        }
    }


    /**
     * Unsubscribe from an event
     * @param eventName Name of the event to unsubscribe from
     * @param listener Listener to remove
     */
    public void off(@NonNull String eventName, @NonNull EventListener listener) {
        synchronized (listeners) {
            List<EventListener> eventListeners = listeners.get(eventName);
            if (eventListeners != null) {
                eventListeners.remove(listener);
                if (eventListeners.isEmpty()) {
                    listeners.remove(eventName);
                }
            }
        }
    }

    /**
     * Emit an event
     * @param eventName Name of the event to emit
     * @param data Data to pass to listeners
     */
    public void emit(@NonNull String eventName, @Nullable Object data) {
        List<EventListener> eventListeners;
        synchronized (listeners) {
            eventListeners = listeners.get(eventName);
            if (eventListeners == null) return;
            eventListeners = new ArrayList<>(eventListeners);
        }

        for (EventListener listener : eventListeners) {
            // Post to main thread to ensure UI safety
            mainHandler.post(() -> listener.onEvent(eventName, data));
        }
    }

    /**
     * Remove all listeners for a specific event
     * @param eventName Name of the event to clear listeners for
     */
    public void removeAllListeners(@NonNull String eventName) {
        synchronized (listeners) {
            listeners.remove(eventName);
        }
    }

    /**
     * Clear all listeners for all events
     */
    public void removeAllListeners() {
        synchronized (listeners) {
            listeners.clear();
        }
    }

    /**
     * Get the number of listeners for a specific event
     * @param eventName Name of the event
     * @return Number of listeners
     */
    public int listenerCount(@NonNull String eventName) {
        synchronized (listeners) {
            List<EventListener> eventListeners = listeners.get(eventName);
            return eventListeners != null ? eventListeners.size() : 0;
        }
    }

    /**
     * Subscribe to an event for one time only
     * @param eventName Name of the event to listen to
     * @param listener Listener to be called when event is emitted
     */
    public void once(@NonNull String eventName, @NonNull EventListener listener) {
        EventListener onceListener = new EventListener() {
            @Override
            public void onEvent(@NonNull String event, @Nullable Object data) {
                off(eventName, this);
                listener.onEvent(event, data);
            }
        };
        on(eventName, onceListener);
    }
}
