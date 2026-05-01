package Scrambler;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.ImageButton;
import android.content.Context;
import android.content.SharedPreferences;
import android.view.LayoutInflater;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import android.util.Log;
import android.widget.Toast;

import java.io.IOException;
import java.security.GeneralSecurityException;
import java.util.Iterator;
import java.util.Set;
import java.util.Arrays;

import rkr.simplekeyboard.inputmethod.R;
import rkr.simplekeyboard.inputmethod.latin.inputlogic.InputLogic.CryptoType;

import com.google.crypto.tink.config.TinkConfig;
import com.google.crypto.tink.signature.SignatureConfig;

import org.json.JSONObject;
import org.json.JSONArray;
import java.io.InputStream;
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel;
import java.io.FileInputStream;
import java.io.File;
import java.nio.ByteBuffer;
import java.util.HashMap;
import java.util.Map;
import org.json.JSONException;
import android.content.res.AssetFileDescriptor;
import org.tensorflow.lite.Interpreter;

public class ScramblerMainActivity extends AppCompatActivity {

        // --- Sensitivity Classifier fields ---
    private static Interpreter sTFLiteInterpreter = null;
    private static Map<Integer, String> sId2Label = null;
    private static final int MAX_LEN = 128; // Must match training length for the DistilBERT model; input text will be truncated/padded to this length.

