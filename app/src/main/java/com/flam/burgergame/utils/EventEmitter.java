package com.flam.burgergame.utils;

import android.os.Handler;
import android.os.Looper;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class EventEmitter {
    private static volatile EventEmitter instance;
    private final Map<String, List<WeakReference<EventListener>>> listeners;
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
            List<WeakReference<EventListener>> eventListeners = listeners.computeIfAbsent(eventName, k -> new ArrayList<>());

            // Prevent duplicates
            for (WeakReference<EventListener> ref : eventListeners) {
                EventListener existing = ref.get();
                if (existing != null && existing.equals(listener)) return false;
            }

            eventListeners.add(new WeakReference<>(listener));
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
            List<WeakReference<EventListener>> eventListeners = listeners.get(eventName);
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
        List<WeakReference<EventListener>> eventListeners;
        synchronized (listeners) {
            eventListeners = listeners.get(eventName);
            if (eventListeners == null) return;
            eventListeners = new ArrayList<>(eventListeners);
        }

        for (WeakReference<EventListener> ref : eventListeners) {
            EventListener listener = ref.get();
            if (listener != null) {
                mainHandler.post(() -> listener.onEvent(eventName, data));
            }
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

//    /**
//     * Get the number of listeners for a specific event
//     * @param eventName Name of the event
//     * @return Number of listeners
//     */
//    public int listenerCount(@NonNull String eventName) {
//        synchronized (listeners) {
//            List<EventListener> eventListeners = listeners.get(eventName);
//            return eventListeners != null ? eventListeners.size() : 0;
//        }
//    }

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
