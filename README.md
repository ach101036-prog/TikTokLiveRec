# LiveVault — TikTok Live Stream Recorder for Android

LiveVault is a Kotlin & Jetpack Compose Android application that checks whether a TikTok creator is currently live, plays a real-time stream preview with a live recording timer, captures the live stream locally to `.mp4` for offline playback, and alerts you with a notification as soon as the file finishes saving.

## Features

- **Live Status Verification**: Enter any `@username` (or TikTok live URL) to verify whether the creator is currently streaming before starting a capture.
- **Live Preview + Time Counter**: Watch the live HLS/FLV stream in Jetpack Media3 (`ExoPlayer`) alongside a live `00:00:00` timer, byte counter, segment tracker, and preview mute toggle.
- **Local MP4 Capture**: Captures HLS (`.m3u8`) and HTTP-FLV (`.flv`) streams directly into standard `.mp4` files saved in `Movies/LiveVault` via `MediaStore` (no broad storage permissions required).
- **Save Alert Notifications**: Runs capture inside an Android Foreground Service and fires a high-priority notification when the MP4 finishes saving—tap to open straight into the offline player.
- **Offline Vault & Player**: Browse, search, share, delete, and watch all saved MP4 recordings offline.

## Publishing APKs via GitHub

This project includes a ready-to-use GitHub Actions workflow at `.github/workflows/android-apk.yml`.

### 1. Push to GitHub

```bash
git init
git add .
git commit -m "Initial LiveVault Android app"
git branch -M main
git remote add origin https://github.com/<your-username>/<your-repo>.git
git push -u origin main
```

Every push or pull request to `main` runs unit tests and builds `LiveVault-debug.apk`, available for download under the **Actions -> LiveVault-APKs** artifact.

### 2. Publish a GitHub Release APK

Create and push a version tag (`v1.0.0`) to automatically publish a GitHub Release with the APK attached:

```bash
git tag v1.0.0
git push origin v1.0.0
```

### 3. Optional: Signed Release APKs

To also build and attach `LiveVault-release.apk`, add these Repository Secrets in **GitHub -> Settings -> Secrets and variables -> Actions**:

- `KEYSTORE_BASE64`: Base64-encoded `.jks` upload keystore (with key alias `upload`)
- `STORE_PASSWORD`: Keystore password
- `KEY_PASSWORD`: Key password