    @Override
    protected void onCreate(Bundle savedInstanceState) {

        // Register Tink ALL key managers 
        // This is needed for any Tink operations.
        // You will get a Tink activity not registered error if you open the ScramblerMainActivity 
        // before opening the keyboard itself, because the keyboard also uses Tink for encrypting/decrypting messages 
        // And thus registers the key managers.
        try {
            TinkConfig.register();
        } catch (Exception e) {
            Log.e("ScramblerMainActivity", "Tink SignatureConfig registration failed", e);
        }

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

        Button addUserButton = findViewById(R.id.add_user_button);
        addUserButton.setOnClickListener(new View.OnClickListener() {
        @Override
        public void onClick(View v) {
            Intent intent = new Intent(ScramblerMainActivity.this, Scrambler.AddUserActivity.class);
            startActivity(intent);
        }
        });

            Button mySigningKeyButton = findViewById(R.id.my_signing_key_button);
            mySigningKeyButton.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    Intent intent = new Intent(ScramblerMainActivity.this, Scrambler.MySigningKeyActivity.class);
                    startActivity(intent);
                }
            });

        // Defer contact rendering so the activity can show its first frame sooner.
        View content = findViewById(R.id.main);
        if (content != null) {
            content.post(this::loadContacts);
        } else {
            loadContacts();
        }
    }

    // Since we want the contact list to always be up to date, we also call loadContacts in onResume.
    // The list of contacts will not be updated in real time when we add/edit/delete a contact, 
    // so we need to refresh the list when we come back to the main activity.
    @Override
    protected void onResume() {
        super.onResume();
        View content = findViewById(R.id.main);
        if (content != null) {
            content.post(this::loadContacts);
        } else {
            loadContacts();
        }
    }

    // This method is for displaying all the contacts with the edit and delete buttons 
    // as a nice list on the main screen. 
    // It is called in onCreate and onResume to ensure the list is always up to date.
    private void loadContacts() {
        LinearLayout contactsContainer = findViewById(R.id.contacts_container);
        if (contactsContainer == null) return;
        contactsContainer.removeAllViews();
        LayoutInflater inflater = LayoutInflater.from(this);
        Set<String> Contacts = null;
        try {
            Contacts = new ScramblerTinkKeyManager(getApplicationContext()).getAllContacts();
        } catch (GeneralSecurityException | IOException e) {
            Log.e("ScramblerMainActivity", "Error loading contacts: " + e.getMessage(), e);
            Toast.makeText(this, "Error loading contacts: " + e.getMessage(), Toast.LENGTH_SHORT).show();
            return;
        }
        for (String contact : Contacts) {
            View contactRow = inflater.inflate(R.layout.contact_row, contactsContainer, false);
            TextView nameText = contactRow.findViewById(R.id.contact_name_text);
            nameText.setText(contact);

            ImageButton editButton = contactRow.findViewById(R.id.edit_contact_button);
            ImageButton deleteButton = contactRow.findViewById(R.id.delete_contact_button);

            editButton.setOnClickListener(v -> {
                Intent intent = new Intent(ScramblerMainActivity.this, Scrambler.UpdateContactActivity.class);
                intent.putExtra("contact_name", contact);
                startActivity(intent);
            });

            deleteButton.setOnClickListener(v -> {
                try {
                    ScramblerTinkKeyManager keyManager = new ScramblerTinkKeyManager(getApplicationContext());
                    keyManager.deleteContact(contact);
                    Toast.makeText(ScramblerMainActivity.this, "Contact: " + contact + " deleted!", Toast.LENGTH_SHORT).show();
                    // Remove from UI
                    contactsContainer.removeView(contactRow);
                } catch (Exception e) {
                    Log.e("ScramblerMainActivity", "Error deleting contact: " + e.getMessage(), e);
                    Toast.makeText(ScramblerMainActivity.this, "Error deleting contact: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                }
            });

            contactsContainer.addView(contactRow);
        }
    }

    public static String processText(Context context, String inputText, CryptoType cryptoType, String selectedContact) {
        try {
            ScramblerTinkKeyManager keyManager = new ScramblerTinkKeyManager(context);
            if (cryptoType == CryptoType.ENCRYPT) {
                String ciphertext = keyManager.encryptForContact(selectedContact, inputText);
                return "[Encrypted]" + ciphertext + "[/Encrypted]";
            } else if (cryptoType == CryptoType.DECRYPT) {
                // Parse [Encrypted]...[/Encrypted] tags
                String tagStart = "[Encrypted]";
                String tagEnd = "[/Encrypted]";
                int start = inputText.indexOf(tagStart);
                int end = inputText.indexOf(tagEnd);
                if (start != -1 && end != -1 && end > start + tagStart.length()) {
                    String ciphertext = inputText.substring(start + tagStart.length(), end);
                    String plaintext = keyManager.decryptFromContact(selectedContact, ciphertext);
                    return "[Decrypted]" + plaintext + "[/Decrypted]";
                } else {
                    return "[Error: Invalid encrypted input]";
                }
            } else if (cryptoType == CryptoType.SIGN) {
                String signature = keyManager.signMessage(inputText);
                return "[Signed]" + inputText + "<Signiture>" + signature + "</Signiture>" + "[/Signed]";
            } else if (cryptoType == CryptoType.VERIFY) {
                // Parse [Signed]...[/Signed] tags
                String tagStart = "[Signed]";
                String tagEnd = "[/Signed]";
                int start = inputText.indexOf(tagStart);
                int end = inputText.indexOf(tagEnd);
                if (start != -1 && end != -1 && end > start + tagStart.length()) {
                    String signedContent = inputText.substring(start + tagStart.length(), end);
                    // Find <Signiture>...</Signiture> tags
                    String sigTagStart = "<Signiture>";
                    String sigTagEnd = "</Signiture>";
                    int sigStart = signedContent.indexOf(sigTagStart);
                    int sigEnd = signedContent.indexOf(sigTagEnd);
                    if (sigStart != -1 && sigEnd != -1 && sigEnd > sigStart + sigTagStart.length()) {
                        String message = signedContent.substring(0, sigStart);
                        String signature = signedContent.substring(sigStart + sigTagStart.length(), sigEnd);
                        boolean verified = keyManager.verifyContactSignature(selectedContact, message, signature);
                        if (verified) {
                            return "Contact: " + selectedContact + " has been verified as the sender of this message.[Verified]" + message + "[/Verified]";
                        } else {
                            return "[Error: Signature verification failed]";
                        }
                    } else {
                        return "[Error: Invalid signed input - signature tags not found]";
                    }
                } else {
                    return "[Error: This is not a signed message]";
                }
            } else if (cryptoType == CryptoType.OFFER_HANDSHAKE) {
                String handshakePayload = keyManager.offerHandshake(selectedContact);
                return "[HandshakeOffer]" + handshakePayload + "[/HandshakeOffer]";
            } else if (cryptoType == CryptoType.ACCEPT_HANDSHAKE) {
                String tagStart = "[HandshakeOffer]";
                String tagEnd = "[/HandshakeOffer]";
                int start = inputText.indexOf(tagStart);
                int end = inputText.indexOf(tagEnd);
                if (start != -1 && end != -1 && end > start + tagStart.length()) {
                    String handshakePayload = inputText.substring(start + tagStart.length(), end);
                    boolean accepted = keyManager.acceptHandshake(selectedContact, handshakePayload);
                    return accepted ? "True" : "False";
                } else {
                    return "False";
                }
            } 
            else {
                Log.e("ScramblerMainActivity", "Unknown CryptoType: " + cryptoType);
                return inputText;
            }
        } catch (Exception e) {
            Log.e("ScramblerMainActivity", "Crypto error: " + e.getMessage(), e);
            return "[Crypto error: " + e.getMessage() + "]";
        }
    }
    


    /**
     * Loads the TFLite model from assets if not already loaded.
     */
    private static void ensureClassifierLoaded(Context context) {
        if (sTFLiteInterpreter != null && sId2Label != null) return;
        try {
            // Load TFLite model
            // This code was adapted from an article on Medium:
            // Kamesh, K. (2020) Running ML Models in Android using Tensorflow Lite. Available at: https://medium.com/analytics-vidhya/running-ml-models-in-android-using-tensorflow-lite-e549209287f0 (accessed on 01 May 2026).
            if (sTFLiteInterpreter == null) {
                AssetFileDescriptor fileDescriptor = context.getAssets().openFd("distilbert_finetuned.tflite");
                FileInputStream inputStream = new FileInputStream(fileDescriptor.getFileDescriptor());
                FileChannel fileChannel = inputStream.getChannel();
                long startOffset = fileDescriptor.getStartOffset();
                long declaredLength = fileDescriptor.getDeclaredLength();
                MappedByteBuffer modelBuffer = fileChannel.map(FileChannel.MapMode.READ_ONLY, startOffset, declaredLength);
                sTFLiteInterpreter = new Interpreter(modelBuffer);
            }
            // Load id2label
            // This is a simple JSON file that maps the model's numeric class predictions (i.e. 0 or 1) to human readable labels (i.e. "encrypt" or "not_encrypt").
            if (sId2Label == null) {
                InputStream id2labelStream = context.getAssets().open("id2label.json");
                int size = id2labelStream.available();
                byte[] buffer = new byte[size];
                id2labelStream.read(buffer);
                id2labelStream.close();
                String json = new String(buffer, "UTF-8");
                JSONObject obj = new JSONObject(json);
                sId2Label = new HashMap<>();
                for (Iterator<String> it = obj.keys(); it.hasNext(); ) {
                    String key = it.next();
                    sId2Label.put(Integer.parseInt(key), obj.getString(key));
                }
            }
            // Check that the model's input and output tensor metadata matches our expectations, and log it for debugging.
            // Check we can inspect the input and output tensor metadata, which is needed to ensure we feed the inputs in the correct order and with the correct shapes/datatypes.
            if (sTFLiteInterpreter != null) {
                try {
                    Log.d("ScramblerMainActivity", "TFLite input tensor 0: name=" + sTFLiteInterpreter.getInputTensor(0).name()
                            + ", dtype=" + sTFLiteInterpreter.getInputTensor(0).dataType()
                            + ", shape=" + Arrays.toString(sTFLiteInterpreter.getInputTensor(0).shape()));
                    if (sTFLiteInterpreter.getInputTensorCount() > 1) {
                        Log.d("ScramblerMainActivity", "TFLite input tensor 1: name=" + sTFLiteInterpreter.getInputTensor(1).name()
                                + ", dtype=" + sTFLiteInterpreter.getInputTensor(1).dataType()
                                + ", shape=" + Arrays.toString(sTFLiteInterpreter.getInputTensor(1).shape()));
                    }
                    Log.d("ScramblerMainActivity", "TFLite output tensor 0: name=" + sTFLiteInterpreter.getOutputTensor(0).name()
                            + ", dtype=" + sTFLiteInterpreter.getOutputTensor(0).dataType()
                            + ", shape=" + Arrays.toString(sTFLiteInterpreter.getOutputTensor(0).shape()));
                } catch (Exception tensorLogError) {
                    Log.e("ScramblerMainActivity", "Unable to inspect TFLite tensor metadata", tensorLogError);
                }
            }
            Log.d("ScramblerMainActivity", "Loaded labels: " + sId2Label.keySet().toString());
        } catch (Exception e) {
            Log.e("ScramblerMainActivity", "Error loading classifier assets", e);
        }
    }

    
    // Classifies the given text and returns true if the model recommends encryption.
    // Returns false if not necessary or on error.
    // The documentation for the Interpreter API is here: https://ai.google.dev/edge/api/tflite/java/org/tensorflow/lite/Interpreter
    // The code in this method is heavily based on the documentation and examples from TensorFlow, 
    // but I have adapted to my specific model inputs/outputs and with additional logging for debugging.
    public static boolean shouldEncryptText(Context context, String text) {
        ensureClassifierLoaded(context);
        if (sTFLiteInterpreter == null || sId2Label == null) {
            Log.e("ScramblerMainActivity", "Classifier not loaded");
            return false;
        }
        try {
            Log.d("ScramblerMainActivity", "Classifying text: " + text);
            // Convert raw text into DistilBERT token IDs. Each ID is an index into the model vocabulary
            // representing a subword/unit of meaning; we store them in a fixed-length [1, MAX_LEN] batch
            // tensor so it matches the model's expected input shape for single-sample inference.
            long[] inputIds = ScramblerTokenizer.encode(text, context, MAX_LEN);
            long[][] input = new long[1][MAX_LEN];
            input[0] = inputIds;

            // DistilBERT needs an attention mask to distinguish real tokens from padding tokens.
            // We build it by writing 1 where input_ids has a non-pad token and 0 where it is padding.
            long[][] attention = new long[1][MAX_LEN];
            for (int i = 0; i < MAX_LEN; ++i) {
                attention[0][i] = (inputIds[i] != 0L) ? 1L : 0L;
            }

            // Logits are the model's raw class scores (pre-softmax). Higher logit means stronger evidence
            // for that class; later we pick the class index with the highest logit.
            float[][] logits = new float[1][sId2Label.size()];

            // Tensors are the typed multidimensional arrays consumed/produced by the TFLite graph.
            // These input tensor descriptors tell us which slot expects attention_mask vs input_ids.
            // This if-else block exists to route inputs by tensor name so inference remains correct even if
            // exported input order changes across model conversions/builds.
            // Basically the model expects two inputs: input_ids and attention_mask, but the order they appear in the TFLite graph can vary,
            // so we check the tensor names to ensure we feed the inputs in the correct order.
            
            org.tensorflow.lite.Tensor inputTensor0 = sTFLiteInterpreter.getInputTensor(0);
            org.tensorflow.lite.Tensor inputTensor1 = sTFLiteInterpreter.getInputTensor(1);
            Object[] inputs;
            // If the model is exported with input order [attention_mask, input_ids], then we feed in that order.
            if (inputTensor0.name().contains("attention_mask")) {
                inputs = new Object[]{attention, input};
            } 
            // If the model is exported with input order [input_ids, attention_mask], then we feed in that order.
            else if (inputTensor0.name().contains("input_ids")) {
                inputs = new Object[]{input, attention};
            } 
            else {
                Log.w("ScramblerMainActivity", "Unexpected TFLite input order: "
                        + inputTensor0.name() + ", " + inputTensor1.name());
                return false;
            }
            java.util.Map<Integer, Object> outputs = new java.util.HashMap<>();
            outputs.put(0, logits);
            // Why are we not storing the output of the inference in a Tensor object like we do for inputs? 
            sTFLiteInterpreter.runForMultipleInputsOutputs(inputs, outputs);
            // Because the TFLite Java API allows us to directly write the output 
            // into a pre-allocated Java array (logits), so we don't need to create 
            // a separate Tensor object for outputs. 
            // The runForMultipleInputsOutputs method will fill the logits array with the model's output values.
            Log.d("ScramblerMainActivity", "input_ids preview: " + Arrays.toString(input[0]));
            Log.d("ScramblerMainActivity", "attention_mask preview: " + Arrays.toString(attention[0]));
            Log.d("ScramblerMainActivity", "logits: " + Arrays.toString(logits[0]));
            int predIdx = 0;
            float maxLogit = logits[0][0];
            // Determining which class has the highest logit score. 
            // This is for binary classification, so I could just check if logits[0][1] > logits[0][0].
            if (logits[0][0] >= logits[0][1]) { // Class 0 has higher logit, so we predict class 0 (i.e. encrypt)
                predIdx = 0;                    // Here I use >= to be more likely to predict encrypt when the model is uncertain,
                maxLogit = logits[0][0];        //since false negatives (not encrypting when we should) are worse than false positives in this use case.
            } 
            else if (logits[0][1] > logits[0][0]) {// Class 1 has higher logit, so we predict class 1 (i.e. not_encrypt)
                predIdx = 1;
                maxLogit = logits[0][1];
            }
            String label = sId2Label.get(predIdx); // This is the human readable label for the predicted class index, i.e. "encrypt" or "not_encrypt"
            Log.d("ScramblerMainActivity", "Classifier prediction: " + predIdx + " -> " + label);
            return "encrypt".equalsIgnoreCase(label);
        } catch (Exception e) {
            Log.e("ScramblerMainActivity", "Classifier inference error", e);
            return false;
        }
    }
}