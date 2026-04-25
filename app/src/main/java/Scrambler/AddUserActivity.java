package Scrambler;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
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

import android.Manifest;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.util.Size;
import android.widget.ImageView;

import androidx.annotation.NonNull;
import androidx.camera.core.CameraSelector;
import androidx.camera.core.ImageAnalysis;
import androidx.camera.core.Preview;
import androidx.camera.lifecycle.ProcessCameraProvider;
import androidx.camera.view.PreviewView;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.google.common.util.concurrent.ListenableFuture;

import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class AddUserActivity extends AppCompatActivity {
    private static final String TAG = "AddUserActivity";
    private static final int REQUEST_CAMERA_PERMISSION = 100;

    private EditText contactNameEditText;
    private Button generateKeyButton;
    private Button scanPublicKeyQrCodeButton;
    private Button scanSigningKeyQrCodeButton;
    private EditText encryptionKeyEditText;
    private EditText signingKeyEditText;
    private Button saveButton;
    private android.widget.TextView handshakePayloadOutput;

    private PreviewView previewView;
    private android.view.View cameraPlaceholder;
    private ImageView ivQRCode;
    private ExecutorService cameraExecutor;
    private ProcessCameraProvider cameraProvider;
    private Button copyPublicKeyButton;
    private enum ScanMode { NONE, PUBLIC_KEY, SIGNING_KEY }
    private ScanMode currentMode = ScanMode.NONE;

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
        ivQRCode = findViewById(R.id.ivQRCode);
        cameraExecutor = Executors.newSingleThreadExecutor();
        scanPublicKeyQrCodeButton = findViewById(R.id.scan_public_key_qr_code_button);
        scanSigningKeyQrCodeButton = findViewById(R.id.scan_signing_key_qr_code_button);
        previewView = findViewById(R.id.previewView);
        cameraPlaceholder = findViewById(R.id.camera_placeholder);
        copyPublicKeyButton = findViewById(R.id.copy_key_button);

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
        scanPublicKeyQrCodeButton.setOnClickListener(v -> {currentMode = ScanMode.PUBLIC_KEY; onScanQrCodeClick();});
        scanSigningKeyQrCodeButton.setOnClickListener(v -> {currentMode = ScanMode.SIGNING_KEY; onScanQrCodeClick();});

        // New copy button listener to copy the generated public key to clipboard
        // Note this technically only copies the text in the handshake payload ouput area 
        // Which can be empty
        copyPublicKeyButton.setOnClickListener(v -> {
            ClipboardManager clipboard = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
            ClipData clip = ClipData.newPlainText("plain_text", handshakePayloadOutput.getText());
            clipboard.setPrimaryClip(clip);
            Toast.makeText(this, "Public key copied to clipboard", Toast.LENGTH_SHORT).show();
        });
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

            // Generate QR code from handshake payload and display it
            Bitmap bitmap = QRCodeHelper.generateQRCode(handshakePayload, 500);
            ivQRCode.setImageBitmap(bitmap);

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
            ScramblerTinkKeyManager keyManager = new ScramblerTinkKeyManager(this);
            keyManager.acceptHandshake(contactName, encryptionKey);

            if (!TextUtils.isEmpty(signingKeyEditText.getText().toString().trim())) {
                keyManager.storeContactSigningPublicKey(contactName, signingKeyEditText.getText().toString().trim());
            }
            
            Toast.makeText(this, "Contact '" + contactName + "' added successfully!", Toast.LENGTH_SHORT).show();
            finish();
        } catch (Exception e) {
            Log.e(TAG, "Failed to save contact", e);
            Toast.makeText(this, "Error saving contact: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }

    // Check for camera permission and ask for it before starting camera
    private void onScanQrCodeClick() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
                != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.CAMERA}, REQUEST_CAMERA_PERMISSION);
        } else {
            startCamera();
        }
    }

    // Handle the result of camera permission request, essentially overriding the
    // above ActivityCompat.requestPermissions callback to start camera if permission is granted
    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions,
            @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQUEST_CAMERA_PERMISSION) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                startCamera();
            } else {
                Toast.makeText(this, "Camera permission is required to scan QR codes.", Toast.LENGTH_SHORT).show();
            }
        }
    }

    private void stopCamera() {
        if (cameraProvider != null) {
            cameraProvider.unbindAll();
        }
        // Show the placeholder again after stopping camera
        // this is so the last frame of the camera doesn't just freeze on the screen after scanning, 
        // which can be confusing to users
        cameraPlaceholder.setVisibility(android.view.View.VISIBLE);
    }

    // When the ML Kit barcode scanner finds a QR code, this function is called with the result. 
    // It sets the paste encryption key field with the scanned encryption key and stops the camera.
    private void handleQrCodeFound(String result) {
        runOnUiThread(() -> {
            if (currentMode == ScanMode.PUBLIC_KEY) {
                encryptionKeyEditText.setText(result);
            } else if (currentMode == ScanMode.SIGNING_KEY) {
                signingKeyEditText.setText(result);
            }
            stopCamera();
            Toast.makeText(this, "QR code scanned.", Toast.LENGTH_SHORT).show();
        });
    }

    private void startCamera() {
        cameraPlaceholder.setVisibility(android.view.View.GONE);

        ListenableFuture<ProcessCameraProvider> cameraProviderFuture = 
                ProcessCameraProvider.getInstance(this);

        cameraProviderFuture.addListener(() -> {
            try {
                cameraProvider = cameraProviderFuture.get();

                // Preview setup
                Preview preview = new Preview.Builder().build();
                preview.setSurfaceProvider(previewView.getSurfaceProvider());

                // Analysis setup (Linking ML Kit)
                ImageAnalysis imageAnalysis = new ImageAnalysis.Builder()
                        .setTargetResolution(new Size(1280, 720))
                        .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                        .build();

                // Use QRAnalyzer which uses ML Kit to analyze camera frames for QR codes
                imageAnalysis.setAnalyzer(cameraExecutor, new QRAnalyzer(result -> {
                    handleQrCodeFound(result);
                }));

                // unbind any existing use cases before rebinding
                cameraProvider.unbindAll();
                cameraProvider.bindToLifecycle(this, CameraSelector.DEFAULT_BACK_CAMERA, 
                        preview, imageAnalysis);

            } catch (ExecutionException | InterruptedException e) {
                //something goes wrong during camera initialization
                // Show the placeholder again if camera fails to start, so user isn't left with a blank screen
                cameraPlaceholder.setVisibility(android.view.View.VISIBLE);
                e.printStackTrace();
            }
        }, ContextCompat.getMainExecutor(this));
    }
}
