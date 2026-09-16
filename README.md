# NT Woods Offline Media Player (Android, Kotlin)

This app plays normal offline media bundled inside the APK. Runtime encryption/decryption is not part of the application.

## Media folders

- Videos: `app/src/main/assets/videos/*.mp4`
- Documents: `app/src/main/assets/docs/*.pdf`

There is no encryption key, encryption script, runtime decryptor, decrypted video DataSource, or `.enc` media format.

## Large video workflow

1. Clone/open the project in Android Studio.
2. Copy the final MP4 directly into `app/src/main/assets/videos/`.
3. Copy PDFs directly into `app/src/main/assets/docs/`.
4. Build and install the APK on the target device.
5. The app automatically lists `.mp4` and `.pdf` files from those asset folders.

## Video playback architecture

Large MP4/PDF assets are packaged uncompressed via `androidResources.noCompress`.

Video playback uses Media3/ExoPlayer with `SeekableAssetDataSource`. The data source opens the bundled MP4 through `AssetManager.openFd()` and seeks directly using a file descriptor/FileChannel. The video is therefore not loaded into RAM and is not copied to a temporary file before playback.

`PlayerActivity` also enables:

- MediaCodec asynchronous queueing to reduce dropped-frame pressure on high-frame-rate content.
- Decoder fallback for devices whose preferred hardware AVC decoder cannot initialize correctly.
- Playback-position restoration across activity lifecycle changes.

The project currently uses Media3 1.9.4, which stays aligned with the existing Kotlin 2.0.x project toolchain while providing a substantially newer playback stack than the previous Media3 1.4.1 dependency.

## PDF playback

Android `PdfRenderer` requires a seekable `ParcelFileDescriptor`, so a selected PDF asset is streamed into a temporary cache file while it is open. This is only a file-access requirement for `PdfRenderer`; no encryption/decryption is performed.

## 1.25 GB MP4 note

GitHub's normal repository file-size limit does not allow a ~1.25 GB MP4 to be committed as a regular Git object. The project therefore ignores final MP4/PDF payloads in the asset folders. Keep the large media file locally in `app/src/main/assets/videos/` before building the APK, or manage it separately with Git LFS if desired.

The local MP4 is still bundled into the generated APK even though Git ignores the source media payload.

## Recommended MP4 compatibility

For broad Android hardware compatibility, prefer an MP4 container with H.264/AVC video and AAC audio. The actual smoothness ceiling still depends on the target device's hardware decoder, video resolution, frame rate, bitrate, profile/level and whether the source is constant- or variable-frame-rate.
