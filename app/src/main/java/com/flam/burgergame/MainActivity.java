package com.flam.burgergame;

import android.media.MediaPlayer;
import android.os.Bundle;
import android.os.Handler;
import android.view.View;
import android.webkit.WebView;
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
import com.flam.burgergame.utils.ModelLoaderManager;
import com.google.ar.core.Config;
import com.google.ar.core.Session;
import com.google.ar.sceneform.ArSceneView;
import com.google.ar.sceneform.Sceneform;
import com.google.ar.sceneform.ux.ArFragment;
import com.google.ar.sceneform.ux.BaseArFragment;
import com.google.ar.sceneform.ux.InstructionsController;

public class MainActivity extends AppCompatActivity implements
        FragmentOnAttachListener,
        BaseArFragment.OnSessionConfigurationListener,
        ArFragment.OnViewCreatedListener {

    private ArFragment arFragment;
    private MediaPlayer bgmPlayer;

    private AppStateManager stateManager;
    private EventEmitter eventEmitter;
    private EventEmitter.EventListener stateChangeListener;
    private EventEmitter.EventListener modelLoadListener;
    private ModelLoaderManager modelLoaderManager;

    private FrameLayout loadingLayout, instructionsLayout, descriptionLayout,  selectionLayout, gameScreen;
    private ImageView nextButton1, nextButton2, startButton, restartButton;

    private ImageView instructionsVideoview;
    private String InstructionsVideoPath;
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        if (!Sceneform.isSupported(this)) {
            Toast.makeText(this, "Sceneform is not supported on this device.", Toast.LENGTH_LONG).show();
            finish();
            return;
        }

        setContentView(R.layout.activity_main);

        bgmPlayer = MediaPlayer.create(this, R.raw.v1); // Use your filename
        bgmPlayer.setLooping(true); // Optional: Loop BGM
        bgmPlayer.setVolume(0.5f, 0.5f); // Optional: Set volume (left, right)
        bgmPlayer.start();

        stateManager = AppStateManager.getInstance();        bgmPlayer = MediaPlayer.create(this, R.raw.v1); // Use your filename
        bgmPlayer.setLooping(true); // Optional: Loop BGM
        bgmPlayer.setVolume(0.5f, 0.5f); // Optional: Set volume (left, right)
        eventEmitter = EventEmitter.getInstance();
        modelLoaderManager = ModelLoaderManager.getInstance();

        loadingLayout = findViewById(R.id.loadingView);
        instructionsLayout = findViewById(R.id.instructionsView);
        descriptionLayout = findViewById(R.id.descriptionView);
        selectionLayout = findViewById(R.id.selectionView);
        gameScreen = findViewById(R.id.gameScreen);
        nextButton1 = findViewById(R.id.nextButton1);
        nextButton2 = findViewById(R.id.nextButton2);
        startButton = findViewById(R.id.startButton);
        restartButton = findViewById(R.id.restartButton);
        WebView gifView = findViewById(R.id.logo);
        gifView.getSettings().setLoadWithOverviewMode(true);
        gifView.getSettings().setUseWideViewPort(true);
        gifView.getSettings().setJavaScriptEnabled(true);
        gifView.setBackgroundColor(0x00000000); // Transparent background

        String gifPath = "file:///android_res/raw/logo.gif"; // if stored in res/raw
        String htmlData = "<html><body style='margin:0;padding:0;'><img style='width:100%;height:auto;' src=\"" + gifPath + "\"></body></html>";
        gifView.loadDataWithBaseURL("file:///android_res/raw/", htmlData, "text/html", "UTF-8", null);


        getSupportFragmentManager().addFragmentOnAttachListener(this);
        launchArFragment();

        registerStateHandlers();
        setupModelLoadListener();
        setupStateChangeListener();

        stateManager.setAppState(AppStateManager.AppState.LOADING);
    }

    private void setupModelLoadListener() {
        final long MIN_LOADING_TIME_MS = 5000; // 5 seconds minimum loading time
        final long[] loadStartTime = {System.currentTimeMillis()}; // Track when loading started

        modelLoadListener = (eventName, data) -> {
            if (eventName.equals(GameEvents.ALL_MODELS_LOADED)) {
                // Models loaded, but ensure we show loading screen for at least 5 seconds
                long elapsedTime = System.currentTimeMillis() - loadStartTime[0];
                long remainingTime = Math.max(0, MIN_LOADING_TIME_MS - elapsedTime);

//                // Delay transition to instructions by remaining time if needed
                new Handler().postDelayed(() ->
                                stateManager.setAppState(AppStateManager.AppState.INSTRUCTIONS),
                        3000);
            }
        };

        // Register listener for model loading events
        eventEmitter.on(GameEvents.ALL_MODELS_LOADED, modelLoadListener);
    }

    public void stopBackgroundMusic() {
        if (bgmPlayer != null && bgmPlayer.isPlaying()) {
            bgmPlayer.stop();
        }
    }

    public void restartBackgroundMusic() {
        if (bgmPlayer != null) {
            bgmPlayer.release();  // Release the old player
            bgmPlayer = null;
        }

        bgmPlayer = MediaPlayer.create(this, R.raw.v1);  // Recreate
        bgmPlayer.setLooping(true);
        bgmPlayer.setVolume(0.3f, 0.3f);
        bgmPlayer.start();
    }

    private void registerStateHandlers() {
        stateManager.registerStateHandler(AppStateManager.AppState.LOADING, new StateHandler() {
            @Override
            public void activate() {
                loadingLayout.setVisibility(View.VISIBLE);
                eventEmitter.emit(GameEvents.UI_STATE_CHANGED, "loading_activated");

                // Start loading models when in loading state
                if (modelLoaderManager != null) {
                    modelLoaderManager.loadAllModels(MainActivity.this);
                }
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
                    instructionsScreen.setupPreloadedModels();
                }

                // Set up start button action
                nextButton1.setOnClickListener(v -> {
                    eventEmitter.emit(GameEvents.BUTTON_CLICKED, "nextButton1");
                    stateManager.setAppState(AppStateManager.AppState.DESCRIPTION);
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

        // Description state handler
        stateManager.registerStateHandler(AppStateManager.AppState.DESCRIPTION, new StateHandler() {
            @Override
            public void activate() {
                descriptionLayout.setVisibility(View.VISIBLE);

                // Set up start button action
                nextButton2.setOnClickListener(v -> {
                    eventEmitter.emit(GameEvents.BUTTON_CLICKED, "nextButton2");
                    stateManager.setAppState(AppStateManager.AppState.SELECTION);
                });

                eventEmitter.emit(GameEvents.UI_STATE_CHANGED, "instructions_activated");
            }

            @Override
            public void deactivate() {
                descriptionLayout.setVisibility(View.GONE);

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

                // Play instruction video
                WebView instruction = findViewById(R.id.instruction);
                instruction.getSettings().setLoadWithOverviewMode(true);
                instruction.getSettings().setUseWideViewPort(true);
                instruction.getSettings().setJavaScriptEnabled(true);
                instruction.setBackgroundColor(0x00000000); // Transparent background

                String gifPath = "file:///android_res/raw/tray1.gif"; // if stored in res/raw
                String htmlData = "<html><body style='margin:0;padding:0;'><img style='width:100%;height:auto;' src=\"" + gifPath + "\"></body></html>";
                instruction.loadDataWithBaseURL("file:///android_res/raw/", htmlData, "text/html", "UTF-8", null);


                startButton.setOnClickListener(v -> {
                    eventEmitter.emit(GameEvents.BUTTON_CLICKED, "startButton");
                    stateManager.setAppState(AppStateManager.AppState.MAIN);
                });

                eventEmitter.emit(GameEvents.UI_STATE_CHANGED, "selections_activated");

            }


            @Override
            public void deactivate() {
                selectionLayout.setVisibility(View.GONE);

            }


            @Override
            public View getRootView() {
                return selectionLayout;
            }
        });


        // Main game state handler
        stateManager.registerStateHandler(AppStateManager.AppState.MAIN, new StateHandler() {

            private final int gameTime = 30;
            private GameScreen gameScreenManager;

            @Override
            public void activate() {
                gameScreen.setVisibility(View.VISIBLE);

                if (arFragment != null && arFragment.getArSceneView() != null) {
                    gameScreenManager = new GameScreen(MainActivity.this, arFragment);
                    gameScreenManager.startGameTimer(gameTime);
                    gameScreenManager.loadModels();
                }

                // Set up restart button action
                restartButton.setOnClickListener(v -> {
                    eventEmitter.emit(GameEvents.BUTTON_CLICKED, "restartButton");

                    if (gameScreenManager != null) {
                        gameScreenManager.restart();
                        gameScreenManager.startGameTimer(gameTime);
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
        config.setDepthMode(Config.DepthMode.DISABLED);
        config.setLightEstimationMode(Config.LightEstimationMode.DISABLED);
    }

    @Override
    public void onViewCreated(ArSceneView arSceneView) {
        arFragment.setOnViewCreatedListener(null);
        arSceneView.setFrameRateFactor(ArSceneView.FrameRate.FULL);

        arSceneView.getPlaneRenderer().setEnabled(false);
        arFragment.setOnTapArPlaneListener(null);
        arFragment.getInstructionsController().setEnabled(InstructionsController.TYPE_PLANE_DISCOVERY,false);

        arSceneView.getPlaneRenderer().getMaterial().thenAccept(material -> {
            material.setFloat3("color", 0.0f, 0.0f, 0.0f);
            arSceneView.getPlaneRenderer().setVisible(false);
        });

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