package com.auction.client.util;

import javafx.stage.FileChooser;
import javafx.stage.Window;

import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.util.Base64;

/**
 * ImageUtil — centralised image processing utility for the Auction client.
 *
 * <p>Encapsulates the complete pipeline used in SellerDashboardController:
 * <ol>
 *   <li>Open a single-file {@link FileChooser} filtered to common image types.</li>
 *   <li>Read the selected file into a {@link BufferedImage} via {@link ImageIO}.</li>
 *   <li>Resize proportionally so neither dimension exceeds {@value #MAX_DIMENSION}px,
 *       using {@code Graphics2D} with {@code BILINEAR} interpolation.</li>
 *   <li>Compress to JPEG at {@value #JPEG_QUALITY} quality via {@link ImageWriter}.</li>
 *   <li>Return the result as a Base64-encoded {@link String} ready to embed in a DTO.</li>
 * </ol>
 *
 * <p>All parameters ({@code MAX_DIMENSION}, {@code JPEG_QUALITY}) exactly match the
 * values in {@code SellerDashboardController.handleImagePicker} so the server receives
 * identically-formatted data regardless of which UI triggered the upload.
 *
 * <p>Returns {@code null} when the user cancels the dialog or any processing error
 * occurs, so callers never need to handle a checked exception.
 */
public final class ImageUtil {

    // ── Pipeline constants (must stay in sync with SellerDashboardController) ─
    /**
     * Maximum allowed width or height in pixels after resizing.
     * Images smaller than this are kept at their original dimensions.
     * Matches: {@code int maxSize = 600} in SellerDashboardController.
     */
    public static final int MAX_DIMENSION = 600;

    /**
     * JPEG compression quality (0.0 = smallest file, 1.0 = lossless).
     * Matches: {@code param.setCompressionQuality(0.80f)} in SellerDashboardController.
     */
    public static final float JPEG_QUALITY = 0.80f;

    // ── Avatar-specific override ───────────────────────────────────────────────
    /**
     * Smaller cap used when processing avatar images to keep payloads light.
     * Avatars are always displayed small so 256 px is more than sufficient.
     */
    public static final int AVATAR_MAX_DIMENSION = 256;

    private ImageUtil() { /* static utility — never instantiated */ }

    // ==========================================================================
    //  PUBLIC API
    // ==========================================================================

    /**
     * Opens a file-picker for a single image, then runs the full resize →
     * compress → Base64 pipeline using the <em>product-image</em> settings
     * ({@value #MAX_DIMENSION}px / {@value #JPEG_QUALITY} quality).
     *
     * <p>Identical to the logic in
     * {@code SellerDashboardController.handleImagePicker} for a single file.
     *
     * @param ownerWindow the JavaFX window that owns the dialog (may be {@code null})
     * @return Base64 JPEG string, or {@code null} if cancelled / error
     */
    public static String pickAndEncodeImage(Window ownerWindow) {
        return pickAndEncode(ownerWindow, MAX_DIMENSION);
    }

    /**
     * Opens a file-picker for a single avatar image, then runs the pipeline
     * with the smaller {@value #AVATAR_MAX_DIMENSION}px cap so the payload
     * stays minimal.
     *
     * @param ownerWindow the JavaFX window that owns the dialog (may be {@code null})
     * @return Base64 JPEG string, or {@code null} if cancelled / error
     */
    public static String pickAndEncodeAvatar(Window ownerWindow) {
        return pickAndEncode(ownerWindow, AVATAR_MAX_DIMENSION);
    }

    /**
     * Encodes an already-loaded {@link File} without opening a dialog.
     * Useful when you have obtained the file through other means.
     *
     * @param file      the image file to process
     * @param maxPixels the maximum dimension cap to apply
     * @return Base64 JPEG string, or {@code null} on error
     */
    public static String encodeFile(File file, int maxPixels) {
        if (file == null || !file.exists()) return null;
        try {
            return processImage(file, maxPixels);
        } catch (Exception e) {
            System.err.println("[ImageUtil] encodeFile failed for " + file.getName() + ": " + e.getMessage());
            return null;
        }
    }

    /**
     * Decodes a Base64 JPEG string back into raw {@code byte[]} so it can be
     * passed to {@code new Image(new ByteArrayInputStream(bytes))} in JavaFX.
     *
     * @param base64 the Base64 string stored in {@link com.auction.client.model.UserSession}
     * @return raw bytes, or an empty array if the input is blank / invalid
     */
    public static byte[] decodeToBytes(String base64) {
        if (base64 == null || base64.isBlank()) return new byte[0];
        try {
            return Base64.getDecoder().decode(base64);
        } catch (IllegalArgumentException e) {
            System.err.println("[ImageUtil] decodeToBytes — invalid Base64 string: " + e.getMessage());
            return new byte[0];
        }
    }

    // ==========================================================================
    //  INTERNAL IMPLEMENTATION
    // ==========================================================================

