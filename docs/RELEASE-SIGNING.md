# راهنمای ساخت و امضای نسخه Release — مشکات (Mr Elf)

## مشخصات Keystore

| مورد | مقدار |
|------|--------|
| فایل | `mishkat-release.jks` |
| Alias | `mrelf` |
| الگوریتم کلید | RSA 4096 |
| امضا | SHA256withRSA |
| اعتبار | 365 روز |
| CN / سازنده | Mr Elf |

## ۱) ساخت Keystore با keytool

```bash
keytool -genkeypair \
  -v \
  -keystore mishkat-release.jks \
  -alias mrelf \
  -keyalg RSA \
  -keysize 4096 \
  -sigalg SHA256withRSA \
  -validity 365 \
  -storetype JKS \
  -dname "CN=Mr Elf, OU=Mishkat, O=Mishkat, L=Tehran, ST=Tehran, C=IR"
```

رمزها:
- **Keystore password** → Secret: `KEYSTORE_PASSWORD`
- **Key password** → Secret: `KEY_PASSWORD` (می‌تواند با قبلی یکی باشد)

فایل `mishkat-release.jks` را commit نکنید.

## ۲) Base64 برای GitHub

```bash
base64 -w 0 mishkat-release.jks > mishkat-release.b64
```

## ۳) Secrets در GitHub

| نام | مقدار |
|-----|--------|
| `KEYSTORE_BASE64` | محتوای base64 |
| `KEYSTORE_PASSWORD` | رمز keystore |
| `KEY_PASSWORD` | رمز کلید (اختیاری) |
| `KEY_ALIAS` | `mrelf` (اختیاری) |

## ۴) بیلد

Actions → **Build Release APK** → Run workflow  
Artifact: **Mishkat-release-signed**

محلی:

```bash
export KEYSTORE_PATH=/path/to/mishkat-release.jks
export KEYSTORE_PASSWORD='...'
export KEY_PASSWORD='...'
export KEY_ALIAS=mrelf
./gradlew assembleRelease
```
