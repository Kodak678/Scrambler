# Scrambler

Scrambler is a cryptography-focused Android keyboard built on top of Simple Keyboard.

## About

Features:
- Hybrid Encryption
- Decryption
- Signing
- Verifying
- Handshake (offering and accepting)
- Sensitivity classification using a fine-tuned DistilBERT model
- QR code generation for keys
- QR code scanning for importing keys

Feature it doesn't have:
- Emojis
- GIFs
- Spell checker
- Swipe typing

## Running the App

1. Download Android Studio from https://developer.android.com/studio/releases.
2. Create a device emulator in Android Studio by following https://developer.android.com/studio/run/managing-avds.
3. Choose a hardware profile from the Phone category.
4. Select any system image with API 26 or above, such as API 35+, API 36+, or newer.
5. Build and run the app from Android Studio after the emulator is ready.
6. Open extended controls for the emulator and set the cellular network to 5G. For additional guidance, see https://developer.android.com/studio/run/emulator-extended-controls.
7. Enable notifications for the Scrambler app in the device/emulator settings to see sensitivity classification results in toast notifications.
8. To test the QR code scanning feature on the device emulator, you can screen shot the QR code image from one emulator and upload the screenshot to the other emulator via the camera option in the extended controls of the emulator. For more details, see https://developers.google.com/ar/develop/java/emulator#add_augmented_images_to_the_scene.
 - The actualy image will appear inside a virtual room and you can scan the QR code by moving your avatar around the virtual room until the QR code is detected and scanned by the app. The QR code will be on the wall or table in the virtual room in the kitchen behind the dog. See this webpage for how instructions on how to move around the virtual room: https://developers.google.com/ar/develop/java/emulator#control_the_virtual_scene.

## Running SensitivityClassification.ipynb

I recommend running the notebook in Google Colaboratory at https://developers.google.com/colab.

Before running the notebook, upload these files to Colab:
- Fn_dataset.jsonl
- sensitive_data_password.csv
- synthetic_dataset.csv

If you want to deploy the model on Android devices, use TensorFlow 2.16.1 and transformers 4.40.0.

## Use Cases

### 1. Choosing a contact

**Instructions**

- To choose a contact for encryption, decryption, signing, or verifying, follow these steps:
  1. Open the Scrambler keyboard in any app that allows text input.
  2. Hold down the "," comma key to open the cryptographic operations keyboard.
  3. Press the "choose contact" key to open the contact selection dropdown.

#### Opening the keyboard: <br/>
![Use case 0](images\whereIsKeyboard.png)
<br/>
#### Selecting a contact:   <br/>
![Use case 1](images\contactSelection.png)

### 2. Encrypting a message

**Instructions**

- To encrypt a message, follow these steps:
  1. Open the Scrambler keyboard in any app that allows text input.
  2. Hold down the "," comma key to open the cryptographic operations keyboard.
  3. Press the "encrypt" key to encrypt the last 1024 characters of the text input field using the selected contact's public key.
  4. OR select a portion of the text input field and press the "encrypt" key to encrypt the selected text using the selected contact's public key.

![Use case 2](images\EncryptionFlow.png)

### 3. Decrypting a message

**Instructions**

- To decrypt a message, follow these steps:
  1. Open the Scrambler keyboard in any app that allows text input.
  2. Hold down the "," comma key to open the cryptographic operations keyboard.
  3. Select a portion of the text and copy it to the clipboard.
  4. Press the "decrypt" key to decrypt the copied text using your private key and the decrypted message will be displayed in a popup.

![Use case 3](images\DecryptionFlow.png)

### 4. Signing a message

**Instructions**

- To sign a message, follow these steps:
  1. Open the Scrambler keyboard in any app that allows text input.
  2. Hold down the "," comma key to open the cryptographic operations keyboard.
  3. Press the "sign" key to sign the last 1024 characters of the text input field using your private key.
  4. OR select a portion of the text input field and press the "sign" key to sign the selected text using your private signing key.

