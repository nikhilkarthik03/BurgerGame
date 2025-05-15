package com.flam.burgergame.utils;

import android.content.Context;
import android.net.Uri;
import android.util.Log;
import android.widget.Toast;

import com.google.ar.sceneform.rendering.ModelRenderable;
import com.google.ar.sceneform.rendering.Renderable;

import java.lang.ref.WeakReference;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Singleton class to manage loading and caching 3D models
 */
public class ModelLoaderManager {
    private static final String TAG = "ModelLoaderManager";

    // Model file paths
    public static final String BURGER_MODEL = "models/prop_burger.glb";
    public static final String COKE_MODEL = "models/prop_coke.glb";
    public static final String WINGS_MODEL = "models/prop_chickenWings.glb";
    public static final String FRIES_MODEL = "models/Fries_Popin_Animation_V2.glb";
    public static final String TRAY_MODEL = "models/prop_tray_with_burger.glb";

    // Singleton instance
    private static ModelLoaderManager instance;

    // Map to store loaded models
    private final Map<String, Renderable> loadedModels = new HashMap<>();

    // Event emitter to notify when models are loaded
    private final EventEmitter eventEmitter;

    // Loading state
    private boolean isLoading = false;
    private boolean allModelsLoaded = false;

    // Private constructor
    private ModelLoaderManager() {
        eventEmitter = EventEmitter.getInstance();
    }

    // Get singleton instance
    public static synchronized ModelLoaderManager getInstance() {
        if (instance == null) {
            instance = new ModelLoaderManager();
        }
        return instance;
    }

    /**
     * Load all required models for the game
     */
    public void loadAllModels(Context context) {
        // Skip if already loaded or loading
        if (allModelsLoaded || isLoading) {
            if (allModelsLoaded) {
                // If already loaded, emit the loaded event again for any new listeners
                eventEmitter.emit(GameEvents.ALL_MODELS_LOADED, loadedModels.size());
            }
            return;
        }

        isLoading = true;

        // Emit loading started event
        eventEmitter.emit(GameEvents.MODEL_LOADING_STARTED, "all_models");

        WeakReference<Context> weakContext = new WeakReference<>(context);

        // List of models to load
        String[] modelPaths = {
                BURGER_MODEL,
                COKE_MODEL,
                WINGS_MODEL,
                FRIES_MODEL,
                TRAY_MODEL
        };

        // Use AtomicInteger to track loading progress
        AtomicInteger loadedCount = new AtomicInteger(0);

        // Create futures for all model loading tasks
        CompletableFuture<?>[] futures = new CompletableFuture<?>[modelPaths.length];

        for (int i = 0; i < modelPaths.length; i++) {
            final String modelPath = modelPaths[i];

            // Skip if already loaded
            if (loadedModels.containsKey(modelPath)) {
                loadedCount.incrementAndGet();
                futures[i] = CompletableFuture.completedFuture(null);
                continue;
            }

            // Load model
            CompletableFuture<ModelRenderable> future = ModelRenderable.builder()
                    .setSource(context, Uri.parse(modelPath))
                    .setIsFilamentGltf(true)
                    .setAsyncLoadEnabled(true)  // Use async loading for better performance
                    .build()
                    .thenApply(renderable -> {
                        Context ctx = weakContext.get();
                        if (ctx != null) {
                            loadedModels.put(modelPath, renderable);
                            Log.d(TAG, "Model loaded: " + modelPath);

                            // Emit progress event
                            int progress = loadedCount.incrementAndGet();
//                            eventEmitter.emit(GameEvents.ALL_MODELS_LOADED, progress);
                        }
                        return renderable;
                    });

            futures[i] = future.exceptionally(throwable -> {
                Context ctx = weakContext.get();
                if (ctx != null) {
                    Log.e(TAG, "Error loading model: " + modelPath, throwable);
                    Toast.makeText(ctx, "Failed to load model: " + modelPath, Toast.LENGTH_LONG).show();

                    // Increment counter even on failure to keep track
                    loadedCount.incrementAndGet();
                }
                return null;
            });
        }

        // When all futures are complete
        CompletableFuture.allOf(futures).thenAccept(unused -> {
            allModelsLoaded = loadedCount.get() == modelPaths.length;
            isLoading = false;

            if (allModelsLoaded) {
                Log.d(TAG, "All models loaded successfully");
                eventEmitter.emit(GameEvents.ALL_MODELS_LOADED, loadedModels.size());
            } else {
                Log.e(TAG, "Some models failed to load");
//                eventEmitter.emit(GameEvents.MODEL_LOADING_FAILED,
//                        "Only " + loadedCount.get() + "/" + modelPaths.length + " models loaded");
            }
        });
    }

    /**
     * Get a loaded model by its path
     */
    public Renderable getModel(String modelPath) {
        return loadedModels.get(modelPath);
    }

    /**
     * Check if all models are loaded
     */
    public boolean areAllModelsLoaded() {
        return allModelsLoaded;
    }

    /**
     * Check if a specific model is loaded
     */
    public boolean isModelLoaded(String modelPath) {
        return loadedModels.containsKey(modelPath);
    }

    /**
     * Clear all loaded models
     */
    public void clearModels() {
        loadedModels.clear();
        allModelsLoaded = false;
    }
}