package com.flam.burgergame.utils;

/**
 * GameEvents defines all event names used throughout the app for consistency
 */
public final class GameEvents {
    // App state events - also defined in AppStateManager
    public static final String APP_STATE_CHANGED = "app_state_changed";
    public static final String DISPLAY_METRICS_CHANGED = "display_metrics_changed";

    // UI events
    public static final String UI_STATE_CHANGED = "ui_state_changed";
    public static final String BUTTON_CLICKED = "button_clicked";
    public static final String MENU_OPENED = "menu_opened";
    public static final String DIALOG_CLOSED = "dialog_closed";

    // AR-related events
    public static final String AR_FRAGMENT_READY = "ar_fragment_ready";
    public static final String AR_SCENE_VIEW_READY = "ar_scene_view_ready";
    public static final String MODEL_LOADING_STARTED = "model_loading_started";
    public static final String MODEL_LOADED = "model_loaded";
    public static final String MODEL_LOAD_ERROR = "model_load_error";
    public static final String MODEL_ANIMATION_STARTED = "model_animation_started";
    public static final String MODEL_TAPPED = "model_tapped";
    public static final String CLEARING_SCENE_NODES = "clearing_scene_nodes";
    public static final String SCENE_NODES_CLEARED = "scene_nodes_cleared";

    // Game mechanics events
    public static final String GAME_TIMER_STARTED = "game_timer_started";
    public static final String GAME_TIMER_TICK = "game_timer_tick";
    public static final String GAME_TIMER_FINISHED = "game_timer_finished";
    public static final String GAME_STARTED = "game_started";
    public static final String GAME_PAUSED = "game_paused";
    public static final String GAME_RESUMED = "game_resumed";
    public static final String GAME_ENDED = "game_ended";
    public static final String LEVEL_COMPLETED = "level_completed";

    // Score events
    public static final String SCORE_UPDATED = "score_updated";
    public static final String HIGH_SCORE_ACHIEVED = "high_score_achieved";

    // Burger-specific events (for your game)
    public static final String BURGER_CREATED = "burger_created";
    public static final String BURGER_SERVED = "burger_served";
    public static final String BURGER_DROPPED = "burger_dropped";

    // Prevent instantiation
    private GameEvents() {}
}