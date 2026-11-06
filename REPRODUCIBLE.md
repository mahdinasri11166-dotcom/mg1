# Reproducible Build & Transparent Signing

## Goals
- Anyone can rebuild the same APK bytes (or explain residual diffs).
- Signing identity is documented for users.

## Steps (developers)
1. Use JDK 17 exactly.
2. Pin dependency versions in `app/build.gradle.kts` (already pinned).
3. Build:
   ```bash
   ./gradlew clean assembleRelease
   ```
4. Record:
   - `git rev-parse HEAD`
   - `sha256sum app/build/outputs/apk/release/app-release-unsigned.apk`
5. Sign with your release keystore (do not commit keystore):
   ```bash
   apksigner sign --ks release.jks --out GhostMago-release.apk app-release-unsigned.apk
   apksigner verify --verbose GhostMago-release.apk
   sha256sum GhostMago-release.apk
   ```
6. Publish SHA-256 of the signed APK on a channel users can verify independently.

## Transparency statement
- Source in this ZIP is the build input.
- No hidden native blobs beyond AndroidX / BouncyCastle / ZXing / CameraX Maven artifacts.
- Users should verify hash before install when operational security requires it.

## CI
See `.github/workflows/android.yml` for automated unit tests and debug assemble.
