package com.flam.burgergame.state;

import android.util.DisplayMetrics;

import com.flam.burgergame.utils.EventEmitter;
import com.flam.burgergame.utils.GameEvents;

import java.util.HashMap;
import java.util.Map;

/**
 * AppStateManager handles the application state transitions and notifies
 * listeners through the EventEmitter pattern.
 */
public class AppStateManager {

    public enum AppState {
        LOADING,
        INSTRUCTIONS,
        DESCRIPTION,
        SELECTION,
        MAIN,
        END
    }

    private static volatile AppStateManager instance;
    private final EventEmitter eventEmitter;
    private AppState currentState = AppState.LOADING;
    private DisplayMetrics displayMetrics;

    // Map of state handlers for each app state
    private final Map<AppState, StateHandler> stateHandlers = new HashMap<>();
    private StateHandler activeHandler = null;

    private AppStateManager() {
        this.eventEmitter = EventEmitter.getInstance();
    }

    public static synchronized AppStateManager getInstance() {
        if (instance == null) {
            synchronized (AppStateManager.class) {
                if (instance == null) {
                    instance = new AppStateManager();
                }
            }
        }
        return instance;
    }

    /**
     * Register a state handler for a specific app state
     */
    public void registerStateHandler(AppState state, StateHandler handler) {
        stateHandlers.put(state, handler);

        // If this is the current state, activate it immediately
        if (state == currentState && activeHandler == null) {
            activateHandler(handler);
        }
    }

    /**
     * Unregister a state handler
     */
    public void unregisterStateHandler(AppState state) {
        // If this is the active handler, deactivate it first
        if (currentState == state && activeHandler != null) {
            deactivateActiveHandler();
        }
        stateHandlers.remove(state);
    }

    /**
     * Sets display metrics and notifies listeners
     */
    public void setDisplayMetrics(DisplayMetrics displayMetrics) {
        this.displayMetrics = displayMetrics;
        eventEmitter.emit(GameEvents.DISPLAY_METRICS_CHANGED, displayMetrics);
    }

    /**
     * Get current display metrics
     */
    public DisplayMetrics getDisplayMetrics() {
        return displayMetrics;
    }

    /**
     * Set application state and notify listeners if state changed
     */
    public void setAppState(AppState newState) {
        if (newState != currentState) {
            AppState oldState = currentState;
            currentState = newState;

            // Deactivate current handler if any
            deactivateActiveHandler();

            // Activate new state handler if available
            StateHandler newHandler = stateHandlers.get(newState);
            if (newHandler != null) {
                activateHandler(newHandler);
            }

            // Create a StateChangeEvent object to pass both old and new states
            StateChangeEvent event = new StateChangeEvent(oldState, newState);
            eventEmitter.emit(GameEvents.APP_STATE_CHANGED, event);
        }
    }

    private void deactivateActiveHandler() {
        if (activeHandler != null) {
            activeHandler.deactivate();
            activeHandler = null;
        }
    }

    private void activateHandler(StateHandler handler) {
        activeHandler = handler;
        handler.activate();
    }

    /**
     * Get current application state
     */
    public AppState getCurrentState() {
        return currentState;
    }

    /**
     * Add listener for app state changes
     */
    public void addStateChangeListener(EventEmitter.EventListener listener) {
        eventEmitter.on(GameEvents.APP_STATE_CHANGED, listener);
    }

    /**
     * Remove listener for app state changes
     */
    public void removeStateChangeListener(EventEmitter.EventListener listener) {
        eventEmitter.off(GameEvents.APP_STATE_CHANGED, listener);
    }

    /**
     * Add one-time listener for next app state change
     */
    public void onceStateChange(EventEmitter.EventListener listener) {
        eventEmitter.once(GameEvents.APP_STATE_CHANGED, listener);
    }

    /**
     * StateChangeEvent class to hold both old and new state
     */
    public static class StateChangeEvent {
        private final AppState oldState;
        private final AppState newState;

        public StateChangeEvent(AppState oldState, AppState newState) {
            this.oldState = oldState;
            this.newState = newState;
        }

        public AppState getOldState() {
            return oldState;
        }

        public AppState getNewState() {
            return newState;
        }
    }
}