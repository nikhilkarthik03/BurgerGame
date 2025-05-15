package com.flam.burgergame.screens;

import android.animation.ObjectAnimator;
import android.content.Context;
import android.net.Uri;
import android.util.Log;
import android.widget.MediaController;
import android.widget.Toast;
import android.widget.VideoView;

import com.flam.burgergame.R;
import com.flam.burgergame.utils.EventEmitter;
import com.flam.burgergame.utils.GameEvents;
import com.flam.burgergame.utils.ModelLoaderManager;
import com.google.ar.sceneform.math.Quaternion;
import com.google.ar.sceneform.math.Vector3;
import com.google.ar.sceneform.rendering.Renderable;
import com.google.ar.sceneform.rendering.RenderableInstance;
import com.google.ar.sceneform.ux.ArFragment;
import com.google.ar.sceneform.Node;
import com.google.ar.sceneform.animation.ModelAnimator;

import java.util.ArrayList;
import java.util.List;

public class Selection {

    private static final String TAG = "Selection";

    private final ArFragment arFragment;
    private final Context context;
    private final EventEmitter eventEmitter;
    private final ModelLoaderManager modelLoaderManager;

    private final List<Node> sceneNodes = new ArrayList<>();

    public Selection(Context context, ArFragment arFragment) {
        this.context = context;
        this.arFragment = arFragment;
        this.eventEmitter = EventEmitter.getInstance();
        this.modelLoaderManager = ModelLoaderManager.getInstance();
    }


//    public void loadModels() {
//        // Emit event that model loading started
//        Log.d(TAG, "Setting up preloaded models for instructions");
//        eventEmitter.emit(GameEvents.MODEL_LOADING_STARTED, "instructions_fries");
//
//        // Get the preloaded fries model
//        Renderable trayModel = modelLoaderManager.getModel(ModelLoaderManager.TRAY_MODEL);
//
//        if (trayModel == null) {
//            String errorMsg = "Preloaded fries model not found!";
//            Log.e(TAG, errorMsg);
//            Toast.makeText(context, errorMsg, Toast.LENGTH_LONG).show();
//            eventEmitter.emit(GameEvents.MODEL_LOAD_ERROR, errorMsg);
//            return;
//        }
//        Node node = new Node();
//        node.setRenderable(trayModel);
//        node.setParent(arFragment.getArSceneView().getScene());
//        float scale = 1f;
//        node.setLocalScale(new Vector3(scale, scale, scale));
//
//        sceneNodes.add(node);
//
//        Quaternion rotationZ = Quaternion.axisAngle(new Vector3(1, 0, 0), 90);
//
//        arFragment.getArSceneView().getScene().addOnUpdateListener(frameTime -> {
//            // Get camera position and forward vector
//            Vector3 cameraPos = arFragment.getArSceneView().getScene().getCamera().getWorldPosition();
//            Vector3 cameraForward = arFragment.getArSceneView().getScene().getCamera().getForward();
//
//            // Position the node 1 meter in front of the camera
//            Vector3 targetPos = Vector3.add(cameraPos, cameraForward.scaled(1.0f));
//            node.setWorldPosition(targetPos);
//
//            // Make node face the camera first
//            node.setLookDirection(Vector3.subtract(targetPos, cameraPos));
//
//            // Then apply the additional 90-degree Z rotation by combining the quaternions
//            Quaternion currentRotation = node.getWorldRotation();
//            Quaternion combinedRotation = Quaternion.multiply(currentRotation, rotationZ);
//            node.setWorldRotation(combinedRotation);
//        });
//
//        // Optional animation logic
//        RenderableInstance renderableInstance = node.getRenderableInstance();
//        if (renderableInstance != null) {
//            renderableInstance.setCulling(false);
//            if (renderableInstance.hasAnimations()) {
//                List<String> anims = renderableInstance.getAnimationNames();
//                if (!anims.isEmpty()) {
//                    ObjectAnimator animator = ModelAnimator.ofAnimation(renderableInstance, anims.get(0));
//                    animator.setRepeatCount(ObjectAnimator.INFINITE);
//                    animator.start();
//
//                    // Emit event that model is animated
//                    eventEmitter.emit(GameEvents.MODEL_ANIMATION_STARTED, anims.get(0));
//                }
//            }
//        }
//    }

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

        // For any other nodes not in our list, remove them too (safety)
        List<Node> children = new ArrayList<>(arFragment.getArSceneView().getScene().getChildren());
        for (Node node : children) {
            // Skip the camera node
            if (!(node.equals(arFragment.getArSceneView().getScene().getCamera()))) {
                node.setParent(null); // This removes the node from the scene
            }
        }

        // Emit event that nodes are cleared
        eventEmitter.emit(GameEvents.SCENE_NODES_CLEARED, null);
    }

    public void cleanup() {
        clearAllNodes();
        // Additional cleanup if needed
    }
}