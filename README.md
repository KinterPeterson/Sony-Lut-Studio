# Sony LUT Studio V0.4

V0.4 incrementally extends the V0.3.1 project while retaining its Liquid Glass visual language and existing import / LUT processing workflow.

## Added in V0.4
- LUT intensity control from 0% to 100%, with interpolation between the original pixel and LUT-transformed pixel.
- Press-and-hold comparison: hold the preview to show the original image; release to return to the processed preview.
- Export the processed preview as a JPEG through Android's document picker.
- Updated version metadata to `versionCode 5`, `versionName 0.4`.
- Updated GitHub Actions artifact name to `SonyLUTStudio-V0.4-debug`.

## Existing functionality retained
- `.cube` 3D LUT parsing through Android Storage Access Framework.
- JPEG/photo import and robust URI-to-cache decoding.
- 3D LUT tetrahedral interpolation.
- Dark blue Liquid Glass interface, translucent panels, rounded controls and pale-blue accents.

## Important limitations
- Input photo is still decoded to a preview whose longest edge is at most approximately 2048 px.
- Export therefore saves the processed preview, not the original full-resolution image.
- Full-resolution tiled processing and export remain future work.
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
