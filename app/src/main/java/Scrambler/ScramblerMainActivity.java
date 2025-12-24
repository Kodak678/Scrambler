package Scrambler;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import rkr.simplekeyboard.inputmethod.R;

public class ScramblerMainActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_scrambler);
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });
    }

    public void launchKeyboardSettings(View v) {
        // This method is an event handler for clicking on the keyboard settings button
        // It reroutes to the Simple Keyboard's original settings page
        Intent i = new Intent(this, rkr.simplekeyboard.inputmethod.latin.settings.SettingsActivity.class);
        startActivity(i);
    }

}