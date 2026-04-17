package Scrambler;

import android.content.SharedPreferences;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.Log;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import rkr.simplekeyboard.inputmethod.R;
import rkr.simplekeyboard.inputmethod.compat.PreferenceManagerCompat;
import rkr.simplekeyboard.inputmethod.latin.settings.Settings;

public class AddUserActivity extends AppCompatActivity {
    private static final String TAG = "AddUserActivity";

    private EditText contactNameEditText;
    private Button generateKeyButton;
    private EditText encryptionKeyEditText;
    private EditText signingKeyEditText;
    private Button saveButton;
    private android.widget.TextView handshakePayloadOutput;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_add_user);
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });

        // Enable the up button in the action bar
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        }

        contactNameEditText = findViewById(R.id.contact_name_input);
        generateKeyButton = findViewById(R.id.generate_key_button);
        encryptionKeyEditText = findViewById(R.id.encryption_key_input);
        signingKeyEditText = findViewById(R.id.signing_key_input);
        saveButton = findViewById(R.id.save_button);
        handshakePayloadOutput = findViewById(R.id.handshake_payload_output);

        // Initially disable action buttons until name is provided
        updateButtonEnablement();

        contactNameEditText.setOnFocusChangeListener((v, hasFocus) -> updateButtonEnablement());
        contactNameEditText.addTextChangedListener(new android.text.TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                updateButtonEnablement();
            }

            @Override
            public void afterTextChanged(android.text.Editable s) {}
        });

        generateKeyButton.setOnClickListener(v -> onGenerateKeyClick());
        saveButton.setOnClickListener(v -> onSaveClick());
    }

    @Override
    public boolean onOptionsItemSelected(android.view.MenuItem item) {
        if (item.getItemId() == android.R.id.home) {
            // Handle up button click: finish this activity
            finish();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    private void updateButtonEnablement() {
        String name = contactNameEditText.getText().toString().trim();
        boolean hasName = !TextUtils.isEmpty(name);
        boolean isValid = hasName && name.matches("[a-zA-Z0-9]+$");

        if (!hasName) {
            contactNameEditText.setError(null);
        } else if (!isValid) {
            contactNameEditText.setError("Contact name can only contain letters, numbers, period, underscore, or hyphen");
        } else {
            contactNameEditText.setError(null);
        }

        generateKeyButton.setEnabled(isValid);
        saveButton.setEnabled(isValid);

        float enabledAlpha = isValid ? 1.0f : 0.5f;
        generateKeyButton.setAlpha(enabledAlpha);
        saveButton.setAlpha(enabledAlpha);
    }

    private void onGenerateKeyClick() {
        final String contactName = contactNameEditText.getText().toString().trim();
        if (TextUtils.isEmpty(contactName)) {
            Toast.makeText(this, "Contact name is required", Toast.LENGTH_SHORT).show();
            return;
        }
        if (!contactName.matches("[a-zA-Z0-9]+$")) {
            Toast.makeText(this, "Contact name can only contain letters and numbers", Toast.LENGTH_SHORT).show();
            return;
        }
        // Check if username is already taken
        SharedPreferences contactPrefs = getSharedPreferences("scrambler_encryption_prefs", MODE_PRIVATE);
        if (contactPrefs.contains(contactName + "_enc_pub")
                || contactPrefs.contains(contactName + "_sign_pub")
                || contactPrefs.contains(contactName + "_")) {
            Toast.makeText(this, "Contact name already in use. Please choose another name.", Toast.LENGTH_SHORT).show();
            return;
        }
        try {
            final String handshakePayload = new ScramblerTinkKeyManager(this)
                    .offerHandshake(contactName);
            handshakePayloadOutput.setText(handshakePayload);
            signingKeyEditText.setText("");
            // Lock the contact name field after successful key generation
            contactNameEditText.setEnabled(false);
            Toast.makeText(this, "Encryption public key generated.", Toast.LENGTH_SHORT).show();
        } catch (Exception e) {
            Log.e(TAG, "Failed to generate key", e);
            Toast.makeText(this, "Error generating key: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }


    private void onSaveClick() {
        final String contactName = contactNameEditText.getText().toString().trim();
        final String encryptionKey = encryptionKeyEditText.getText().toString().trim();

        if (TextUtils.isEmpty(contactName)) {
            Toast.makeText(this, "Contact name is required", Toast.LENGTH_SHORT).show();
            return;
        }
        if (!contactName.matches("[a-zA-Z0-9]+$")) {
            Toast.makeText(this, "Contact name can only contain letters and numbers", Toast.LENGTH_SHORT).show();
            return;
        }
        if (TextUtils.isEmpty(encryptionKey)) {
            Toast.makeText(this, "Encryption key is required. Generate or paste a key first.", Toast.LENGTH_SHORT).show();
            return;
        }
        try {
            final SharedPreferences contactPrefs = getSharedPreferences(
                "scrambler_encryption_prefs", MODE_PRIVATE);
            if (contactPrefs.contains(contactName + "_enc_pub")
                || contactPrefs.contains(contactName + "_sign_pub")
                || contactPrefs.contains(contactName + "_")) {
                Toast.makeText(this,
                    "Contact name already in use. Please choose another name.",
                    Toast.LENGTH_SHORT).show();
                return;
            }
            // Always acceptHandshake when saving, since the output area is now separate
            new ScramblerTinkKeyManager(this)
                .acceptHandshake(contactName, encryptionKey);
            Toast.makeText(this, "Contact '" + contactName + "' added successfully!", Toast.LENGTH_SHORT).show();
            finish();
        } catch (Exception e) {
            Log.e(TAG, "Failed to save contact", e);
            Toast.makeText(this, "Error saving contact: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }

}
