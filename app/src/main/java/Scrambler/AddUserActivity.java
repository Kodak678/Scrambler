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
    private Button pasteKeyButton;
    private EditText encryptionKeyEditText;
    private EditText signingKeyEditText;
    private Button saveButton;

    // This variable tracks whether the user generated a key when adding a new user, 
    // which affects whether we call acceptHandshake on save. If they generated a key, 
    // we assume they already have the public key and just want to save the contact without accepting a handshake. 
    // If they didn't generate a key, we assume they pasted an encryption key and need to call acceptHandshake 
    // to validate and save it.
    private boolean generatedKey = false;

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

        contactNameEditText = findViewById(R.id.contact_name_input);
        generateKeyButton = findViewById(R.id.generate_key_button);
        pasteKeyButton = findViewById(R.id.paste_key_button);
        encryptionKeyEditText = findViewById(R.id.encryption_key_input);
        signingKeyEditText = findViewById(R.id.signing_key_input);
        saveButton = findViewById(R.id.save_button);

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
        pasteKeyButton.setOnClickListener(v -> onPasteKeyClick());
        saveButton.setOnClickListener(v -> onSaveClick());
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
        pasteKeyButton.setEnabled(isValid);
        saveButton.setEnabled(isValid);

        float enabledAlpha = isValid ? 1.0f : 0.5f;
        pasteKeyButton.setAlpha(enabledAlpha);
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
            encryptionKeyEditText.setText(handshakePayload);
            signingKeyEditText.setText("");
            // Lock the contact name field after successful key generation
            contactNameEditText.setEnabled(false);
            generatedKey = true;
            Toast.makeText(this, "Encryption public key generated.", Toast.LENGTH_SHORT).show();
        } catch (Exception e) {
            Log.e(TAG, "Failed to generate key", e);
            Toast.makeText(this, "Error generating key: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }

    private void onPasteKeyClick() {
        Toast.makeText(this, "Paste your encryption public key data URI above", Toast.LENGTH_SHORT).show();
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
            if (generatedKey != true) {
                new ScramblerTinkKeyManager(this)
                    .acceptHandshake(contactName, encryptionKey);
                Toast.makeText(this, "Contact '" + contactName + "' added successfully!", Toast.LENGTH_SHORT).show();
                finish();
            }
            
            Toast.makeText(this, "Contact '" + contactName + "' added successfully, don't forget to get their public encryption key!", Toast.LENGTH_SHORT).show();
            // Finish activity and return to main
            finish();
        } catch (Exception e) {
            Log.e(TAG, "Failed to save contact", e);
            Toast.makeText(this, "Error saving contact: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }

}
