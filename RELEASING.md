# Team release (signed APK + SHA-256)

## Debug (GitHub Actions)
Actions builds `assembleDebug` and uploads artifact.

## Signed release (on your PC)
```bash
# create keystore once
keytool -genkey -v -keystore ghostmago.jks -keyalg RSA -keysize 2048 -validity 10000 -alias ghostmago

# in app/build.gradle.kts configure signingConfigs (do not commit jks or passwords)

./gradlew assembleRelease
sha256sum app/build/outputs/apk/release/app-release.apk
```

Publish the APK and the SHA-256 hash to your team on a trusted channel.
All members must use the same versionName/versionCode.
