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

        Button addUserButton = findViewById(R.id.add_user_button);
        addUserButton.setOnClickListener(new View.OnClickListener() {
        @Override
        public void onClick(View v) {
            Intent intent = new Intent(ScramblerMainActivity.this, Scrambler.AddUserActivity.class);
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
                    Toast.makeText(ScramblerMainActivity.this, "Error deleting contact: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                }
            });

            contactsContainer.addView(contactRow);
        }
    }

    public static String processText(String inputText, CryptoType cryptoType, String selectedContact) {
        
        if (cryptoType == CryptoType.ENCRYPT) 
        {
            return "[Encrypted]" + selectedContact;
        } 
        else if (cryptoType == CryptoType.DECRYPT) 
        {
            return "[Decrypted]" + inputText + "||" + selectedContact + "[Decrypted]"; 
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