package com.flam.burgergame;

import android.os.Bundle;
import android.os.Handler;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;
import androidx.fragment.app.FragmentOnAttachListener;

import com.flam.burgergame.screens.GameScreen;
import com.flam.burgergame.screens.Instructions;
import com.flam.burgergame.screens.Selection;
import com.flam.burgergame.state.AppStateManager;
import com.flam.burgergame.state.StateHandler;
import com.flam.burgergame.utils.EventEmitter;
import com.flam.burgergame.utils.GameEvents;
import com.google.ar.core.Config;
import com.google.ar.core.Session;
import com.google.ar.sceneform.ArSceneView;
import com.google.ar.sceneform.Sceneform;
import com.google.ar.sceneform.ux.ArFragment;
import com.google.ar.sceneform.ux.BaseArFragment;

public class MainActivity extends AppCompatActivity implements
        FragmentOnAttachListener,
        BaseArFragment.OnSessionConfigurationListener,
        ArFragment.OnViewCreatedListener {

    private ArFragment arFragment;
    private AppStateManager stateManager;
    private EventEmitter eventEmitter;
    private EventEmitter.EventListener stateChangeListener;

    private FrameLayout loadingLayout, instructionsLayout, selectionLayout, gameScreen;
    private ImageView nextButton, startButton, restartButton;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        if (!Sceneform.isSupported(this)) {
            Toast.makeText(this, "Sceneform is not supported on this device.", Toast.LENGTH_LONG).show();
            finish();
            return;
        }

        setContentView(R.layout.activity_main);

        stateManager = AppStateManager.getInstance();
        eventEmitter = EventEmitter.getInstance();

        loadingLayout = findViewById(R.id.loadingView);
        instructionsLayout = findViewById(R.id.instructionsView);
        selectionLayout = findViewById(R.id.selectionView);
        gameScreen = findViewById(R.id.gameScreen);
        nextButton = findViewById(R.id.nextButton);
        startButton = findViewById(R.id.startButton);
        restartButton = findViewById(R.id.restartButton);

        getSupportFragmentManager().addFragmentOnAttachListener(this);
        launchArFragment();

        registerStateHandlers();

        setupStateChangeListener();

        stateManager.setAppState(AppStateManager.AppState.LOADING);

        new Handler().postDelayed(() ->
                stateManager.setAppState(AppStateManager.AppState.INSTRUCTIONS), 3000);
    }

    private void registerStateHandlers() {
        stateManager.registerStateHandler(AppStateManager.AppState.LOADING, new StateHandler() {
            @Override
            public void activate() {
                loadingLayout.setVisibility(View.VISIBLE);
                eventEmitter.emit(GameEvents.UI_STATE_CHANGED, "loading_activated");
            }

            @Override
            public void deactivate() {
                loadingLayout.setVisibility(View.GONE);
                eventEmitter.emit(GameEvents.UI_STATE_CHANGED, "loading_deactivated");
            }

            @Override
            public View getRootView() {
                return loadingLayout;
            }
        });

        // Instructions state handler
        stateManager.registerStateHandler(AppStateManager.AppState.INSTRUCTIONS, new StateHandler() {
            private Instructions instructionsScreen;

            @Override
            public void activate() {
                instructionsLayout.setVisibility(View.VISIBLE);

                if (arFragment != null && arFragment.getArSceneView() != null) {
                    instructionsScreen = new Instructions(MainActivity.this, arFragment);
                    instructionsScreen.loadModels();
                }

                // Set up start button action
                nextButton.setOnClickListener(v -> {
                    eventEmitter.emit(GameEvents.BUTTON_CLICKED, "nextButton");
                    stateManager.setAppState(AppStateManager.AppState.SELECTION);
                });

                eventEmitter.emit(GameEvents.UI_STATE_CHANGED, "instructions_activated");
            }

            @Override
            public void deactivate() {
                instructionsLayout.setVisibility(View.GONE);

                // Clean up
                if (instructionsScreen != null) {
                    instructionsScreen.clearAllNodes();
                    instructionsScreen = null;
                }

                eventEmitter.emit(GameEvents.UI_STATE_CHANGED, "instructions_deactivated");
            }

            @Override
            public View getRootView() {
                return instructionsLayout;
            }
        });

        stateManager.registerStateHandler(AppStateManager.AppState.SELECTION, new StateHandler() {
            private Selection selectionScreen;

            @Override
            public void activate() {
                selectionLayout.setVisibility(View.VISIBLE);

                if (arFragment != null && arFragment.getArSceneView() != null) {
                    selectionScreen = new Selection(MainActivity.this, arFragment);
                    selectionScreen.loadModels();
                }

                // Set up start button action
                startButton.setOnClickListener(v -> {
                    eventEmitter.emit(GameEvents.BUTTON_CLICKED, "startButton");
                    stateManager.setAppState(AppStateManager.AppState.MAIN);
                });

                eventEmitter.emit(GameEvents.UI_STATE_CHANGED, "selections_activated");
            }

            @Override
            public void deactivate() {
                selectionLayout.setVisibility(View.GONE);

                // Clean up
                if (selectionScreen != null) {
                    selectionScreen.clearAllNodes();
                    selectionScreen = null;
                }

                eventEmitter.emit(GameEvents.UI_STATE_CHANGED, "selections_deactivated");
            }

            @Override
            public View getRootView() {
                return selectionLayout;
            }
        });


        // Main game state handler
        stateManager.registerStateHandler(AppStateManager.AppState.MAIN, new StateHandler() {
            private GameScreen gameScreenManager;

            @Override
            public void activate() {
                gameScreen.setVisibility(View.VISIBLE);

                if (arFragment != null && arFragment.getArSceneView() != null) {
                    gameScreenManager = new GameScreen(MainActivity.this, arFragment);
                    gameScreenManager.startGameTimer(10);
                    gameScreenManager.loadModels();

                }

                // Set up restart button action
                restartButton.setOnClickListener(v -> {
                    eventEmitter.emit(GameEvents.BUTTON_CLICKED, "restartButton");

                    if (gameScreenManager != null) {
                        gameScreenManager.restart();
                        gameScreenManager.startGameTimer(10);
                        gameScreenManager.placeModel();
                    }
                });

                eventEmitter.emit(GameEvents.UI_STATE_CHANGED, "game_activated");
                eventEmitter.emit(GameEvents.GAME_STARTED, null);
            }

            @Override
            public void deactivate() {
                gameScreen.setVisibility(View.GONE);

                // Clean up
                if (gameScreenManager != null) {
                    gameScreenManager.cleanup();
                    gameScreenManager = null;
                }

                eventEmitter.emit(GameEvents.UI_STATE_CHANGED, "game_deactivated");
                eventEmitter.emit(GameEvents.GAME_ENDED, null);
            }

            @Override
            public View getRootView() {
                return gameScreen;
            }
        });

    }

    private void setupStateChangeListener() {
        stateChangeListener = (eventName, data) -> {
            if (eventName.equals(GameEvents.APP_STATE_CHANGED)) {
                AppStateManager.StateChangeEvent event = (AppStateManager.StateChangeEvent) data;
                assert event != null;
                String stateChange = "State changed from " + event.getOldState() + " to " + event.getNewState();
            }
        };

        stateManager.addStateChangeListener(stateChangeListener);
    }

    private void launchArFragment() {
        arFragment = new ArFragment();
        getSupportFragmentManager()
                .beginTransaction()
                .replace(R.id.arFragment, arFragment)
                .commit();
    }

    @Override
    public void onAttachFragment(@NonNull FragmentManager fragmentManager, @NonNull Fragment fragment) {
        if (fragment instanceof ArFragment) {
            arFragment = (ArFragment) fragment;
            arFragment.setOnSessionConfigurationListener(this);
            arFragment.setOnViewCreatedListener(this);

            eventEmitter.emit(GameEvents.AR_FRAGMENT_READY, arFragment);

            AppStateManager.AppState currentState = stateManager.getCurrentState();
            if (currentState != AppStateManager.AppState.LOADING) {
                stateManager.setAppState(currentState);
            }
        }
    }

    @Override
    public void onSessionConfiguration(Session session, Config config) {
        if (session.isDepthModeSupported(Config.DepthMode.AUTOMATIC)) {
            config.setDepthMode(Config.DepthMode.AUTOMATIC);
        }
        config.setLightEstimationMode(Config.LightEstimationMode.DISABLED);
    }

    @Override
    public void onViewCreated(ArSceneView arSceneView) {
        arFragment.setOnViewCreatedListener(null);
        arSceneView.setFrameRateFactor(ArSceneView.FrameRate.FULL);

        eventEmitter.emit(GameEvents.AR_SCENE_VIEW_READY, arSceneView);
    }

    @Override
    protected void onDestroy() {
        if (stateManager != null) {
            stateManager.removeStateChangeListener(stateChangeListener);

            stateManager.unregisterStateHandler(AppStateManager.AppState.LOADING);
            stateManager.unregisterStateHandler(AppStateManager.AppState.INSTRUCTIONS);
            stateManager.unregisterStateHandler(AppStateManager.AppState.MAIN);
            stateManager.unregisterStateHandler(AppStateManager.AppState.SELECTION);
            stateManager.unregisterStateHandler(AppStateManager.AppState.END);
        }

        super.onDestroy();
    }
}