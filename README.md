# مشکات (مشکات) — Mr Elf

Secure offline mesh messenger + steganography (Android 10+).

## Download APK from GitHub Actions

1. Push this repo to GitHub (`main` branch).
2. Open **Actions** tab → workflow **Build APK** → latest run.
3. Download artifact **مشکات-debug-apk**.
4. Or create a tag (`v1.0.0`) to attach APK to a **Release**.

Manual trigger: Actions → Build APK → **Run workflow**.

## Build on your PC (Android Studio)

1. Open this folder in Android Studio  
2. JDK **17** → Gradle Sync  
3. **Build → Build APK(s)**  
4. `app/build/outputs/apk/debug/app-debug.apk`

```bash
chmod +x gradlew
./gradlew assembleDebug
```

## First login

| Step | Value |
|------|--------|
| Calculator PIN | `0000` then `=` |
| App password | `mr elf` |
| Safe word (honey) | `آزادی` |

## Use with multiple phones

- Same APK version on all devices  
- Same Wi‑Fi (or Wi‑Fi Direct)  
- Commander: Mesh → group password → Invite QR  
- Others: Scan QR or enter password → Connect  
- Tabs: **Group** / **Private**

## Notes

- No internet required for mesh after install  
- Emulator is weak for mesh; use real phones  
- UDP port **5599**

## License

Private / team use — Creator: **Mr Elf**

## Security modules (v1.2)

- **LicenseManager**: offline HMAC license bound to device (Settings → لایسنس)
- **RASP**: root/debugger checks in `مشکاتApp` (respects devMode / strictAttestation)
- **NDK**: `app/src/main/cpp` + `NativeBridge` pepper mixed into Argon2 KDF
- **ProGuard/R8**: release minify + log strip + JNI keep

Build release:
```bash
./gradlew assembleRelease
```
