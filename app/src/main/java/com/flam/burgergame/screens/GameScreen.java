package com.flam.burgergame.screens;

import android.content.Context;
import android.os.CountDownTimer;
import android.widget.TextView;

import com.flam.burgergame.R;
import com.flam.burgergame.utils.EventEmitter;
import com.flam.burgergame.utils.GameEvents;
import com.google.ar.sceneform.ux.ArFragment;

import java.util.ArrayList;
import java.util.List;
import com.google.ar.sceneform.Node;

public class GameScreen {

    private Context context;
    private ArFragment arFragment;
    private EventEmitter eventEmitter;
    private CountDownTimer gameTimer;
    private int currentScore = 0;
    private List<Node> sceneNodes = new ArrayList<>();

    // Reference to timer TextView - you would set this in your actual implementation
    private TextView timerText;

    public GameScreen(Context context, ArFragment arFragment) {
        this.context = context;
        this.arFragment = arFragment;
        this.eventEmitter = EventEmitter.getInstance();

        // You would set up timerText here, for example:
        // timerText = ((Activity)context).findViewById(R.id.timer_text);
    }

    public void loadModels() {
        // Emit event that model loading started
        eventEmitter.emit(GameEvents.MODEL_LOADING_STARTED, "game_models");

        // Your model loading code here
        // ...

        // When done loading, emit event
        eventEmitter.emit(GameEvents.MODEL_LOADED, "game_models");
    }

    public void placeModel() {
        // Place your model in the scene
        // ...

        // Emit event that model has been placed
        eventEmitter.emit(GameEvents.MODEL_LOADED, "model_placed");
    }

    public void startGameTimer(int seconds) {
        // Cancel any existing timer
        if (gameTimer != null) {
            gameTimer.cancel();
        }

        // Emit timer started event
        eventEmitter.emit(GameEvents.GAME_TIMER_STARTED, seconds);

        // Create and start new timer
        gameTimer = new CountDownTimer(seconds * 1000, 1000) {
            @Override
            public void onTick(long millisUntilFinished) {
                int secondsRemaining = (int) (millisUntilFinished / 1000);

                // If you have a timer text view, update it
                if (timerText != null) {
                    timerText.setText("Time: " + secondsRemaining);
                }

                // Emit tick event
                eventEmitter.emit(GameEvents.GAME_TIMER_TICK, secondsRemaining);
            }

            @Override
            public void onFinish() {
                if (timerText != null) {
                    timerText.setText("Time's up!");
                }

                // Emit timer finished event
                eventEmitter.emit(GameEvents.GAME_TIMER_FINISHED, currentScore);
            }
        }.start();
    }

    public void updateScore(int points) {
        currentScore += points;

        // Emit score updated event
        eventEmitter.emit(GameEvents.SCORE_UPDATED, currentScore);
    }

    public void restart() {
        // Clear the scene
        clearAllNodes();

        // Reset score
        currentScore = 0;
        eventEmitter.emit(GameEvents.SCORE_UPDATED, currentScore);

        // Emit restart event
        eventEmitter.emit(GameEvents.GAME_STARTED, null);
    }

    public void clearAllNodes() {
        // First, emit event that we're clearing nodes
        eventEmitter.emit(GameEvents.CLEARING_SCENE_NODES, sceneNodes.size());

        // Clear our tracked nodes
        for (Node node : sceneNodes) {
            if (node != null) {
                node.setParent(null);
            }
        }
        sceneNodes.clear();

        // For any other nodes, remove them too (safety)
        if (arFragment != null && arFragment.getArSceneView() != null) {
            List<Node> children = new ArrayList<>(arFragment.getArSceneView().getScene().getChildren());
            for (Node node : children) {
                // Skip the camera node
                if (!(node.equals(arFragment.getArSceneView().getScene().getCamera()))) {
                    node.setParent(null);
                }
            }
        }

        // Emit event that nodes are cleared
        eventEmitter.emit(GameEvents.SCENE_NODES_CLEARED, null);
    }

    public void cleanup() {
        // Cancel timer if it's running
        if (gameTimer != null) {
            gameTimer.cancel();
            gameTimer = null;
        }

        // Clear all nodes
        clearAllNodes();

        // Emit game ended event
        eventEmitter.emit(GameEvents.GAME_ENDED, currentScore);
    }
}