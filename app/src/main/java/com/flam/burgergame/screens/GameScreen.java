package com.flam.burgergame.screens;

import android.animation.ValueAnimator;
import android.net.Uri;
import android.os.Bundle;
import android.os.CountDownTimer;
import android.transition.TransitionManager;
import android.util.TypedValue;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.RelativeLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentContainerView;
import androidx.fragment.app.FragmentManager;
import androidx.fragment.app.FragmentOnAttachListener;

import com.flam.burgergame.R;
import com.google.ar.core.Config;
import com.google.ar.core.Session;
import com.google.ar.sceneform.ArSceneView;
import com.google.ar.sceneform.Sceneform;
import com.google.ar.sceneform.math.Vector3;
import com.google.ar.sceneform.rendering.ModelRenderable;
import com.google.ar.sceneform.rendering.Renderable;
import com.google.ar.sceneform.ux.ArFragment;
import com.google.ar.sceneform.ux.BaseArFragment;
import com.google.ar.sceneform.Node;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class GameScreen extends AppCompatActivity implements
        FragmentOnAttachListener,
        BaseArFragment.OnSessionConfigurationListener,
        ArFragment.OnViewCreatedListener {

    private ArFragment arFragment;
    private final List<Renderable> loadedModels = new ArrayList<>();
    private int score = 0;
    private TextView scoreText;
    private final Set<Node> tappedNodes = new HashSet<>();

    private TextView timerText;
    private CountDownTimer countDownTimer;
    private static final int GAME_DURATION = 10; // seconds


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        if (!Sceneform.isSupported(this)) {
            Toast.makeText(this, "Sceneform is not supported on this device.", Toast.LENGTH_LONG).show();
            finish();
            return;
        }

        setContentView(R.layout.game_screen);

        scoreText = findViewById(R.id.scoreText);

        timerText = findViewById(R.id.timerText);
        startGameTimer(GAME_DURATION);


        // Register this activity to listen when fragments are attached
        getSupportFragmentManager().addFragmentOnAttachListener(this);


        FragmentContainerView arFragmentContainer = findViewById(R.id.arFragment);

        // Launch the AR fragment immediately
        launchArFragment();

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
        config.setLightEstimationMode(Config.LightEstimationMode.ENVIRONMENTAL_HDR);

    }

    @Override
    public void onViewCreated(ArSceneView arSceneView) {
        arFragment.setOnViewCreatedListener(null);
        arSceneView.setFrameRateFactor(ArSceneView.FrameRate.FULL);
        loadModels();

    }

    public void loadModels() {
        String[] modelUris = {
                "models/prop_burger.glb",
                "models/prop_coke.glb",
                "models/prop_chickenWings.glb"
        };

        WeakReference<GameScreen> weakActivity = new WeakReference<>(this);

        for (String uri : modelUris) {
            ModelRenderable.builder()
                    .setSource(this, Uri.parse(uri))
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
                        Toast.makeText(this, "Failed to load " + uri, Toast.LENGTH_LONG).show();
                        return null;
                    });
        }
    }

    private void placeModel() {
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

    private void startGameTimer(int seconds) {
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
        // Hide other UI elements
        findViewById(R.id.timer).setVisibility(View.GONE);
        findViewById(R.id.timerText).setVisibility(View.GONE);
        findViewById(R.id.home).setVisibility(View.GONE);

        // Show final score UI
        ImageView starsBanner = findViewById(R.id.starsbanner);
        ImageView congrats = findViewById(R.id.congrats);
        ImageView scorecard = findViewById(R.id.gamescore);
        TextView scoreText = findViewById(R.id.scoreText);

        starsBanner.setVisibility(View.VISIBLE);
        congrats.setVisibility(View.VISIBLE);
        scorecard.setVisibility(View.VISIBLE);
        scoreText.setVisibility(View.VISIBLE);

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
        paramsScorecard.addRule(RelativeLayout.CENTER_IN_PARENT); // center scorecard

        scorecard.setLayoutParams(paramsScorecard);

        // Update scoreText layout
        paramsScoreText.removeRule(RelativeLayout.ALIGN_BOTTOM);
        paramsScoreText.addRule(RelativeLayout.CENTER_VERTICAL);
        scoreText.setTextSize(TypedValue.COMPLEX_UNIT_SP, 24);
        paramsScoreText.bottomMargin = 0;

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
