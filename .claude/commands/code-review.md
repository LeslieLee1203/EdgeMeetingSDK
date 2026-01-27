# Code Review

Comprehensive security and quality review of uncommitted changes (Android/Kotlin):

1. Get changed files: git diff --name-only HEAD

2. For each changed file, check for:

**Security Issues (CRITICAL):**
- Hardcoded sensitive data (API keys in strings)
- Missing `exported="false"` in Manifest
- Insecure WebView configs (`javaScriptEnabled=true`)
- Unprotected Intents (Missing `FLAG_IMMUTABLE`)
- SQL/Room injection risks
- Unsafe JNI calls (Missing null checks)

**Code Quality (HIGH):**
- Functions > 50 lines
- Composable functions with side-effects
- Files > 800 lines
- Nesting depth > 4 levels
- Missing error handling (Swallowed exceptions)
- `println` used instead of `Timber`
- TODO/FIXME comments
- Main thread blocking operations

**Best Practices (MEDIUM):**
- Missing `@Preview` for Composables
- Hardcoded strings (Should use `strings.xml`)
- Hardcoded dimensions (Should use `dimens.xml`)
- Missing tests for new logic
- Accessibility issues (`contentDescription` missing)

3. Generate report with:
   - Severity: CRITICAL, HIGH, MEDIUM, LOW
   - File location and line numbers
   - Issue description
   - Suggested fix

4. Block commit if CRITICAL or HIGH issues found

Never approve code with security vulnerabilities!
