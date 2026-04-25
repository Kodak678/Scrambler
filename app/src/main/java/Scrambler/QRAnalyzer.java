package Scrambler;

import androidx.annotation.NonNull;
import androidx.camera.core.ImageAnalysis;
import androidx.camera.core.ImageProxy;
import com.google.mlkit.vision.barcode.BarcodeScanner;
import com.google.mlkit.vision.barcode.BarcodeScannerOptions;
import com.google.mlkit.vision.barcode.BarcodeScanning;
import com.google.mlkit.vision.barcode.common.Barcode;
import com.google.mlkit.vision.common.InputImage;

public class QRAnalyzer implements ImageAnalysis.Analyzer {
    private final BarcodeScanner scanner;
    private final OnQrFoundListener listener;

    public interface OnQrFoundListener {
        void onQrFound(String qrCode);
    }

    public QRAnalyzer(OnQrFoundListener listener) {
        this.listener = listener;
        // Optimize for QR codes only to improve speed
        // Scanner will ignore other barcode formats
        BarcodeScannerOptions options = new BarcodeScannerOptions.Builder()
                .setBarcodeFormats(Barcode.FORMAT_QR_CODE)
                .build();
        this.scanner = BarcodeScanning.getClient(options);
    }

    @Override
    @androidx.camera.core.ExperimentalGetImage
    public void analyze(@NonNull ImageProxy imageProxy) {
        if (imageProxy.getImage() == null) {
            imageProxy.close();
            return;
        }

        InputImage image = InputImage.fromMediaImage(
                imageProxy.getImage(), 
                imageProxy.getImageInfo().getRotationDegrees()
        );

        scanner.process(image)
            .addOnSuccessListener(barcodes -> {
                for (Barcode barcode : barcodes) {
                    String rawValue = barcode.getRawValue();
                    if (rawValue != null && !rawValue.trim().isEmpty()) {
                        listener.onQrFound(rawValue.trim());
                        break;
                    }
                }
            })
            .addOnCompleteListener(task -> imageProxy.close()); // Always close the frame
    }
}