![Use case 4](images\SigningFlow.png)

### 5. Verifying a signed message

**Instructions**

- To verify a signed message, follow these steps:
  1. Open the Scrambler keyboard in any app that allows text input.
  2. Hold down the "," comma key to open the cryptographic operations keyboard.
  3. Select a portion of the text and copy it to the clipboard.
  4. Press the "verify" key to verify the signature of the copied text using the sender's public key and the verification result will be displayed in a popup.

![Use case 5](images\VerificationFlow.png)

### 6. Offering a handshake

**Instructions**

- To offer a handshake, follow these steps:
  1. Open the Scrambler keyboard in any app that allows text input.
  2. Hold down the "," comma key to open the cryptographic operations keyboard.
  3. Press the "offer handshake" key to paste a handshake payload message containing a public key into the text input field. The recipient can then copy this handshake payload message to accept the handshake and use your public key for future encryption.

![Use case 6](images\HandshakeFlow.png)

### 7. Accepting a handshake

**Instructions**

- To accept a handshake, follow these steps:
  1. Open the Scrambler keyboard in any app that allows text input.
  2. Hold down the "," comma key to open the cryptographic operations keyboard.
  3. Select the handshake payload message and copy it to the clipboard.
  4. Press the "accept handshake" key to accept the handshake and use the sender's public key for future encryption.

![Use case 7](images\AcceptHandshakeFlow.png)

### 8. Classify message sensitivity

**Instructions**
- We don't store your data. Period. The sensitivity classification is performed locally on your device using a fine-tuned DistilBERT model, and the text you want to classify never leaves your device. 

- Enable notifications for the Scrambler app to see the sensitivity classification results in toast notifications.

- To classify the sensitivity of a message, follow these steps:
  1. Open the Scrambler keyboard in any app that allows text input.
  2. Hold down the "," comma key to open the cryptographic operations keyboard.
  3. Press the "Should I encrypt?" key to classify the sensitivity of the last 1024 characters of the text input field using a fine-tuned DistilBERT model.
  4. OR select a portion of the text input field and press the "classify sensitivity" key to classify the sensitivity of the selected text using a fine-tuned DistilBERT model. The sensitivity classification result will be outputted in a toast notification.

![Use case 8](images\ClassificationFlow.png)

### 9. Viewing all contacts

**Instructions**

- Open the Scrambler main app to view all your contacts and their associated public keys. You can also manage your contacts by adding new ones, editing existing ones, or deleting existing ones from this screen.

![Use case 9](images\ScramblerMainActivity.png)

### 10. Adding a new contact

**Instructions**

- To add a new contact, follow these steps:
  1. Open the Scrambler main app to view all your contacts.
  2. Press the "add user" button to open the add contact screen.
  3. Enter the contact's name and public key and/or signing key in the respective fields and press the "save" button to save the new contact.
  4. Valid contact name consists of only letters a-z (case-insensitive) and digits 0-9.
  5. Pressing the "GENERATE NEW KEY PAIR" button will automatically generate a new public/private key pair for the contact and fill in the public key field with the generated public key. 

![Use case 10](images\AddUserActivity.png)

### 11. Editing an existing contact

**Instructions**

- To edit an existing contact, follow these steps:
  1. Open the Scrambler main app to view all your contacts.
  2. Select the contact you want to edit.
  3. Press the edit icon to open the edit contact screen.
  4. Modify the contact's public key and/or signing key in the respective fields and press the "save" button to save the changes.

![Use case 11](images\EditUserActivity.png)

### 12. Viewing your own signing key

**Instructions**

- To view your own signing key, follow these steps:
  1. Open the Scrambler main app to view all your contacts.
  2. Press the "What's my signing key?" button to view your own signing key.

![Use case 12](images\WhatsMySingingkey.png)


## Credits

This keyboard is based on the Simple Keyboard whose original source code you can find here: https://github.com/rkkr/simple-keyboard
