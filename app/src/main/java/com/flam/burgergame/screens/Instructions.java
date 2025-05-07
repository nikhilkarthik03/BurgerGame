package com.flam.burgergame.screens;

import android.animation.ObjectAnimator;
import android.content.Context;
import android.net.Uri;
import android.widget.Toast;

import com.google.ar.sceneform.math.Vector3;
import com.google.ar.sceneform.rendering.ModelRenderable;
import com.google.ar.sceneform.rendering.RenderableInstance;
import com.google.ar.sceneform.ux.ArFragment;
import com.google.ar.sceneform.Node;
import com.google.ar.sceneform.animation.ModelAnimator;

import java.util.List;

public class Instructions {

    private ArFragment arFragment;
    private Context context;

    private float scale = 2.5f;

    public Instructions(Context context, ArFragment arFragment) {
        this.context = context;
        this.arFragment = arFragment;
    }

    public void loadModels() {
        ModelRenderable.builder()
                .setSource(context, Uri.parse("models/Fries_Popin_Animation.glb"))
                .setIsFilamentGltf(true)
                .setAsyncLoadEnabled(false)
                .build()
                .thenAccept(renderable -> {
                    Node node = new Node();
                    node.setRenderable(renderable);
                    node.setParent(arFragment.getArSceneView().getScene());

                    node.setLocalScale(new Vector3(scale, scale, scale));



                    arFragment.getArSceneView().getScene().addOnUpdateListener(frameTime -> {
                        // Get camera position and forward vector
                        Vector3 cameraPos = arFragment.getArSceneView().getScene().getCamera().getWorldPosition();
                        Vector3 cameraForward = arFragment.getArSceneView().getScene().getCamera().getForward();

                        // Position the node 1 meter in front of the camera
                        Vector3 targetPos = Vector3.add(cameraPos, cameraForward.scaled(1.0f));
                        node.setWorldPosition(targetPos);

                        // Make node face the camera
                        node.setLookDirection(Vector3.subtract(targetPos, cameraPos)); // cameraPos - objectPos
                    });

                    // Optional animation logic
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
                })


                .exceptionally(throwable -> {
                    Toast.makeText(context, "Failed to load models/Fries_Popin_Animation.glb", Toast.LENGTH_LONG).show();
                    return null;
                });
    }

    public void clearAllNodes() {
        // Collect the children in a list first to avoid concurrent modification
        List<Node> children = new java.util.ArrayList<>(arFragment.getArSceneView().getScene().getChildren());

        for (Node node : children) {
            // Skip the camera node
            if (!(node.equals(arFragment.getArSceneView().getScene().getCamera()))) {
                node.setParent(null); // This removes the node from the scene
            }
        }
    }
}
