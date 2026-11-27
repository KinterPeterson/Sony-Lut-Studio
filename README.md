# Sony LUT Studio V0.7

V0.7 is the high-fidelity color-processing upgrade built on the user-tested V0.6 workflow. It keeps the dark-blue Liquid Glass interface, photo browser, folder permissions, queue, LUT manager, favorites, categories, presets, recent exports, EXIF orientation correction, and 0–200% LUT intensity.

## V0.7 highlights

### Float32 color processing
- LUT math and intensity blending use 32-bit floating-point RGB values.
- The processing path works one scanline at a time; it does not allocate a full-frame Float32 RGB buffer.
- The 8-bit ARGB bitmap is quantized only when creating the regular 8-bit preview/JPEG output. The 16-bit PNG path writes the floating-point LUT result directly to 16-bit samples without creating an intermediate 8-bit result bitmap.
- Tetrahedral interpolation and the existing 0–200% intensity behavior are retained.

### 16-bit PNG export
- Adds a streaming PNG encoder that writes standard RGB PNG with 16 bits per channel and lossless DEFLATE compression.
- The encoder writes bounded IDAT chunks and processes one image row at a time to limit temporary memory use.
- PNG output includes an sRGB rendering-intent chunk.
- 16-bit PNG is the default export format. JPEG remains available.

### JPEG export
- JPEG quality can be selected from 95 to 100; default is 98.
- JPEG remains lossy at every quality setting.

### Color-space handling
- Bitmap decoding requests sRGB as the preferred working color space. Android's decoder performs supported source-profile conversion where available.
- The 16-bit PNG encoder tags its RGB samples as sRGB.
- This version does not preserve the original embedded ICC profile byte-for-byte, does not expose Display P3 / Adobe RGB working-space selection, and does not yet implement a full ICC profile management UI.

### App identity
- Adds a new camera-aperture / color-cube logo.
- Integrates the logo into Android launcher icons and the in-app header.

## Important precision note

This version currently requests Android `ARGB_8888` when decoding the source, so the working input is 8-bit per channel even if a higher-bit-depth source file is selected. A 16-bit PNG output cannot restore color or detail already discarded by the source file. It does preserve the higher-precision floating-point LUT result in a 16-bit-per-channel output container, which can reduce additional quantization loss at the processing/export stage and is useful for subsequent editing. True end-to-end high-bit-depth input, RAW development, and wide-gamut workflows are future work.

The preview remains an Android ARGB_8888 bitmap for compatibility and memory control. The high-precision export path is applied to the full-resolution decoded source.

## Preserved V0.6 functionality
- App-owned photo browser with subfolder navigation, search, sorting, and multi-select.
- First selected photo loads immediately; remaining photos enter the pending queue.
- SAF read/output folder permissions and direct export into the configured output folder.
- Built-in and custom LUT browser, previews, search, favorites, categories, rename/delete for custom LUTs.
- Preset save/apply/rename/delete and remembered settings.
- Full-resolution export, EXIF orientation correction, filename collision avoidance, recent export history, and stay-on-current-photo behavior.
- Dark-blue Liquid Glass UI.

## Build configuration
- Android Gradle Plugin 8.7.3
- Gradle 8.9
- Kotlin 2.0.21
- JDK 17
- Compile SDK 35
- Minimum SDK 29
- `versionCode = 8`
- `versionName = "0.7"`
- GitHub Actions artifact: `SonyLUTStudio-V0.7-debug`

Build from the project root:

```bash
gradle --no-daemon assembleDebug
```

GitHub Actions builds on pushes to `main` or `master`, or by manual workflow dispatch.

## V0.7 test checklist
1. Install and launch; verify the new launcher icon and in-app logo.
2. Confirm existing read/output folder permissions remain available after upgrading from V0.6.
3. Export a photo as 16-bit PNG; inspect the file with an editor that reports PNG bit depth (it should report 16 bits per channel).
4. Export the same photo as JPEG at quality 95, 98, and 100.
5. Compare the 16-bit PNG and JPEG outputs for dimensions, orientation, color appearance, and file size.
6. Test dark gradients, smooth skies, skin tones, saturated colors, and shadow detail.
7. Test a large full-resolution photo for memory stability and export completion.
8. Recheck LUT intensity at 0%, 100%, 150%, and 200%, plus hold-to-compare.
9. Recheck photo browsing, queue, LUT management, presets, and export history.

The source ZIP is not a substitute for a successful Android build. Build status must be confirmed by Gradle/GitHub Actions and real-device testing.
