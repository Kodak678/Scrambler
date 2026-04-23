package Scrambler;

import android.content.Context;
import android.content.SharedPreferences;

import com.google.crypto.tink.CleartextKeysetHandle;
import com.google.crypto.tink.HybridDecrypt;
import com.google.crypto.tink.HybridEncrypt;
import com.google.crypto.tink.JsonKeysetReader;
import com.google.crypto.tink.JsonKeysetWriter;
import com.google.crypto.tink.KeysetHandle;
import com.google.crypto.tink.PublicKeySign;
import com.google.crypto.tink.PublicKeyVerify;
import com.google.crypto.tink.hybrid.HybridKeyTemplates;
import com.google.crypto.tink.integration.android.AndroidKeysetManager;
import com.google.crypto.tink.signature.SignatureKeyTemplates;
import com.google.crypto.tink.subtle.Base64;

import java.io.IOException;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import android.util.Log;

public class ScramblerTinkKeyManager {

    private static final String MASTER_KEY_URI = "android-keystore://scrambler_master_key";

    // Identity signing key (one per user)
    private static final String SIGNING_KEYSET_NAME = "scrambler_signing_keyset";
    private static final String SIGNING_PREF_FILE = "scrambler_signing_prefs";

    // Per‑contact encryption keys
    private static final String ENCRYPTION_KEYSET_PREFIX = "scrambler_enc_";
    private static final String ENCRYPTION_PREF_FILE = "scrambler_encryption_prefs";

    // Contact existence marker key suffix
    private static final String CONTACT_EXISTS_SUFFIX = "_exists";

    private final Context context;
    private KeysetHandle signingKeyset;
    private final SharedPreferences encryptionPrefs;

    public ScramblerTinkKeyManager(Context context) throws GeneralSecurityException, IOException {
        this.context = context.getApplicationContext();
        this.encryptionPrefs = context.getSharedPreferences(ENCRYPTION_PREF_FILE, Context.MODE_PRIVATE);
        initSigningKeyset();
    }

    // Mark a contact as existing
    private void markContactExists(String contactName) {
        encryptionPrefs.edit().putBoolean(contactName + CONTACT_EXISTS_SUFFIX, true).apply();
    }
       
    // -------------------- IDENTITY SIGNING KEY --------------------
    private void initSigningKeyset() throws GeneralSecurityException, IOException {
        AndroidKeysetManager manager = new AndroidKeysetManager.Builder()
                .withSharedPref(context, SIGNING_KEYSET_NAME, SIGNING_PREF_FILE)
                .withKeyTemplate(SignatureKeyTemplates.ECDSA_P256)
                .withMasterKeyUri(MASTER_KEY_URI)
                .build();
        this.signingKeyset = manager.getKeysetHandle();
    }

    // Force regeneration of the signing key (dangerous: old signatures will not verify)
    public void forceRegenerateSigningKey() throws GeneralSecurityException, IOException {
        // Remove the old keyset from SharedPreferences
        SharedPreferences prefs = context.getSharedPreferences(SIGNING_PREF_FILE, Context.MODE_PRIVATE);
        prefs.edit().remove(SIGNING_KEYSET_NAME).apply();
        // Reinitialize (will generate a new key)
        initSigningKeyset();
    }

    public String getIdentitySigningPublicKey() throws GeneralSecurityException, IOException {
        KeysetHandle publicHandle = signingKeyset.getPublicKeysetHandle();
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        CleartextKeysetHandle.write(publicHandle, JsonKeysetWriter.withOutputStream(out));
        byte[] serialized = out.toByteArray();
        return Base64.encodeToString(serialized, Base64.NO_WRAP);
    }

    public String signMessage(String message) throws GeneralSecurityException {
        PublicKeySign signer = signingKeyset.getPrimitive(PublicKeySign.class);
        byte[] signature = signer.sign(message.getBytes(StandardCharsets.UTF_8));
        return Base64.encodeToString(signature, Base64.NO_WRAP);
    }

