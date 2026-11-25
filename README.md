# Sony LUT Studio V0.5

V0.5 incrementally extends the V0.4 project while retaining its Liquid Glass visual language and existing import / LUT processing workflow.

## Added in V0.5
- **Full-resolution lossless export**: the selected photo is decoded at its original resolution (kept under a memory pixel budget to avoid OOM) and processed at full size, then exported as a lossless PNG instead of a downscaled JPEG preview.
- **Built-in LUTs**: five Arri-style `.cube` LUTs are shipped inside `app/src/main/assets/luts/` and can be picked directly in the app; external `.cube` import is still supported.
- **LUT intensity up to 200%**: the strength slider now ranges 0%–200%. Values above 100% extrapolate away from the original pixel toward (and beyond) the LUT result; final channels are clamped to the valid 0–255 range.
- Updated version metadata to `versionCode 6`, `versionName 0.5`.
- Updated GitHub Actions artifact name to `SonyLUTStudio-V0.5-debug`.

## Existing functionality retained
- `.cube` 3D LUT parsing through Android Storage Access Framework and app assets.
- JPEG/photo import and robust URI-to-cache decoding.
- 3D LUT tetrahedral interpolation.
- Press-and-hold comparison: hold the preview to show the original image; release to return to the processed preview.
- Dark blue Liquid Glass interface, translucent panels, rounded controls and pale-blue accents.

## Notes and limitations
- The on-screen preview still uses a downscaled copy (longest edge ≤ 2048 px) for smooth interaction; the exported file always uses the retained full-resolution original.
- Full-resolution decoding is capped by a pixel budget (24 MP) to prevent out-of-memory failures on very large photos; images above the budget are halved by power-of-two sampling.
- 1D LUT files are not supported.
- A LUT must match the input image's color encoding.

## Build
- Android Gradle Plugin 8.7.3
- Gradle 8.9
- Kotlin 2.0.21
- JDK 17
- Compile SDK 35
- Minimum SDK 29

Run `gradle --no-daemon assembleDebug` from the project root.
