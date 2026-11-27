# Sony LUT Studio V0.5

V0.5 incrementally extends V0.4. The existing dark-blue Liquid Glass interface and core workflow are retained.

## V0.5 changes
- Includes five Arri-style 3D LUTs: IceBlue, Jamaica, Neutral, Tungsten and Utopia.
- Keeps importing custom `.cube` LUT files.
- LUT intensity range is 0–200%, with 100% as the default. The formula is `original + (LUT(original) - original) * intensity`; values are clamped to the valid RGB range.
- Keeps press-and-hold original-image comparison.
- Corrects all EXIF orientation cases (rotation and mirroring) before preview and export.
- Exports a newly decoded, full-resolution source photo as JPEG at quality 98, rather than exporting the 2048 px preview.
- LUT processing reads and writes one scanline at a time to avoid allocating a second full-image pixel array.

## Important memory note
The preview is decoded at a maximum dimension of approximately 2048 px. Full-resolution export decodes the original image into an Android `Bitmap` before processing it scanline by scanline. This reduces additional pixel-array memory, but the full source and output bitmaps still occupy substantial memory. Very large photographs may exceed the memory available to the device and cause an out-of-memory failure. Fully streaming decode/encode in small image tiles is a possible future improvement.

The exported JPEG contains the corrected pixel orientation. The original EXIF metadata is not copied to the new JPEG.

## Existing functionality retained
- `.cube` 3D LUT parsing through Android Storage Access Framework.
- JPEG/photo import.
- Tetrahedral interpolation.
- Dark-blue Liquid Glass interface, translucent panels, rounded controls and pale-blue accents.
- Press-and-hold preview to compare the original image.

## Build configuration
- Android Gradle Plugin 8.7.3
- Gradle 8.9
- Kotlin 2.0.21
- JDK 17
- Compile SDK 35
- Minimum SDK 29
- `versionCode = 6`
- `versionName = "0.5"`

Build from the project root:

```bash
gradle --no-daemon assembleDebug
```

GitHub Actions builds on pushes to `main` or `master`, or by manual workflow dispatch. The artifact is named `SonyLUTStudio-V0.5-debug`.
