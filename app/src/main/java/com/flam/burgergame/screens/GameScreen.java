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
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.RelativeLayout;
import android.widget.TextView;
import android.widget.Toast;

import com.flam.burgergame.R;
import com.google.ar.sceneform.math.Vector3;
import com.google.ar.sceneform.rendering.ModelRenderable;
import com.google.ar.sceneform.rendering.Renderable;
import com.google.ar.sceneform.rendering.RenderableInstance;
import com.google.ar.sceneform.ux.ArFragment;
import com.google.ar.sceneform.Node;
import com.google.ar.sceneform.animation.ModelAnimator;


import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class GameScreen {


    private ArFragment arFragment;
    private Context context;

    private final List<Renderable> loadedModels = new ArrayList<>();
    private int score = 0;
    private TextView scoreText;
    private final Set<Node> tappedNodes = new HashSet<>();

    private TextView timerText;
    private CountDownTimer countDownTimer;

    public GameScreen(Context context, ArFragment arFragment){
        this.context = context;
        this.arFragment = arFragment;

        timerText = ((Activity) context).findViewById(R.id.timerText);
        scoreText = ((Activity) context).findViewById(R.id.scoreText);
    }

    public void loadModels() {
        String[] modelUris = {
                "models/prop_burger.glb",
                "models/prop_coke.glb",
                "models/prop_chickenWings.glb",
        };

        WeakReference<GameScreen> weakActivity = new WeakReference<>(this);

        for (String uri : modelUris) {
            ModelRenderable.builder()
                    .setSource(this.context, Uri.parse(uri))
                    .setIsFilamentGltf(true)
                    .setAsyncLoadEnabled(false)
                    .build()
                    .thenAccept(renderable -> {
                        loadedModels.add(renderable);
                        if (loadedModels.size() == modelUris.length) {
                            placeModel(); // Only when all models are loaded
                        }
                    })
                    .exceptionally(throwable -> {
                        Toast.makeText(this.context, "Failed to load " + uri, Toast.LENGTH_LONG).show();
                        return null;
                    });
        }
    }

    public void placeModel() {
        Log.e("place","logged");

        List<Vector3> spherePoints = generateSpherePoints(20, 1.0f); // 20 non-overlapping points

        for (Vector3 pos : spherePoints) {
            Renderable chosenModel = loadedModels.get((int)(Math.random() * loadedModels.size()));
            placeModelAt(pos, chosenModel);
        }

        arFragment.getArSceneView().getScene().addOnPeekTouchListener((hitTestResult, motionEvent) -> {
            if (motionEvent.getAction() == MotionEvent.ACTION_UP) {
                Node tappedNode = hitTestResult.getNode();
                if (tappedNode != null && loadedModels.contains(tappedNode.getRenderable())) {
                    animateNodeScaleAndRemove(tappedNode);
                }
            }
        });
    }

    private void placeModelAt(Vector3 position, Renderable renderable) {
        Node node = new Node();
        node.setRenderable(renderable);
        node.setParent(arFragment.getArSceneView().getScene());
        node.setWorldPosition(position);
        node.setLocalScale(new Vector3(1f, 1f, 1f));

        // Get the RenderableInstance from the Node (after setRenderable)
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
        double increment = Math.PI * (3.0 - Math.sqrt(5.0)); // golden angle

        for (int i = 0; i < count; i++) {
            double y = ((i * offset) - 1) + (offset / 2);
            double r = Math.sqrt(1 - y * y);
            double phi = i * increment;

            float x = (float) (Math.cos(phi) * r * radius);
            float z = (float) (Math.sin(phi) * r * radius);
            float yScaled = (float) y * radius;

            points.add(new Vector3(x, yScaled, z));
        }

        return points;
    }


    private void updateScoreText() {
        scoreText.setText(String.valueOf(score));
    }

    public void startGameTimer(int seconds) {
        countDownTimer = new CountDownTimer(seconds * 1000L, 1000L) {
            @Override
            public void onTick(long millisUntilFinished) {
                int secondsLeft = (int) (millisUntilFinished / 1000);
                timerText.setText(String.valueOf(secondsLeft));
            }

            @Override
            public void onFinish() {
                timerText.setText("0");
                clearAllNodes();
                showFinalScoreUI();
            }

        };
        countDownTimer.start();
    }

    private void clearAllNodes() {
        arFragment.getArSceneView().getScene().callOnHierarchy(node -> {
            if (loadedModels.contains(node.getRenderable())) {
                node.setParent(null); // remove AR object
            }
        });
    }

    private void showFinalScoreUI() {
        Activity activity = (Activity) context;

        // Hide other UI elements
        activity.findViewById(R.id.gameHelper).setVisibility(View.GONE);
        activity.findViewById(R.id.endScreen).setVisibility(View.VISIBLE);
        activity.findViewById(R.id.restartButton).setVisibility(View.VISIBLE);

        ImageView scorecard = activity.findViewById(R.id.gamescore);
        TextView scoreText = activity.findViewById(R.id.scoreText);


        // Get layout params
        RelativeLayout.LayoutParams paramsScorecard = (RelativeLayout.LayoutParams) scorecard.getLayoutParams();
        RelativeLayout.LayoutParams paramsScoreText = (RelativeLayout.LayoutParams) scoreText.getLayoutParams();

        // Convert 130dp to pixels
        int widthInPx = (int) TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_DIP, 130, scorecard.getResources().getDisplayMetrics());
        paramsScorecard.width = widthInPx;

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
        paramsScoreText.bottomMargin = 0;

        scoreText.setLayoutParams(paramsScoreText);
    }

    public void restart(){
        Activity activity = (Activity) context;

        clearAllNodes();

        score = 0;
        updateScoreText();

        // Hide other UI elements
        activity.findViewById(R.id.gameHelper).setVisibility(View.VISIBLE);
        activity.findViewById(R.id.endScreen).setVisibility(View.GONE);
        activity.findViewById(R.id.restartButton).setVisibility(View.GONE);

        ImageView scorecard = activity.findViewById(R.id.gamescore);
        TextView scoreText = activity.findViewById(R.id.scoreText);


        // Get layout params
        RelativeLayout.LayoutParams paramsScorecard = (RelativeLayout.LayoutParams) scorecard.getLayoutParams();
        RelativeLayout.LayoutParams paramsScoreText = (RelativeLayout.LayoutParams) scoreText.getLayoutParams();

        // Convert 130dp to pixels
        int widthInPx = (int) TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_DIP, 100, scorecard.getResources().getDisplayMetrics());
        paramsScorecard.width = widthInPx;

        // Begin transition animation
        ViewGroup parent = (ViewGroup) scorecard.getParent();
        TransitionManager.beginDelayedTransition(parent);

        // Update layout rules
        paramsScorecard.addRule(RelativeLayout.ALIGN_PARENT_BOTTOM);
        paramsScorecard.addRule(RelativeLayout.ALIGN_PARENT_END);
        paramsScorecard.removeRule(RelativeLayout.CENTER_HORIZONTAL); // center scorecard

        scorecard.setLayoutParams(paramsScorecard);

        // Update scoreText layout
        paramsScoreText.addRule(RelativeLayout.ALIGN_BOTTOM, R.id.gamescore);
        paramsScoreText.removeRule(RelativeLayout.CENTER_VERTICAL);
        scoreText.setTextSize(TypedValue.COMPLEX_UNIT_SP, 18);
        paramsScoreText.bottomMargin = (int) TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_SP, 14, scorecard.getResources().getDisplayMetrics());

        scoreText.setLayoutParams(paramsScoreText);
        paramsScoreText.removeRule(RelativeLayout.CENTER_VERTICAL);
        scoreText.setTextSize(TypedValue.COMPLEX_UNIT_SP, 18);



        paramsScoreText.bottomMargin = (int) TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_SP, 14, scorecard.getResources().getDisplayMetrics());

        scoreText.setLayoutParams(paramsScoreText);

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
                node.setParent(null); // removes node from scene
            }
        });

        scaleAnimator.start();

        score += 1;
        updateScoreText();
    }

}