    public boolean verifySignature(String senderPublicKeyB64, String message, String signatureB64) {
        try {
            byte[] publicKeyBytes = Base64.decode(senderPublicKeyB64, Base64.NO_WRAP);
            KeysetHandle publicHandle = CleartextKeysetHandle.read(JsonKeysetReader.withBytes(publicKeyBytes));
            PublicKeyVerify verifier = publicHandle.getPrimitive(PublicKeyVerify.class);
            verifier.verify(Base64.decode(signatureB64, Base64.NO_WRAP), message.getBytes(StandardCharsets.UTF_8));
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    // -------------------- PER‑CONTACT ENCRYPTION KEYS --------------------
    private KeysetHandle getOrCreateEncryptionKeyset(String contactName) throws GeneralSecurityException, IOException {
        String keysetName = ENCRYPTION_KEYSET_PREFIX + contactName;
        AndroidKeysetManager manager = new AndroidKeysetManager.Builder()
                .withSharedPref(context, keysetName, ENCRYPTION_PREF_FILE)
                .withKeyTemplate(HybridKeyTemplates.ECIES_P256_HKDF_HMAC_SHA256_AES128_GCM)
                .withMasterKeyUri(MASTER_KEY_URI)
                .build();
        return manager.getKeysetHandle();
    }

    public String getEncryptionPublicKeyForContact(String contactName) throws GeneralSecurityException, IOException {
        KeysetHandle keyset = getOrCreateEncryptionKeyset(contactName);
        KeysetHandle publicHandle = keyset.getPublicKeysetHandle();
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        CleartextKeysetHandle.write(publicHandle, JsonKeysetWriter.withOutputStream(out));
        byte[] serialized = out.toByteArray();
        return Base64.encodeToString(serialized, Base64.NO_WRAP);
    }

    // -------------------- HANDSHAKE METHODS (OFFER / ACCEPT) --------------------

    /**
     * Offer handshake – generates a key pair for the given contact,
     * and returns only the public encryption key as a Base64 string.
     */
    public String offerHandshake(String contactName) throws GeneralSecurityException, IOException {
        // Delete the old private key half of the keyset
        // Does not delete the  public key stored for the contact (this is the contact's own public key), 
        // so we do not end up in an eternal loop of regenerating keys 
        // where one user offers a handshake, the other accepts and stores the public key, 
        // then the second one offers a handshake which deletes the public key they just got 
        // from the first one, then the first one accepts and stores the new public key, 
        // then the first one offers again which deletes the public key they just got from the second one, etc.
        // This however means that this is only a half handshake 
        // as the contact's public key will still be used for encryption until they initiate a new handshake. 
        // Meaning the contact can still decrypt messages sent by us until they offer a new handshake, 
        // but we will not be able to decrypt messages sent by them until they accept our handshake.
        String keysetName = ENCRYPTION_KEYSET_PREFIX + contactName;
        encryptionPrefs.edit().remove(keysetName).commit();
   
        markContactExists(contactName);
        return getEncryptionPublicKeyForContact(contactName);
    }

    /**
     * Accept handshake – takes the encryption public key from the other party
     * and stores it for sending encrypted messages.
    */
    public boolean acceptHandshake(String contactName, String encryptionPublicKeyB64) {
        if (encryptionPublicKeyB64 == null || encryptionPublicKeyB64.trim().isEmpty()) {
            return false;
        }
        try {
            storeContactEncryptionPublicKey(contactName, encryptionPublicKeyB64.trim());
            markContactExists(contactName);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    // Helper methods for storing contact public keys
    private void storeContactEncryptionPublicKey(String contactName, String publicKeyB64) {
        encryptionPrefs.edit().putString(contactName + "_enc_pub", publicKeyB64).apply();
    }

    public String getStoredContactEncryptionPublicKey(String contactName) {
        return encryptionPrefs.getString(contactName + "_enc_pub", null);
    }

    public void storeContactSigningPublicKey(String contactName, String publicKeyB64) {
        encryptionPrefs.edit().putString(contactName + "_sign_pub", publicKeyB64).apply();
        markContactExists(contactName);
    }

    public String getStoredContactSigningPublicKey(String contactName) {
        return encryptionPrefs.getString(contactName + "_sign_pub", null);
    }

    // -------------------- ENCRYPT / DECRYPT USING STORED CONTACT KEYS --------------------
    public String encryptForContact(String contactName, String plaintext) throws GeneralSecurityException, IOException {
        String contactPublicKeyB64 = getStoredContactEncryptionPublicKey(contactName);
        if (contactPublicKeyB64 == null) {
            throw new IllegalStateException("No public key stored for contact: " + contactName);
        }
        byte[] publicKeyBytes = Base64.decode(contactPublicKeyB64, Base64.NO_WRAP);
        KeysetHandle contactPublicHandle = CleartextKeysetHandle.read(JsonKeysetReader.withBytes(publicKeyBytes));
        HybridEncrypt encryptor = contactPublicHandle.getPrimitive(HybridEncrypt.class);
        byte[] ciphertext = encryptor.encrypt(plaintext.getBytes(StandardCharsets.UTF_8), null);
        return Base64.encodeToString(ciphertext, Base64.NO_WRAP);
    }

    public String decryptFromContact(String contactName, String ciphertextB64) throws GeneralSecurityException, IOException {
        KeysetHandle ourKeyset = getOrCreateEncryptionKeyset(contactName);
        HybridDecrypt decryptor = ourKeyset.getPrimitive(HybridDecrypt.class);
        byte[] ciphertext = Base64.decode(ciphertextB64, Base64.NO_WRAP);
        byte[] plaintext = decryptor.decrypt(ciphertext, null);
        return new String(plaintext, StandardCharsets.UTF_8);
    }

    // -------------------- VERIFY SIGNATURE FROM CONTACT --------------------
    public boolean verifyContactSignature(String contactName, String message, String signatureB64) {
        String contactSigningKey = getStoredContactSigningPublicKey(contactName);
        if (contactSigningKey == null) return false;
        return verifySignature(contactSigningKey, message, signatureB64);
    }

    // -------------------- DELETE CONTACT --------------------
    public void deleteContact(String contactName) {
        String keysetName = ENCRYPTION_KEYSET_PREFIX + contactName;
        encryptionPrefs.edit().remove(keysetName).commit();
        encryptionPrefs.edit().remove(contactName + CONTACT_EXISTS_SUFFIX).commit();
        encryptionPrefs.edit().remove(contactName + "_enc_pub").commit();
        encryptionPrefs.edit().remove(contactName + "_sign_pub").commit();

        // Also clear currently-selected contact if it matches the deleted one
        SharedPreferences prefs = context.getSharedPreferences("scrambler_prefs", Context.MODE_PRIVATE);
        String selected = prefs.getString("currently-selected-contact", null);
        if (selected != null && selected.equals(contactName)) {
            prefs.edit().remove("currently-selected-contact").commit();
        }
    }

    // -------------------- GET ALL CONTACTS --------------------

    public Set<String> getAllContacts() {
        SharedPreferences prefs = context.getSharedPreferences(ENCRYPTION_PREF_FILE, Context.MODE_PRIVATE);
        Map<String, ?> allEntries = prefs.getAll();
        Set<String> contactNames = new HashSet<>();

        for (Map.Entry<String, ?> entry : allEntries.entrySet()) {
            String key = entry.getKey();
            if (key.endsWith(CONTACT_EXISTS_SUFFIX) && Boolean.TRUE.equals(entry.getValue())) {
                String cleanName = key.substring(0, key.length() - CONTACT_EXISTS_SUFFIX.length());
                contactNames.add(cleanName);
            }
        }
        return contactNames;
    }

}