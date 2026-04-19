package Scrambler;

import android.content.Intent;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.Log;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;
import android.widget.TextView;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import java.io.IOException;
import java.security.GeneralSecurityException;

import rkr.simplekeyboard.inputmethod.R;

public class UpdateContactActivity extends AppCompatActivity {
    private static final String TAG = "UpdateContactActivity";

    private EditText contactNameEditText;
    private Button generateKeyButton;
    private EditText encryptionKeyEditText;
    private EditText signingKeyEditText;
    private Button saveButton;
    private TextView handshakePayloadOutput;
    private Button deleteButton;

    private String contactName;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_update_contact);
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });

        contactNameEditText = findViewById(R.id.contact_name_input);
        generateKeyButton = findViewById(R.id.generate_key_button);
        encryptionKeyEditText = findViewById(R.id.encryption_key_input);
        signingKeyEditText = findViewById(R.id.signing_key_input);
        saveButton = findViewById(R.id.save_button);
        handshakePayloadOutput = findViewById(R.id.handshake_payload_output);
        deleteButton = findViewById(R.id.delete_button);

        // Get contact name from intent
        contactName = getIntent().getStringExtra("contact_name");
        if (TextUtils.isEmpty(contactName)) {
            Toast.makeText(this, "No contact name provided", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        contactNameEditText.setText(contactName);
        contactNameEditText.setEnabled(false);

        // Load keys
        try {
            ScramblerTinkKeyManager keyManager = new ScramblerTinkKeyManager(this);
            String encKey = keyManager.getStoredContactEncryptionPublicKey(contactName);
            String signKey = keyManager.getStoredContactSigningPublicKey(contactName);
            if (encKey != null) encryptionKeyEditText.setText(encKey);
            if (signKey != null) signingKeyEditText.setText(signKey);
        } catch (Exception e) {
            Log.e(TAG, "Failed to load contact keys", e);
        }

        saveButton.setOnClickListener(v -> onSaveClick());
        deleteButton.setOnClickListener(v -> onDeleteClick());
        generateKeyButton.setOnClickListener(v -> onGenerateKeyClick());
    }

    private void onSaveClick() {
        final String encryptionKey = encryptionKeyEditText.getText().toString().trim();
        final String signingKey = signingKeyEditText.getText().toString().trim();
        if (TextUtils.isEmpty(encryptionKey)) {
            Toast.makeText(this, "Encryption key is required.", Toast.LENGTH_SHORT).show();
            return;
        }
        try {
            ScramblerTinkKeyManager keyManager = new ScramblerTinkKeyManager(this);
            keyManager.acceptHandshake(contactName, encryptionKey);
            if (!TextUtils.isEmpty(signingKey)) {
                keyManager.storeContactSigningPublicKey(contactName, signingKey);
            }
            Toast.makeText(this, "Contact updated!", Toast.LENGTH_SHORT).show();
            finish();
        } catch (Exception e) {
            Log.e(TAG, "Failed to update contact", e);
            Toast.makeText(this, "Error updating contact: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }

    private void onDeleteClick() {
        try {
            ScramblerTinkKeyManager keyManager = new ScramblerTinkKeyManager(this);
            keyManager.deleteContact(contactName);
            Toast.makeText(this, "Contact deleted!", Toast.LENGTH_SHORT).show();
            finish();
        } catch (Exception e) {
            Log.e(TAG, "Failed to delete contact", e);
            Toast.makeText(this, "Error deleting contact: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }

    private void onGenerateKeyClick() {
        try {
            final String handshakePayload = new ScramblerTinkKeyManager(this)
                    .offerHandshake(contactName);
            handshakePayloadOutput.setText(handshakePayload);
            Toast.makeText(this, "Encryption public key generated.", Toast.LENGTH_SHORT).show();
        } catch (Exception e) {
            Log.e(TAG, "Failed to generate key", e);
            Toast.makeText(this, "Error generating key: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }
}
