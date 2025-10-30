
# Offline Encrypted Video Player (Android, Kotlin)

**What it does:** Plays offline videos that are bundled inside the app as encrypted `.enc` files (AES‑256‑GCM).  
**Flow:** Encrypt MP4s on your PC with the provided Python script → place `.enc` files into `app/src/main/assets/videos` → the app decrypts on-the-fly and streams to ExoPlayer.

## Quick start
1. Open this project in Android Studio (Giraffe+). Let it sync Gradle.
2. In `app/build.gradle.kts`, a demo key is generated and exposed as `BuildConfig.AES_KEY_B64`. For production, don't hardcode keys.
3. Put your mp4 files in a folder, then run (Python 3.10+, `pip install pycryptodome`):

```bash
python encrypt_videos.py --in ./my_mp4s --out ./app/src/main/assets/videos --key-out ./app/src/main/assets/videos/key.txt
```

The script will ask you to paste the **AES_KEY_B64** from `app/build.gradle.kts` (or you can set `AES_KEY_B64` environment variable).  
It will create `.enc` files and update `videos.json` with titles and asset paths.

4. Build & run on a device. Select a video and press **Play**.

## Notes
- `.enc` file format: `[12-byte IV][ciphertext...][16-byte GCM tag]`.
- Decryption happens only in memory, via a custom `DecryptedAssetDataSource`.
- This is **not DRM**. For strong protection, use Widevine DRM / licensing, or store keys in TEE/Keystore and fetch per-license over network.

## Project structure
```
OfflineEncryptedVideoPlayer/
  app/
    src/main/
      AndroidManifest.xml
      java/com/ntwoods/offlineplayer/
        MainActivity.kt
        PlayerActivity.kt
        VideoAdapter.kt
        crypto/DecryptedAssetDataSource.kt
      res/layout/
        activity_main.xml
        activity_player.xml
        item_video.xml
      res/values/
        strings.xml colors.xml themes.xml
      assets/videos/
        videos.json
        sample.enc (placeholder; replace with your encrypted files)
    build.gradle.kts
    proguard-rules.pro
  build.gradle.kts
  settings.gradle.kts
  encrypt_videos.py
  gradlew, gradlew.bat, gradle/wrapper/gradle-wrapper.properties
```

## Security caveat
- Key baked into app is recoverable by a motivated attacker. This demo is for offline training/courseware distribution with *basic* obfuscation. For commercial-grade security, integrate license checks, keystore, and device binding.
