# NT Woods Offline Media Player (Android, Kotlin)

This app plays normal, unencrypted offline media bundled inside the APK.

## Media folders

- Videos: `app/src/main/assets/videos/*.mp4`
- Documents: `app/src/main/assets/docs/*.pdf`

There is no encryption key, encryption script, runtime decryption, custom decrypted DataSource, or `.enc` media format.

## Large video workflow

1. Clone/open the project in Android Studio.
2. Copy the final MP4 directly into `app/src/main/assets/videos/`.
3. Copy PDFs directly into `app/src/main/assets/docs/`.
4. Build and install the APK on the target device.
5. The app automatically lists `.mp4` and `.pdf` files from those asset folders.

The Android build keeps MP4/PDF assets uncompressed. Media3/ExoPlayer reads the MP4 directly from the packaged asset using an `asset:///...` URI, so the video is not decrypted or copied to a temporary file before playback.

`PlayerActivity` also keeps decoder fallback enabled for devices whose preferred hardware AVC decoder fails to initialize.

## 1.25 GB MP4 note

GitHub's normal repository file-size limit does not allow a ~1.25 GB MP4 to be committed as a regular Git object. The project therefore ignores MP4/PDF payloads inside the final asset folders. Keep the large media file locally in `app/src/main/assets/videos/` before building the APK (or manage it separately with Git LFS if you intentionally want large-file versioning).

The large MP4 is still bundled into the generated APK even though Git ignores the local source file.

## Recommended MP4 compatibility

For broad Android hardware compatibility, use a standard MP4 container with H.264/AVC video and AAC audio. Avoid unusual codecs/profiles if the target devices are older or vendor decoder support is inconsistent.
