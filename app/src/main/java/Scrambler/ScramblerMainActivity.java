package Scrambler;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import android.util.Log;

import rkr.simplekeyboard.inputmethod.R;

public class ScramblerMainActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_scrambler_main);
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });

        Button openSettingsButton = findViewById(R.id.open_settings_button);
        openSettingsButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent intent = new Intent(ScramblerMainActivity.this, rkr.simplekeyboard.inputmethod.latin.settings.SettingsActivity.class);
                startActivity(intent);
            }
        });

        
    }
    // Basic handlers for scrambler keys (temporary - print to console)
    public static void onEncryptPressed() {
        Log.i("ScramblerMainActivity", "encrypt");
    }

    public static void onDecryptPressed() {
        Log.i("ScramblerMainActivity", "decrypt");
    }

    public static void onSignPressed() {
        Log.i("ScramblerMainActivity", "sign");
    }

    public static void onVerifyPressed() {
        Log.i("ScramblerMainActivity", "verify");
    }
}