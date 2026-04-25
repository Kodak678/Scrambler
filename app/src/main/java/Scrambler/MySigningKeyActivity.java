package Scrambler;

import android.app.AlertDialog;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.graphics.Bitmap;
import android.os.Bundle;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import java.io.IOException;
import java.security.GeneralSecurityException;

import rkr.simplekeyboard.inputmethod.R;

public class MySigningKeyActivity extends AppCompatActivity {
    private TextView signingKeyTextView;
    private Button generateKeyButton;
    private ImageView ivQRCode;
    private Button copyPublicKeyButton;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_my_signing_key);
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(android.R.id.content), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });

        signingKeyTextView = findViewById(R.id.signing_key_textview);
        generateKeyButton = findViewById(R.id.generate_signing_key_button);
        ivQRCode = findViewById(R.id.ivQRCode);
        copyPublicKeyButton = findViewById(R.id.copy_key_button);
        loadSigningKey();
        generateKeyButton.setOnClickListener(v -> showRegenerateKeyDialog());

        // New copy button listener to copy the fetched public signing key to clipboard
        // Note this technically only copies the text in the signing key area
        // Which can be empty
        copyPublicKeyButton.setOnClickListener(v -> {
            ClipboardManager clipboard = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
            ClipData clip = ClipData.newPlainText("plain_text", signingKeyTextView.getText());
            clipboard.setPrimaryClip(clip);
            Toast.makeText(this, "Public signing key copied to clipboard", Toast.LENGTH_SHORT).show();
        });
    }

    private void loadSigningKey() {
        try {
            ScramblerTinkKeyManager keyManager = new ScramblerTinkKeyManager(getApplicationContext());
            String publicKey = keyManager.getIdentitySigningPublicKey();
            signingKeyTextView.setText(publicKey);

            // Generate QR code from signing key and display it
            Bitmap bitmap = QRCodeHelper.generateQRCode(publicKey, 500);
            ivQRCode.setImageBitmap(bitmap);

        } catch (GeneralSecurityException | IOException e) {
            signingKeyTextView.setText("Error loading signing key: " + e.getMessage());
        }
    }

    private void showRegenerateKeyDialog() {
        new AlertDialog.Builder(this)
                .setTitle("Regenerate Signing Key?")
                .setMessage("If you generate a new signing key, nobody will be able to verify your old messages anymore. Are you sure you want to continue?")
                .setPositiveButton("Yes, regenerate", (dialog, which) -> {
                    regenerateSigningKey();
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void regenerateSigningKey() {
        try {
            ScramblerTinkKeyManager keyManager = new ScramblerTinkKeyManager(getApplicationContext());
            keyManager.forceRegenerateSigningKey();
            loadSigningKey();
            Toast.makeText(this, "New signing key generated!", Toast.LENGTH_SHORT).show();
        } catch (GeneralSecurityException | IOException e) {
            Toast.makeText(this, "Error generating new key: " + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }
}
