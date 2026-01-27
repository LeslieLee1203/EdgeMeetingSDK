# Build and Fix

Incrementally fix Kotlin/Gradle build errors:

1. Run build: `./gradlew assembleDebug` or `./gradlew build`

2. Parse error output:
   - Group by file
   - Match Gradle error patterns (e.g., `Unresolved reference`, `Type mismatch`)

3. For each error:
   - Show error context (5 lines before/after)
   - Explain the issue (check for JNI/NDK issues if related to `.so` or `external fun`)
   - Propose fix
   - Apply fix
   - Re-run build
   - Verify error resolved

4. Stop if:
   - Fix introduces new errors
   - Same error persists after 3 attempts
   - User requests pause

5. Show summary:
   - Errors fixed
   - Errors remaining
   - New errors introduced

Fix one error at a time for safety!
