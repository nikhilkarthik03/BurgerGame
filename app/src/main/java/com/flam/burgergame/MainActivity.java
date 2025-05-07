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

    public enum AppState {
        LOADING,
        INSTRUCTIONS,
        SELECTION,
        MAIN,
        END
    }

    private AppState currentState = AppState.LOADING;

    private ArFragment arFragment;

    private FrameLayout loadingLayout, instructionsLayout, gameScreen;

    private ImageView startButton, restartButton;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        if (!Sceneform.isSupported(this)) {
            Toast.makeText(this, "Sceneform is not supported on this device.", Toast.LENGTH_LONG).show();
            finish();
            return;
        }

        setContentView(R.layout.activity_main);


        loadingLayout = findViewById(R.id.loadingView);
        instructionsLayout = findViewById(R.id.instructionsView);
        gameScreen = findViewById(R.id.gameScreen);
        startButton = findViewById(R.id.startButton); // FIXED: moved after setContentView()
        restartButton = findViewById(R.id.restartButton);

        getSupportFragmentManager().addFragmentOnAttachListener(this);
        launchArFragment();

        // Start with LOADING
        setAppState(AppState.LOADING);

        // Automatically go to INSTRUCTIONS after 2 seconds
        new Handler().postDelayed(() -> setAppState(AppState.INSTRUCTIONS), 3000);

    }

    private void setAppState(AppState newState) {
        currentState = newState;

        loadingLayout.setVisibility(View.GONE);
        instructionsLayout.setVisibility(View.GONE);
        gameScreen.setVisibility(View.GONE);

        switch (currentState) {
            case LOADING:
                loadingLayout.setVisibility(View.VISIBLE);
                break;
            case INSTRUCTIONS:
                instructionsLayout.setVisibility(View.VISIBLE);
                Instructions instructionsScreen = new Instructions(this, arFragment);
                instructionsScreen.loadModels();

                startButton.setOnClickListener(v -> {
                    instructionsScreen.clearAllNodes();
                    setAppState(AppState.MAIN);
                });
                break;
            case MAIN:
                gameScreen.setVisibility(View.VISIBLE);
                GameScreen gameScreenManager = new GameScreen(this, arFragment);
                gameScreenManager.startGameTimer(10);
                gameScreenManager.loadModels();

                restartButton.setOnClickListener(v -> {
                    gameScreenManager.restart();
                    gameScreenManager.startGameTimer(10);
                    gameScreenManager.placeModel();
                });


                break;
            // TODO: Add other cases like SELECTION and END
        }
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
    }
}

