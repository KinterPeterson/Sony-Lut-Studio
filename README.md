# Sony LUT Studio V0.2

V0.2 adds Android Storage Access Framework import and validation for `.cube` 3D LUT files.

- Reads TITLE, LUT_3D_SIZE, DOMAIN_MIN and DOMAIN_MAX
- Supports 3D LUT sizes 2–64
- Validates RGB triplet rows and exact N³ data count
- Minimum Android API 29; JDK 17; AGP 8.7.3; Gradle 8.9

Build with `gradle assembleDebug`.

This version imports and validates LUT files only. JPEG import, LUT application and export are later milestones.
