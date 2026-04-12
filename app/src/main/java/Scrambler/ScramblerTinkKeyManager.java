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

public class ScramblerTinkKeyManager {

    private static final String MASTER_KEY_URI = "android-keystore://scrambler_master_key";

    // Identity signing key (one per user)
    private static final String SIGNING_KEYSET_NAME = "scrambler_signing_keyset";
    private static final String SIGNING_PREF_FILE = "scrambler_signing_prefs";

    // Per‑contact encryption keys
    private static final String ENCRYPTION_KEYSET_PREFIX = "scrambler_enc_";
    private static final String ENCRYPTION_PREF_FILE = "scrambler_encryption_prefs";

    private final Context context;
    private KeysetHandle signingKeyset;
    private final SharedPreferences encryptionPrefs;

    public ScramblerTinkKeyManager(Context context) throws GeneralSecurityException, IOException {
        this.context = context.getApplicationContext();
        this.encryptionPrefs = context.getSharedPreferences(ENCRYPTION_PREF_FILE, Context.MODE_PRIVATE);
        initSigningKeyset();
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
        return getEncryptionPublicKeyForContact(contactName);
    }

    /**
     * Accept handshake – takes the encryption public key from the other party
     * and stores it for sending encrypted messages.
    */
    public void acceptHandshake(String contactName, String encryptionPublicKeyB64) throws IOException {
        if (encryptionPublicKeyB64 == null || encryptionPublicKeyB64.trim().isEmpty()) {
            throw new IllegalArgumentException("Invalid encryption public key");
        }
        storeContactEncryptionPublicKey(contactName, encryptionPublicKeyB64.trim());
    }

    // Helper methods for storing contact public keys
    private void storeContactEncryptionPublicKey(String contactName, String publicKeyB64) {
        encryptionPrefs.edit().putString(contactName + "_enc_pub", publicKeyB64).apply();
    }

    private String getStoredContactEncryptionPublicKey(String contactName) {
        return encryptionPrefs.getString(contactName + "_enc_pub", null);
    }

    private void storeContactSigningPublicKey(String contactName, String publicKeyB64) {
        encryptionPrefs.edit().putString(contactName + "_sign_pub", publicKeyB64).apply();
    }

    private String getStoredContactSigningPublicKey(String contactName) {
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
        encryptionPrefs.edit().remove(keysetName).apply();
        encryptionPrefs.edit().remove(contactName + "_enc_pub").apply();
        encryptionPrefs.edit().remove(contactName + "_sign_pub").apply();
    }

    // -------------------- GET ALL CONTACTS --------------------

    public Set<String> getAllContacts() {
        SharedPreferences prefs = context.getSharedPreferences(ENCRYPTION_PREF_FILE, Context.MODE_PRIVATE);
        Map<String, ?> allEntries = prefs.getAll();
        Set<String> contactNames = new HashSet<>();

        for (Map.Entry<String, ?> entry : allEntries.entrySet()) {
            String key = entry.getKey();
            if (key.endsWith("_enc_pub")) {
                String cleanName = key.replace("_enc_pub", "");
                contactNames.add(cleanName);
            }
        }
        return contactNames;
    }

}