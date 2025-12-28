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
import rkr.simplekeyboard.inputmethod.latin.inputlogic.InputLogic.CryptoType;

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
    
    public static String processText(String inputText, CryptoType cryptoType) {
        
        if (cryptoType == CryptoType.ENCRYPT) 
        {
            return "[Encrypted]";
        } 
        else if (cryptoType == CryptoType.DECRYPT) 
        {
            return "[Decrypted]";
        } 
        else if (cryptoType == CryptoType.SIGN) 
        {
            return "[Signed]";
        } 
        else if (cryptoType == CryptoType.VERIFY) 
        {
            return "[Verified]";
        } 
        else 
        {   
            // Should never reach here. This is just so the method always returns the expected type
            Log.e("ScramblerMainActivity", "Unknown CryptoType: " + cryptoType);
            return inputText; 
        }
    }
}