    /**
     * Core picker + pipeline method shared by the public overloads.
     *
     * @param ownerWindow the owner window for the dialog (may be {@code null})
     * @param maxPixels   the maximum dimension cap passed to {@link #processImage}
     * @return Base64 JPEG string, or {@code null}
     */
    private static String pickAndEncode(Window ownerWindow, int maxPixels) {
        // ── Step 1: open file chooser ──────────────────────────────────────────
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Chọn ảnh");
        // Extension filter exactly matching SellerDashboardController
        chooser.getExtensionFilters().add(
                new FileChooser.ExtensionFilter(
                        "Ảnh", "*.png", "*.jpg", "*.jpeg", "*.gif", "*.bmp"
                )
        );

        File file = chooser.showOpenDialog(ownerWindow);
        if (file == null) return null; // user cancelled — not an error

        // ── Steps 2-5: delegate to processImage ───────────────────────────────
        try {
            return processImage(file, maxPixels);
        } catch (Exception e) {
            System.err.println("[ImageUtil] Failed to process image " + file.getName() + ": " + e.getMessage());
            return null;
        }
    }

    /**
     * Runs the resize → compress → Base64 pipeline on a single {@link File}.
     *
     * <p>Pipeline (line-for-line equivalent to SellerDashboardController):
     * <pre>
     * BufferedImage original = ImageIO.read(file);
     * // Resize — keep within maxPixels × maxPixels, preserving aspect ratio
     * if (w > maxSize || h > maxSize) {
     *     double ratio = Math.min((double)maxSize/w, (double)maxSize/h);
     *     w = (int)(w*ratio);  h = (int)(h*ratio);
     * }
     * BufferedImage resized = new BufferedImage(w, h, TYPE_INT_RGB);
     * Graphics2D g2d = resized.createGraphics();
     * g2d.setRenderingHint(KEY_INTERPOLATION, VALUE_INTERPOLATION_BILINEAR);
     * g2d.drawImage(original, 0, 0, w, h, null);
     * g2d.dispose();
     * // Compress via ImageWriter at JPEG_QUALITY
     * ImageWriter writer = ImageIO.getImageWritersByFormatName("jpg").next();
     * ImageWriteParam param = writer.getDefaultWriteParam();
     * param.setCompressionMode(MODE_EXPLICIT);
     * param.setCompressionQuality(0.80f);
     * writer.setOutput(ImageIO.createImageOutputStream(baos));
     * writer.write(null, new IIOImage(resized, null, null), param);
     * writer.dispose();
     * return Base64.getEncoder().encodeToString(baos.toByteArray());
     * </pre>
     *
     * @param file      the image file to process
     * @param maxPixels maximum dimension cap
     * @return Base64 JPEG string
     * @throws Exception if the file cannot be read or the writer is unavailable
     */
    private static String processImage(File file, int maxPixels) throws Exception {
        // ── Step 2: read original image ───────────────────────────────────────
        BufferedImage original = ImageIO.read(file);
        if (original == null) {
            throw new IllegalArgumentException(
                    "ImageIO could not decode the file. Ensure it is a valid image: " + file.getName());
        }

        // ── Step 3: compute target dimensions (proportional resize) ───────────
        int w = original.getWidth();
        int h = original.getHeight();

        if (w > maxPixels || h > maxPixels) {
            // Maintain aspect ratio — shrink so the larger axis equals maxPixels
            double ratio = Math.min((double) maxPixels / w, (double) maxPixels / h);
            w = (int) (w * ratio);
            h = (int) (h * ratio);
        }
        // If the image is already within bounds, w/h remain the original values.

        // ── Step 4: draw into a fresh RGB BufferedImage (removes alpha channel) ─
        // TYPE_INT_RGB is required by the JPEG encoder — JPEG has no alpha support.
        BufferedImage resized = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
        Graphics2D g2d = resized.createGraphics();
        // BILINEAR produces smooth down-scaled output, matching the original logic
        g2d.setRenderingHint(
                RenderingHints.KEY_INTERPOLATION,
                RenderingHints.VALUE_INTERPOLATION_BILINEAR
        );
        g2d.drawImage(original, 0, 0, w, h, null);
        g2d.dispose();

        // ── Step 5: compress to JPEG via ImageWriter at controlled quality ─────
        ByteArrayOutputStream baos = new ByteArrayOutputStream();

        // Prefer the Sun/JDK built-in JPEG writer for consistent behaviour
        ImageWriter writer = ImageIO.getImageWritersByFormatName("jpg").next();

        ImageWriteParam param = writer.getDefaultWriteParam();
        param.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
        param.setCompressionQuality(JPEG_QUALITY); // 0.80f — matches seller dashboard

        writer.setOutput(ImageIO.createImageOutputStream(baos));
        writer.write(null, new IIOImage(resized, null, null), param);
        writer.dispose();

        // ── Step 6: Base64 encode and return ──────────────────────────────────
        return Base64.getEncoder().encodeToString(baos.toByteArray());
    }
}
