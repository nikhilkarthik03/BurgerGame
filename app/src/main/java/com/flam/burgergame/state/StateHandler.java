package com.flam.burgergame.state;

import android.view.View;

/**
 * Interface for state handlers to manage each app state without switch statements
 */
public interface StateHandler {
    /**
     * Activate this state - set up UI, initialize components
     */
    void activate();

    /**
     * Deactivate this state - clean up resources, hide UI
     */
    void deactivate();

    /**
     * Get the root view for this state
     */
    View getRootView();
}