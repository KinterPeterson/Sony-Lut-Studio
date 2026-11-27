# Sony LUT Studio V0.6

V0.6 is an incremental upgrade from the verified V0.5 baseline. It keeps the dark-blue Liquid Glass visual language, 3D `.cube` LUT parser, tetrahedral interpolation, 0–200% intensity, EXIF orientation correction and full-resolution JPEG export.

## V0.6 highlights

### Photo workflow
- Adds an in-app photo browser based on the user-authorized read folder.
- Folder authorization is performed once through Android's Storage Access Framework (SAF); normal browsing and photo selection then happen inside Sony LUT Studio.
- Supports navigating into subfolders, searching the current folder, sorting by filename, EXIF capture time or modification time, and selecting multiple photos across folders.
- The first selected photo is loaded immediately; the remaining selections appear in a pending queue.
- The browser displays image thumbnails and names. Supported image types depend on Android's image decoders; JPEG, PNG and WebP are the primary supported formats.

### Settings and folders
- Adds a Settings screen.
- Stores persistent SAF tree permissions for a read folder and an output folder.
- The app's own photo browser reads from the configured read folder.
- Export writes directly into the configured output folder without showing a save-location picker.
- If a folder permission is revoked or the folder becomes unavailable, the user can reselect it in Settings.

### Export
- Keeps full-resolution source decoding and EXIF orientation correction.
- Keeps JPEG quality 98 and the existing 0–200% LUT intensity.
- Generates a filename from the original photo filename and selected LUT name.
- Avoids overwriting existing files by adding `_001`, `_002`, etc.
- Writes to a temporary JPEG document first, then renames or copies it to the final name.
- Remains on the current photo after export.
- Stores the ten most recent export records and provides an open-photo action.

### LUT browser and management
- Visual grid browser for the five bundled ARRI-style LUTs and imported custom LUTs.
- Generates LUT-specific preview thumbnails from the currently loaded photo at reduced resolution.
- Search by LUT name.
- System filters: All, Built-in, My LUTs and Favorites.
- Default user tags: Cinema, Cool, Warm and Film; users can add custom tags and assign multiple tags to a LUT.
- Favorite/unfavorite built-in and custom LUTs.
- Rename and delete custom LUTs; built-in LUT files are read-only.
- Deleting a LUT leaves referencing presets in place and marks them as missing when displayed.

### Presets and remembered state
- Save, apply, rename and delete presets.
- V0.6 preset contents: LUT ID, intensity, name, optional note and creation timestamp.
- Preset model is structured so more adjustment parameters can be added in a future version.
- Remembers the last selected LUT, intensity, last LUT-browser category, favorites, categories, presets and folder permissions.
- Does not automatically reopen the previous photo on app startup.

## Data and storage

- App-private `filesDir/luts/`: imported `.cube` files.
- App-private SharedPreferences JSON: LUT metadata, categories, favorites, presets and recent exports.
- SAF persisted tree URI permissions: configured read and output folders.
- Built-in LUT assets remain bundled under `app/src/main/assets/luts/`.

## Important limitations

- The current photo processor still uses Android `ARGB_8888` bitmaps and the V0.5 tetrahedral LUT processor. V0.6 does not add ICC color management, RAW development or a 16-bit/float processing pipeline.
- JPEG export remains high-quality lossy JPEG (quality 98), not mathematically lossless.
- Full-resolution export decodes full-size source and output bitmaps. Very large photos may still exceed available device memory.
- The in-app browser navigates folders selected by the user; Android's SAF controls which directories and files the app can access.
- Custom LUT import still uses Android's document picker. The no-native-picker requirement applies to photo import and export location selection.

## Build configuration

- Android Gradle Plugin 8.7.3
- Gradle 8.9
- Kotlin 2.0.21
- JDK 17
- Compile SDK 35
- Minimum SDK 29
- `versionCode = 7`
- `versionName = "0.6"`
- GitHub Actions artifact: `SonyLUTStudio-V0.6-debug`

Build from the project root:

```bash
gradle --no-daemon assembleDebug
```

GitHub Actions builds on pushes to `main` or `master`, or by manual workflow dispatch.
