# Security Guidelines

## Mandatory Security Checks

Before ANY commit:
- [ ] No hardcoded secrets (API Keys, Keystore passwords, Tokens)
- [ ] Permissions follow "Principle of Least Privilege"
- [ ] `minSdkVersion` and `targetSdkVersion` are up to date
- [ ] Exported Activities/Services/Receivers are protected or intended
- [ ] Network traffic enforces HTTPS (Cleartext traffic disabled)
- [ ] Sensitive data stored using EncryptedSharedPreferences / EncryptedFile
- [ ] ProGuard/R8 obfuscation enabled for release builds
- [ ] Logs do not leak sensitive data (PII)
- [ ] Dependencies scanned for known vulnerabilities

## Secret Management

Secrets should be managed via `local.properties` (not committed) and injected via `BuildConfig`.

```kotlin
// NEVER: Hardcoded secrets
val apiKey = "AIzaSy..."

// ALWAYS: BuildConfig via local.properties
// In build.gradle.kts:
// buildConfigField("String", "API_KEY", "\"${project.findProperty("API_KEY")}\"")

val apiKey = BuildConfig.API_KEY
```

## Android Specifics

- **Intents**: Use `FLAG_IMMUTABLE` for PendingIntents. Validate implicit Intent data.
- **WebView**: Disable JavaScript (`setJavaScriptEnabled(false)`) if not needed. Allow only specific domains.
- **Keystore**: Use Android Keystore System for cryptographic key storage.
- **Tapjacking**: Prevent overlay attacks on sensitive screens (`filterTouchesWhenObscured`).

## Security Response Protocol

If security issue found:
1. STOP immediately
2. Fix CRITICAL issues before continuing
3. Rotate any exposed secrets or signing keys
4. Review entire codebase for similar specific vulnerability
