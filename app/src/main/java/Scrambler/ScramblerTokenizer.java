package Scrambler;

import android.content.Context;
import android.util.Log;

import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Arrays;
import java.util.Map;

public class ScramblerTokenizer {
    private static Map<String, Integer> vocab = null;
    // Special token ids based on the DistilBert tokenizer configuration. 
    // These are standard for BERT-based models and must match the ids used during model training for correct behavior.
    // So I manually read the tokenizer.json file to confirm these values.
    private static int clsTokenId = 101;
    private static int sepTokenId = 102;
    private static int padTokenId = 0;
    private static int unkTokenId = 100;

    private static synchronized void ensureTokenizerLoaded(Context context) {
       
        //   This method ensures the tokenizer vocabulary is loaded into memory.
        //   Loading the vocabulary once avoids repeated I/O and expensive parsing.
        //   Synchronized because multiple threads may request the tokenizer concurrently.
         
        // If we've already loaded the vocab: STOP.
        if (vocab != null) {
            return;
        }

        // Attempt to read the tokenizer JSON from the app assets and parse the "vocab" map.
        try (InputStream is = context.getAssets().open("distilbert_tokenizer/tokenizer.json");
            ByteArrayOutputStream bos = new ByteArrayOutputStream()) {
            // Read the asset stream in chunks into the byte buffer.
            byte[] buffer = new byte[8192];
            int read;
            // Looping read until EOF (-1). This is to copy the JSON content safely.
            while ((read = is.read(buffer)) != -1) {
                bos.write(buffer, 0, read);
            }

            // Convert the accumulated bytes into a UTF-8 string containing the JSON.
            String json = bos.toString(StandardCharsets.UTF_8.name());
            // Parse JSON: tokenizerObj -> model -> vocab. Purpose: locate the token->id map.
            // Essentially, we want tokenizerObj["model"]["vocab"] which is a dict of token string to integer id.
            JSONObject tokenizerObj = new JSONObject(json);
            JSONObject modelObj = tokenizerObj.getJSONObject("model");
            JSONObject vocabObj = modelObj.getJSONObject("vocab");

            // Set the vocab attribute from null to a new HashMap and populate from the JSON object.
            vocab = new HashMap<>();
            for (Iterator<String> it = vocabObj.keys(); it.hasNext(); ) {
                String key = it.next();
                // Map token string -> integer id.
                vocab.put(key, vocabObj.getInt(key));
            }

        } catch (Exception e) {
            // On any failure, log the error
            // and set vocab to null to indicate the tokenizer is unavailable.
            // Not really an fallbacks here but if something goes wrong
            // The app will predict "encrypt" by default
            Log.e("ScramblerTokenizer", "Error loading tokenizer", e);
            vocab = null;
        }
    }

    //   This method performs a simple whitespace/punctuation-aware tokenization.
    //   This produces tokens that the WordPiece tokenizer can further split.
    //   This method lowercases input as distilbert-uncased was trained with lowercase text, 
    //   groups contiguous letters/digits/apostrophes
    //   into word tokens and adds punctuation as separate tokens.
    private static List<String> basicTokenize(String text) {
        
        List<String> tokens = new ArrayList<>();

        // No token? then return empty token list.
        if (text == null || text.isEmpty()) {
            return tokens;
        }

        // Normalize to lowercase to make token matching case-insensitive.
        String normalized = text.toLowerCase(Locale.ROOT);
        // Reusable buffer for building the current wordlike token.
        StringBuilder current = new StringBuilder();

        // Iterate over each character to split words and punctuation.
        for (int i = 0; i < normalized.length(); i++) {
            char ch = normalized.charAt(i);
            // If character is alphanumeric or an apostrophe, append to current token.
            if (Character.isLetterOrDigit(ch) || ch == '\'') {
                current.append(ch);
                // Continue to build a multi-character word token.
                continue;
            }

            // If we hit a non-word character and we have an accumulated token,
            // add it to the token list and reset the buffer.
            if (current.length() > 0) {
                tokens.add(current.toString());
                current.setLength(0);
            }

            // Non whitespace punctuation like (, ',', '.', '?') are emitted as its
            // own token to allow the WordPiece tokenizer to handle them appropriately.
            if (!Character.isWhitespace(ch)) {
                tokens.add(String.valueOf(ch));
            }
        }

        // After the loop, if there's a remaining buffered token, add it.
        if (current.length() > 0) {
            tokens.add(current.toString());
        }
        return tokens;
    }

