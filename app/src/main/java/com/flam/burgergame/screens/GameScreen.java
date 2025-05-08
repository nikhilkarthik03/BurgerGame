package com.flam.burgergame.screens;

import android.animation.ObjectAnimator;
import android.animation.ValueAnimator;
import android.app.Activity;
import android.content.Context;
import android.net.Uri;
import android.os.CountDownTimer;
import android.transition.TransitionManager;
import android.util.Log;
import android.util.TypedValue;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.RelativeLayout;
import android.widget.TextView;
import android.widget.Toast;

import com.flam.burgergame.R;
import com.flam.burgergame.utils.EventEmitter;
import com.flam.burgergame.utils.GameEvents;
import com.google.ar.sceneform.Node;
import com.google.ar.sceneform.animation.ModelAnimator;
import com.google.ar.sceneform.math.Quaternion;
import com.google.ar.sceneform.math.Vector3;
import com.google.ar.sceneform.rendering.ModelRenderable;
import com.google.ar.sceneform.rendering.Renderable;
import com.google.ar.sceneform.rendering.RenderableInstance;
import com.google.ar.sceneform.ux.ArFragment;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class GameScreen {

    private final Context context;
    private final ArFragment arFragment;
    private final EventEmitter eventEmitter;
    private CountDownTimer gameTimer;
    private int currentScore = 0;
    private final List<Node> sceneNodes = new ArrayList<>();
    private final List<Renderable> loadedModels = new ArrayList<>();
    private final Map<Renderable, String> modelTypeMap = new HashMap<>();
    private final Set<Node> tappedNodes = new HashSet<>();

    // Add a map to store node to model type associations
    private final Map<Node, String> nodeModelTypeMap = new HashMap<>();

    // Reference to UI elements
    private final TextView timerText;
    private final TextView scoreText;

    // Model URIs
    private static final String BURGER_MODEL = "models/prop_burger.glb";
    private static final String COKE_MODEL = "models/prop_coke.glb";
    private static final String WINGS_MODEL = "models/prop_chickenWings.glb";

    public GameScreen(Context context, ArFragment arFragment) {
        this.context = context;
        this.arFragment = arFragment;
        this.eventEmitter = EventEmitter.getInstance();

        // Set up UI references
        timerText = ((Activity) context).findViewById(R.id.timerText);
        scoreText = ((Activity) context).findViewById(R.id.scoreText);
    }

    public void loadModels() {
        // Emit event that model loading started
        eventEmitter.emit(GameEvents.MODEL_LOADING_STARTED, "game_models");

        String[] modelUris = {
                BURGER_MODEL,
                COKE_MODEL,
                WINGS_MODEL,
        };

        WeakReference<GameScreen> weakReference = new WeakReference<>(this);

        for (String uri : modelUris) {
            ModelRenderable.builder()
                    .setSource(this.context, Uri.parse(uri))
                    .setIsFilamentGltf(true)
                    .setAsyncLoadEnabled(false)
                    .build()
                    .thenAccept(renderable -> {
                        GameScreen gameScreen = weakReference.get();
                        if (gameScreen != null) {
                            loadedModels.add(renderable);

                            // Map model to its type
                            gameScreen.modelTypeMap.put(renderable, uri);

                            // If all models are loaded, emit model loaded event
                            if (loadedModels.size() == modelUris.length) {
                                eventEmitter.emit(GameEvents.MODEL_LOADED, "game_models");
                                placeModel();
                            }
                        }
                    })
                    .exceptionally(throwable -> {
                        Toast.makeText(this.context, "Failed to load " + uri, Toast.LENGTH_LONG).show();
                        return null;
                    });
        }
    }

    public void placeModel() {
        List<Vector3> spherePoints = generateSpherePoints(20, 1.0f);

        for (Vector3 pos : spherePoints) {
            if (loadedModels.isEmpty()) {
                break;
            }

            int randomModelIndex = (int) (Math.random() * loadedModels.size());
            Renderable model = loadedModels.get(randomModelIndex);

            placeModelAtPosition(model, pos);
        }

        eventEmitter.emit(GameEvents.MODEL_LOADED, "model_placed");
    }

    private void placeModelAtPosition(Renderable model, Vector3 position) {
        Node node = new Node();
        node.setRenderable(model);

        String modelType = modelTypeMap.get(model);
        nodeModelTypeMap.put(node, modelType);

        sceneNodes.add(node);

        node.setParent(arFragment.getArSceneView().getScene());
        node.setWorldPosition(position);
        node.setLocalScale(new Vector3(1f, 1f, 1f));

        node.setOnTapListener((hitTestResult, motionEvent) -> {
            animateNodeScaleAndRemove(node);
        });

        RenderableInstance renderableInstance = node.getRenderableInstance();
        if (renderableInstance != null) {
            renderableInstance.setCulling(false);

            if (renderableInstance.hasAnimations()) {
                List<String> anims = renderableInstance.getAnimationNames();
                if (!anims.isEmpty()) {
                    ObjectAnimator animator = ModelAnimator.ofAnimation(renderableInstance, anims.get(0));
                    animator.setRepeatCount(ObjectAnimator.INFINITE);
                    animator.start();
                }
            }
        }
    }

    private List<Vector3> generateSpherePoints(int count, float radius) {
        List<Vector3> points = new ArrayList<>();
        double offset = 2.0 / count;
        double increment = Math.PI * (3.0 - Math.sqrt(5.0));

        for (int i = 0; i < count; i++) {
            double y = ((i * offset) - 1) + (offset / 2);
            double r = Math.sqrt(1 - y * y);
            double phi = i * increment;

            double x = Math.cos(phi) * r;
            double z = Math.sin(phi) * r;

            points.add(new Vector3((float) (x * radius), (float) (y * radius), (float) (z * radius)));
        }

        return points;
    }

    public void updateScore(int points) {
        currentScore += points;
        updateScoreText();

        // Emit score updated event
//        eventEmitter.emit(GameEvents.SCORE_UPDATED, currentScore);
    }

    private void updateScoreText() {
        if (scoreText != null) {
            scoreText.setText(String.valueOf(currentScore));
        }
    }

    public void startGameTimer(int seconds) {
        // Cancel any existing timer
        if (gameTimer != null) {
            gameTimer.cancel();
        }

        // Emit timer started event
        eventEmitter.emit(GameEvents.GAME_TIMER_STARTED, seconds);

        // Create and start new timer
        gameTimer = new CountDownTimer(seconds * 1000L, 1000L) {
            @Override
            public void onTick(long millisUntilFinished) {
                int secondsRemaining = (int) (millisUntilFinished / 1000);

                // If you have a timer text view, update it
                if (timerText != null) {
                    timerText.setText(String.valueOf(secondsRemaining));
                }

                // Emit tick event
//                eventEmitter.emit(GameEvents.GAME_TIMER_TICK, secondsRemaining);
            }

            @Override
            public void onFinish() {
                clearAllNodes();

                // Show final score UI
                showFinalScoreUI();

                // Emit timer finished event
                eventEmitter.emit(GameEvents.GAME_TIMER_FINISHED, currentScore);
            }
        }.start();
    }

    private void showFinalScoreUI() {
        Activity activity = (Activity) context;

        // Hide other UI elements
        activity.findViewById(R.id.gameHelper).setVisibility(View.GONE);
        activity.findViewById(R.id.endScreen).setVisibility(View.VISIBLE);
        activity.findViewById(R.id.restartButton).setVisibility(View.VISIBLE);

        ImageView scorecard = activity.findViewById(R.id.gamescore);
        TextView scoreText = activity.findViewById(R.id.scoreText);

        scorecard.setVisibility(View.VISIBLE);
        scoreText.setVisibility(View.VISIBLE);

        // Get layout params
        RelativeLayout.LayoutParams paramsScorecard = (RelativeLayout.LayoutParams) scorecard.getLayoutParams();
        RelativeLayout.LayoutParams paramsScoreText = (RelativeLayout.LayoutParams) scoreText.getLayoutParams();

        // Convert to bigger size
        paramsScorecard.width = (int) TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_DIP, 150, scorecard.getResources().getDisplayMetrics());

        // Begin transition animation
        ViewGroup parent = (ViewGroup) scorecard.getParent();
        TransitionManager.beginDelayedTransition(parent);

        // Update layout rules
        paramsScorecard.removeRule(RelativeLayout.ALIGN_PARENT_BOTTOM);
        paramsScorecard.removeRule(RelativeLayout.ALIGN_PARENT_END);
        paramsScorecard.addRule(RelativeLayout.CENTER_HORIZONTAL); // center scorecard

        scorecard.setLayoutParams(paramsScorecard);

        // Update scoreText layout
        paramsScoreText.removeRule(RelativeLayout.ALIGN_BOTTOM);
        paramsScoreText.addRule(RelativeLayout.CENTER_VERTICAL);
        scoreText.setTextSize(TypedValue.COMPLEX_UNIT_SP, 24);
        scoreText.setLayoutParams(paramsScoreText);
    }

    public void restart() {
        Activity activity = (Activity) context;

        // Clear the scene
        clearAllNodes();

        // Reset score
        currentScore = 0;
        updateScoreText();

        // Reset tapped nodes tracking
        tappedNodes.clear();

        // Reset UI elements
        activity.findViewById(R.id.gameHelper).setVisibility(View.VISIBLE);
        activity.findViewById(R.id.endScreen).setVisibility(View.GONE);
        activity.findViewById(R.id.restartButton).setVisibility(View.GONE);

        ImageView scorecard = activity.findViewById(R.id.gamescore);
        TextView scoreText = activity.findViewById(R.id.scoreText);

        // Get layout params
        RelativeLayout.LayoutParams paramsScorecard = (RelativeLayout.LayoutParams) scorecard.getLayoutParams();
        RelativeLayout.LayoutParams paramsScoreText = (RelativeLayout.LayoutParams) scoreText.getLayoutParams();

        // Convert to original size
        paramsScorecard.width = (int) TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_DIP, 100, scorecard.getResources().getDisplayMetrics());

        // Begin transition animation
        ViewGroup parent = (ViewGroup) scorecard.getParent();
        TransitionManager.beginDelayedTransition(parent);

        // Update layout rules
        paramsScorecard.addRule(RelativeLayout.ALIGN_PARENT_BOTTOM);
        paramsScorecard.addRule(RelativeLayout.ALIGN_PARENT_END);
        paramsScorecard.removeRule(RelativeLayout.CENTER_HORIZONTAL);

        scorecard.setLayoutParams(paramsScorecard);

        // Update scoreText layout
        paramsScoreText.addRule(RelativeLayout.ALIGN_BOTTOM, R.id.gamescore);
        paramsScoreText.removeRule(RelativeLayout.CENTER_VERTICAL);
        scoreText.setTextSize(TypedValue.COMPLEX_UNIT_SP, 18);
        paramsScoreText.bottomMargin = (int) TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_SP, 14, scorecard.getResources().getDisplayMetrics());

        scoreText.setLayoutParams(paramsScoreText);

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
                // Also remove from our model type map
                nodeModelTypeMap.remove(node);
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
                    // Also remove from our model type map if present
                    nodeModelTypeMap.remove(node);
                }
            }
        }

        // Emit event that nodes are cleared
        eventEmitter.emit(GameEvents.SCENE_NODES_CLEARED, null);
    }

    private void animateNodeScaleAndRemove(Node node) {
        // Prevent re-tapping already processed nodes
        if (tappedNodes.contains(node)) return;

        tappedNodes.add(node); // Mark this node as handled

        final float initialScale = 1f;
        ValueAnimator scaleAnimator = ValueAnimator.ofFloat(initialScale, 1.1f, 0f);
        scaleAnimator.setDuration(500); // milliseconds

        scaleAnimator.addUpdateListener(animation -> {
            float scale = (float) animation.getAnimatedValue();
            node.setLocalScale(new Vector3(scale, scale, scale));
        });

        scaleAnimator.addListener(new android.animation.AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(android.animation.Animator animation) {
                node.setParent(null);
                sceneNodes.remove(node);

                nodeModelTypeMap.remove(node);
            }
        });

        scaleAnimator.start();

        // Check what type of model was tapped and update score accordingly
        String modelType = nodeModelTypeMap.get(node);
        int points;

        if (BURGER_MODEL.equals(modelType)) {
            // Burger - add 100 points
            points = 100;
            Toast.makeText(context, "+100 Points!", Toast.LENGTH_SHORT).show();
        } else {
            // Any other food item - subtract 50 points
            points = -50;
            Toast.makeText(context, "-50 Points!", Toast.LENGTH_SHORT).show();
        }

        updateScore(points);
        updateScoreText();


        eventEmitter.emit(GameEvents.SCORE_UPDATED, currentScore);
    }

    public void cleanup() {
        if (gameTimer != null) {
            gameTimer.cancel();
            gameTimer = null;
        }

        clearAllNodes();

        tappedNodes.clear();
        nodeModelTypeMap.clear();

        eventEmitter.emit(GameEvents.GAME_ENDED, currentScore);
    }
}