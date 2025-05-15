package com.flam.burgergame.screens;

import android.animation.ObjectAnimator;
import android.animation.ValueAnimator;
import android.app.Activity;
import android.content.Context;
import android.media.MediaPlayer;
import android.net.Uri;
import android.os.CountDownTimer;
import android.os.Handler;
import android.transition.TransitionManager;
import android.util.Log;
import android.util.TypedValue;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.RelativeLayout;
import android.widget.TextView;
import android.widget.Toast;

import com.flam.burgergame.MainActivity;
import com.flam.burgergame.R;
import com.flam.burgergame.utils.EventEmitter;
import com.flam.burgergame.utils.GameEvents;
import android.graphics.Color;
import com.google.ar.core.Anchor;
import com.google.ar.core.Frame;
import com.google.ar.core.Pose;
import com.google.ar.core.Session;
import com.google.ar.sceneform.AnchorNode;
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
    private MediaPlayer tapSound, coinSound;

    private CountDownTimer gameTimer;
    private int currentScore = 0;
    private final List<Node> sceneNodes = new ArrayList<>();
    private final List<AnchorNode> anchorNodes = new ArrayList<>(); // Track anchor nodes
    private final List<Renderable> loadedModels = new ArrayList<>();
    private final Map<Renderable, String> modelTypeMap = new HashMap<>();
    private final Set<Node> tappedNodes = new HashSet<>();
    private final Handler respawnHandler = new Handler();
    private boolean isGameActive = false;
    private Anchor centerAnchor; // Main game anchor
    private MediaPlayer countdownSound;

    // Add a map to store node to model type associations
    private final Map<Node, String> nodeModelTypeMap = new HashMap<>();

    // Reference to UI elements
    private final TextView timerText;
    private final TextView scoreText;

    // Model URIs
    private static final String BURGER_MODEL = "models/Burger_Blast.glb";
    private static final String COKE_MODEL = "models/prop_coke.glb";
    private static final String WINGS_MODEL = "models/prop_chickenWings.glb";

    private final Handler imageChangeHandler = new Handler();

    // Respawn delay in milliseconds
    private static final int RESPAWN_DELAY = 1500; // 1.5 seconds

    // Remaining game time in seconds
    private int remainingGameTime = 0;

    private final Map<Integer, String> imageToModelMap = new HashMap<>();
    private int currentTargetImageId = -1; // holds the currently displayed image drawable id



    public GameScreen(Context context, ArFragment arFragment) {
        this.context = context;
        this.arFragment = arFragment;
        this.eventEmitter = EventEmitter.getInstance();

        // Set up UI references
        timerText = ((Activity) context).findViewById(R.id.timerText);
        scoreText = ((Activity) context).findViewById(R.id.scoreText);

        imageToModelMap.put(R.drawable.homebutton, BURGER_MODEL);
        imageToModelMap.put(R.drawable.coke, COKE_MODEL);
        imageToModelMap.put(R.drawable.wings, WINGS_MODEL);
    }

    public void loadModels() {
        // Emit event that model loading started
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
                                createCenterAnchor();
                            }
                        }
                    })
                    .exceptionally(throwable -> {
                        Toast.makeText(this.context, "Failed to load " + uri, Toast.LENGTH_LONG).show();
                        return null;
                    });
        }
    }

    private void createCenterAnchor() {
        if (arFragment == null || arFragment.getArSceneView() == null ||
                arFragment.getArSceneView().getArFrame() == null) {
            new Handler().postDelayed(this::createCenterAnchor, 500);
            return;
        }

        try {
            Session session = arFragment.getArSceneView().getSession();
            Frame frame = arFragment.getArSceneView().getArFrame();

            if (session == null || frame == null) {
                new Handler().postDelayed(this::createCenterAnchor, 500);
                return;
            }

            Pose cameraPose = frame.getCamera().getPose();
            float[] cameraPosition = cameraPose.getTranslation();
            float[] forward = cameraPose.getZAxis(); // points backward, so negate it

            float x = cameraPosition[0]; // move 2m forward
            float y = cameraPosition[1] - 0.5f; // slightly below eye level
            float z = cameraPosition[2];

            Pose anchorPose = Pose.makeTranslation(x, y, z);
            centerAnchor = session.createAnchor(anchorPose);

            placeModel();
        } catch (Exception e) {
            Log.e("GameScreen", "Error creating anchor: " + e.getMessage());
            new Handler().postDelayed(this::createCenterAnchor, 1000);
        }
    }

    public void placeModel() {
        if (centerAnchor == null) {
            createCenterAnchor();
            return;
        }

        // Clear existing nodes first
        clearAllNodes();

        // Create an anchor node for our main anchor
        AnchorNode anchorNode = new AnchorNode(centerAnchor);
        anchorNode.setParent(arFragment.getArSceneView().getScene());
        anchorNodes.add(anchorNode);

        List<Vector3> spherePoints = generateSpherePoints(20, 1.5f);

        for (Vector3 pos : spherePoints) {
            if (loadedModels.isEmpty()) {
                break;
            }

            int randomModelIndex = (int) (Math.random() * loadedModels.size());
            Renderable model = loadedModels.get(randomModelIndex);

            placeModelAtPosition(model, pos, anchorNode);
        }
    }

    private void placeModelAtPosition(Renderable model, Vector3 position, AnchorNode parentAnchor) {
        Node node = new Node();
        node.setRenderable(model);

        String modelType = modelTypeMap.get(model);
        nodeModelTypeMap.put(node, modelType);

        sceneNodes.add(node);

        // Attach to the anchor node instead of directly to the scene
        node.setParent(parentAnchor);
        node.setLocalPosition(position);
        node.setLocalScale(new Vector3(2f, 2f, 2f)); // Make models a bit smaller

        node.setOnTapListener((hitTestResult, motionEvent) -> {
            animateNodeScaleAndRemove(node);
        });
    }

    private void randomizeTargetImage() {
        List<Integer> drawableIds = new ArrayList<>(imageToModelMap.keySet());
        int randomIndex = (int) (Math.random() * drawableIds.size());
        currentTargetImageId = drawableIds.get(randomIndex);

        ImageView homeIcon = ((Activity) context).findViewById(R.id.home);
        homeIcon.setImageResource(currentTargetImageId);
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
        int previousScore = currentScore;
        currentScore += points;
        updateScoreText();

        // Check if player crossed the 1000 points milestone
        if (previousScore < 1000 && currentScore >= 1000) {
            addBonusTime(10);
            showBonusTimeToast(10);
        }

        // Check for additional milestones (optional)
        // For example, add 10 more seconds at 2000 points
        if (previousScore < 2000 && currentScore >= 2000) {
            addBonusTime(10);
            showBonusTimeToast(10);
        }

        // Additional milestones can be added here
        if (previousScore < 3000 && currentScore >= 3000) {
            addBonusTime(15);
            showBonusTimeToast(15);
        }

//        coinSound = MediaPlayer.create(this.context, R.raw.coins); // Use your filename
//        coinSound.setLooping(false); // Optional: Loop BGM
//        coinSound.setVolume(0.7f, 0.7f); // Optional: Set volume (left, right)
//        coinSound.start();

        // Emit score updated event
    }

    private void updateScoreText() {
        if (scoreText != null) {
            scoreText.setText(String.valueOf(currentScore));
        }
    }

    /**
     * Adds bonus time to the current game timer
     * @param secondsToAdd Number of seconds to add to the timer
     */
    public void addBonusTime(int secondsToAdd) {
        // Cancel existing timer
        if (gameTimer != null) {
            gameTimer.cancel();
        }

        // Add time to the remaining time
        remainingGameTime += secondsToAdd;

        // Create and start a new timer with updated time
        startGameTimerWithRemaining(remainingGameTime);

        // Emit event for bonus time added
//        eventEmitter.emit(GameEvents.BONUS_TIME_ADDED, secondsToAdd);
    }

    private void scheduleTargetImageRotation() {
        imageChangeHandler.postDelayed(new Runnable() {
            @Override
            public void run() {
                if (!isGameActive) return;

                randomizeTargetImage(); // change the image

                // Schedule next run randomly between 5–8 seconds
                int delay = 3000 + (int)(Math.random() * 1000); // 5000–8000ms
                imageChangeHandler.postDelayed(this, delay);
            }
        }, 1000); // initial delay before first change (optional)
    }


    public void startGameTimer(int seconds) {
        // Store initial game time
        remainingGameTime = seconds;

        // Start timer with initial seconds
        startGameTimerWithRemaining(seconds);
    }

    private void startGameTimerWithRemaining(int seconds) {
        // Cancel any existing timer
        if (gameTimer != null) {
            gameTimer.cancel();
        }

        // Set game as active
        isGameActive = true;

        randomizeTargetImage(); // set one immediately
        scheduleTargetImageRotation(); // start rotation


        // Emit timer started event

        // Create and start new timer
        gameTimer = new CountDownTimer(seconds * 1000L, 1000L) {
            @Override
            public void onTick(long millisUntilFinished) {
                remainingGameTime = (int) (millisUntilFinished / 1000);

                if (timerText != null) {
                    timerText.setText(String.valueOf(remainingGameTime));
                }

                // Play countdown sound when 3 seconds remain
                if (remainingGameTime == 3) {
                    countdownSound = MediaPlayer.create(context, R.raw.countdown); // Place countdown.mp3 as res/raw/countdown.mp3
                    countdownSound.setLooping(false);
                    countdownSound.setVolume(0.75f, 0.75f);
                    countdownSound.start();
                }
            }


            @Override
            public void onFinish() {
                isGameActive = false;
                clearAllNodes();

                // Stop the background music
                if (context instanceof MainActivity) {
                    ((MainActivity) context).stopBackgroundMusic();
                }

                MediaPlayer endSound;
                endSound = MediaPlayer.create(context, R.raw.win); // Use your filename
                endSound.setLooping(false); // Optional: Loop BGM
                endSound.setVolume(0.5f, 0.5f); // Optional: Set volume (left, right)
                endSound.start();


                // Show final score UI
                showFinalScoreUI();
            }

        }.start();
    }

    private void animateStars() {
        ImageView star1 = ((Activity) context).findViewById(R.id.star1);
        ImageView star2 = ((Activity) context).findViewById(R.id.star2);
        ImageView star3 = ((Activity) context).findViewById(R.id.star3);

        animateStar(star1, 0);
        animateStar(star2, 300);
        animateStar(star3, 600);
    }

    private void animateStar(ImageView star, long delay) {
        star.setVisibility(View.VISIBLE);
        star.animate()
                .scaleX(1f)
                .scaleY(1f)
                .setDuration(400)
                .setStartDelay(delay)
                .start();
    }


    private void showFinalScoreUI() {
        Activity activity = (Activity) context;

        animateStars();

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
        scoreText.setTextSize(TypedValue.COMPLEX_UNIT_SP, 20);
        scoreText.setLayoutParams(paramsScoreText);
    }

    /**
     * Shows a special toast for bonus time
     * @param seconds Number of seconds added
     */
    private void showBonusTimeToast(int seconds) {
        Toast toast = Toast.makeText(context, "BONUS: +" + seconds + " seconds!", Toast.LENGTH_LONG);
        View view = toast.getView();

        // Customize toast appearance if available on this Android version
        if (view != null) {
            // Set a background color
            view.setBackgroundColor(Color.rgb(0, 200, 0));

            // Find the TextView within the Toast
            TextView text = view.findViewById(android.R.id.message);
            if (text != null) {
                text.setTextColor(Color.RED);
                text.setTextSize(18);
            }
        }

        toast.show();

        // Flash the timer text to indicate bonus
        if (timerText != null) {
            // Flash timer text by changing colors
            ObjectAnimator colorAnim = ObjectAnimator.ofArgb(timerText, "textColor",
                    Color.RED, Color.GREEN, Color.RED);
            colorAnim.setDuration(1000);
            colorAnim.setRepeatCount(1);
            colorAnim.start();
        }
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

        // Reset remaining time
        remainingGameTime = 0;

        // Set game as active
        isGameActive = true;


        if (context instanceof MainActivity) {
            ((MainActivity) context).restartBackgroundMusic();
        }

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
                TypedValue.COMPLEX_UNIT_SP, 6, scorecard.getResources().getDisplayMetrics());

        scoreText.setLayoutParams(paramsScoreText);

        // Create a new center anchor and place models
        createCenterAnchor();

        // Emit restart event
    }

    public void clearAllNodes() {
        // First, emit event that we're clearing nodes

        // Clear our tracked nodes
        for (Node node : sceneNodes) {
            if (node != null) {
                node.setParent(null);
                // Also remove from our model type map
                nodeModelTypeMap.remove(node);
            }
        }
        sceneNodes.clear();

        // Clear anchor nodes
        for (AnchorNode anchorNode : anchorNodes) {
            if (anchorNode != null) {
                if (anchorNode.getAnchor() != null) {
                    anchorNode.getAnchor().detach();
                }
                anchorNode.setParent(null);
            }
        }
        anchorNodes.clear();

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
    }

    private void removeNodeAndRespawn(Node node, Vector3 position, AnchorNode parentAnchor) {
        node.setParent(null);
        sceneNodes.remove(node);
        nodeModelTypeMap.remove(node);

        // Respawn a new model after 500ms
        respawnHandler.postDelayed(() -> {
            if (!isGameActive || loadedModels.isEmpty() || parentAnchor == null) return;

            int randomModelIndex = (int) (Math.random() * loadedModels.size());
            Renderable model = loadedModels.get(randomModelIndex);
            placeModelAtPosition(model, position, parentAnchor);
        }, 500);
    }


    private void animateNodeScaleAndRemove(Node node) {
        if (tappedNodes.contains(node)) return;
        tappedNodes.add(node);

        // Find the parent anchor node
        Node parent = (Node) node.getParent();
        while (parent != null && !(parent instanceof AnchorNode)) {
            parent = (Node) parent.getParent();
        }
        AnchorNode parentAnchor = (parent instanceof AnchorNode) ? (AnchorNode) parent : null;

        String modelType = nodeModelTypeMap.get(node);
        int points;

        String correctModel = imageToModelMap.get(currentTargetImageId);
        if (correctModel != null && correctModel.equals(modelType)) {
            points = 100;
            tapSound = MediaPlayer.create(this.context, R.raw.pos); // Use your filename
            tapSound.setLooping(false); // Optional: Loop BGM
            tapSound.setVolume(0.7f, 0.7f); // Optional: Set volume (left, right)
            tapSound.start();
            Toast.makeText(context, "+100 Points!", Toast.LENGTH_SHORT).show();
        } else {
            points = -50;
            tapSound = MediaPlayer.create(this.context, R.raw.neg); // Use your filename
            tapSound.setLooping(false); // Optional: Loop BGM
            tapSound.setVolume(0.7f, 0.7f); // Optional: Set volume (left, right)
            tapSound.start();
            Toast.makeText(context, "-50 Points!", Toast.LENGTH_SHORT).show();
        }



        updateScore(points);
        updateScoreText();


        Vector3 oldPosition = node.getLocalPosition();

        RenderableInstance renderableInstance = node.getRenderableInstance();
        if (renderableInstance != null && renderableInstance.hasAnimations()) {
            List<String> animations = renderableInstance.getAnimationNames();
            if (!animations.isEmpty()) {
                ObjectAnimator animator = ModelAnimator.ofAnimation(renderableInstance, animations.get(0));
                animator.setRepeatCount(0);
                animator.addListener(new android.animation.AnimatorListenerAdapter() {
                    @Override
                    public void onAnimationEnd(android.animation.Animator animation) {
                        removeNodeAndRespawn(node, oldPosition, parentAnchor);
                    }
                });
                animator.start();
                return;
            }
        }

        removeNodeAndRespawn(node, oldPosition, parentAnchor);
    }

    public void cleanup() {
        isGameActive = false;

        imageChangeHandler.removeCallbacksAndMessages(null);

        if (gameTimer != null) {
            gameTimer.cancel();
            gameTimer = null;
        }

        // Remove any pending respawn callbacks
        respawnHandler.removeCallbacksAndMessages(null);

        clearAllNodes();

        // Clean up center anchor if it exists
        if (centerAnchor != null) {
            centerAnchor.detach();
            centerAnchor = null;
        }

        tappedNodes.clear();
        nodeModelTypeMap.clear();
    }
}