    //  This method applies WordPiece-style tokenization to a single token.
    //  WordPiece breaks words into subword units that exist in the model's
    //  vocabulary, enabling open-vocabulary handling and shared subword reuse.
    // The code here was inspired by the original WordPiece algorithm as described in the BERT paper 
    // and implemented in Hugging Face's tokenizers library which can be found here:
    // https://github.com/huggingface/tflite-android-transformers/blob/master/bert/src/main/java/co/huggingface/android_transformers/bertqa/tokenization/WordpieceTokenizer.java 
    private static List<Integer> wordPieceTokenize(String token) {
        // Example: For the token "HuggingFace", the WordPiece tokenizer might produce:
        // HuggingFace
        // |HuggingFace|
        // |Hugging|##Face|
        // |Hugging|##F|##ace|
        List<Integer> ids = new ArrayList<>();

        // If the vocab isn't loaded or token is empty, return the unknown id.
        if (vocab == null || token == null || token.isEmpty()) {
            ids.add(unkTokenId);
            return ids;
        }

        // Greedy longest match first algorithm: try to find the longest substring
        // from the current start position that appears in the vocabulary.
        int start = 0;
        while (start < token.length()) {
            // Try matching from the end of the token back to start to find the
            // longest candidate subtoken.
            int end = token.length();
            String currentSubtoken = null;

            // Inner loop: shrink the candidate end until we find a known piece.
            while (start < end) {
                String piece = token.substring(start, end);
                // If this is not the first piece, add the WordPiece prefix '##'
                // to indicate a continuation subtoken as per standard convention.
                if (start > 0) {
                    piece = "##" + piece;
                }
                // If the piece exists in vocab, we've found the longest match here.
                if (vocab.containsKey(piece)) {
                    currentSubtoken = piece;
                    break;
                }
                // Otherwise, shorten the candidate by one character and try again.
                end--;
            }

            // If no subtoken matched at this position, return unknown for the whole
            // token: this preserves model expectations for unknown/rare words.
            if (currentSubtoken == null) {
                ids.clear();
                ids.add(unkTokenId);
                return ids;
            }

            // Append the numeric id for the matched subtoken and advance start.
            ids.add(vocab.get(currentSubtoken));
            start = end; // Move the sliding window forward to continue tokenizing.
        }

        return ids;
    }

    
    //   This method converts an input string into a fixed-length array of token ids.
    //   This is necessary because the model expects a numeric tensor of token ids with special tokens
    //   (CLS/SEP) and padding to a fixed `maxLen`.
    //   Steps:
    //    - Ensure vocab is loaded
    //    - Build token id list using basic + WordPiece tokenization
    //    - Add special tokens
    //    - Copy into a padded/truncated fixed size array
    public static synchronized long[] encode(String text, Context context, int maxLen) {

        // Example trace for the input "What is an API key?", the tokenization process would be:
        // Input text: What is an API key?
        // Tokenization trace: [[CLS], what -> [2054], is -> [2003], an -> [2019], api -> [17928], key -> [3145], ? -> [1029], [SEP]]
        // Token ids before padding/truncation: [101, 2054, 2003, 2019, 17928, 3145, 1029, 102]
        // Encoded ids (maxLen=128): [101, 2054, 2003, 2019, 17928, 3145, 1029, 102, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0]


        // Make sure the vocabulary is available before tokenizing.
        ensureTokenizerLoaded(context);

        // Initialize output array filled with the pad token id. This ensures any
        // unused positions contain the padding id the model expects.
        // Need to pad to maxLen (128 tokens) because the model input must be a fixed size tensor. 
        // The model will ignore padding tokens.
        long[] inputIds = new long[maxLen];
        for (int i = 0; i < maxLen; i++) {
            inputIds[i] = padTokenId;
        }

        // If the vocab failed to load, return the padding only array
        // There is no fallback tokenization strategy here
        // If the model is feed nothing but padding tokens it 
        // will predict the most common class which is "encrypt" in our case
        if (vocab == null) {
            return inputIds;
        }

        // Build a dynamic list of token ids: start with CLS.
        List<Integer> tokenIds = new ArrayList<>();
        tokenIds.add(clsTokenId);

        // Optional debug trace: record how each basic token was split into ids. 
        // Commented out to avoid overhead in production but can be enabled for diagnostics.
        // See example trace in the comments above.
        // List<String> debugPieces = new ArrayList<>();
        // debugPieces.add("[CLS]");

        // Tokenize the input text into basic tokens, then apply WordPiece to each.
        for (String token : basicTokenize(text)) {
            // Convert the basic token into one or more WordPiece ids.
            List<Integer> pieces = wordPieceTokenize(token);
            // Append the resulting ids to the token id stream.
            tokenIds.addAll(pieces);
            // Add a human readable trace entry for diagnostics.
            // debugPieces.add(token + " -> " + pieces.toString());
        }

        // Append the final SEP token id to mark the end of sequence.
        tokenIds.add(sepTokenId);
        // debugPieces.add("[SEP]");

        // Log inputs and tokenization for debugging purposes.
        // Log.d("ScramblerTokenizer", "Input text: " + text);
        // Log.d("ScramblerTokenizer", "Tokenization trace: " + debugPieces.toString());
        // Log.d("ScramblerTokenizer", "Token ids before padding/truncation: " + tokenIds.toString());

        // Copy as many token ids as will fit into the fixed-size output array.
        int limit = Math.min(maxLen, tokenIds.size());
        for (int i = 0; i < limit; i++) {
            inputIds[i] = tokenIds.get(i);
        }

        // Final encoded array ready for model input (contains padding if needed).
        Log.d("ScramblerTokenizer", "Encoded ids (maxLen=" + maxLen + "): " + Arrays.toString(inputIds));

        return inputIds;
    }
}