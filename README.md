# Sony LUT Studio V0.3

V0.3 builds on the V0.2 Liquid Glass interface and adds:
- Import of `.cube` 3D LUT files through Android Storage Access Framework.
- Import of JPEG images through Android Storage Access Framework.
- 3D LUT application using tetrahedral interpolation.
- Original and processed preview display.

## Important scope
- Preview decoding is downsampled so the longest side is at most approximately 2048 px.
- This version does not export the full-resolution JPEG.
- Full-resolution tiled processing and JPEG export are planned for a later version.
- 1D LUT files are not supported.
- A LUT must match the input image's color encoding. Do not assume an S-Log3 LUT is suitable for every Sony JPEG.

## Build
- Android Gradle Plugin 8.7.3
- Gradle 8.9
- Kotlin 2.0.21
- JDK 17
- Compile SDK 35
- Minimum SDK 29
