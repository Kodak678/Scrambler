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
import java.util.Set;

import rkr.simplekeyboard.inputmethod.R;
import rkr.simplekeyboard.inputmethod.latin.inputlogic.InputLogic.CryptoType;

import com.google.crypto.tink.config.TinkConfig;
import com.google.crypto.tink.signature.SignatureConfig;

public class ScramblerMainActivity extends AppCompatActivity {

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

        // Initial load
        loadContacts();
    }

    // Since we want the contact list to always be up to date, we also call loadContacts in onResume.
    // The list of contacts will not be updated in real time when we add/edit/delete a contact, 
    // so we need to refresh the list when we come back to the main activity.
    @Override
    protected void onResume() {
        super.onResume();
        loadContacts();
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
}