package com.flam.burgergame;

import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.ImageView;

import androidx.appcompat.app.AppCompatActivity;

import com.flam.burgergame.screens.GameScreen;

public class MainActivity extends AppCompatActivity {

    private ImageView playButton;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);  // Make sure this file exists!

        playButton = findViewById(R.id.playButton); // This must match the ID in XML!
        playButton.setVisibility(View.VISIBLE);

        if (playButton != null) {
            playButton.setOnClickListener(v -> {
                Intent intent = new Intent(MainActivity.this, GameScreen.class);
                startActivity(intent);
            });
        } else {
            Log.e("MainActivity", "playButton not found in layout!");
        }
    }
}

