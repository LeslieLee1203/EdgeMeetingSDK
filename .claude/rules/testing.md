# Testing Requirements

## Minimum Test Coverage: 80%

Test Types (ALL required):
1. **Local Unit Tests** (`test` source set) - Domain logic, ViewModels, Repositories, Utils. (JUnit, Mockk/Mockito, Robolectric).
2. **Instrumented Tests** (`androidTest` source set) - Components requiring Android context (DAO, SharedPreferences).
3. **UI/E2E Tests** - Critical user flows, Screen navigation. (Jetpack Compose UI Tests, SemanticsMatcher).
4. **Native Tests** (`cpp` source set) - C/C++ logic via JNI. (Google Test / GTest).

## Test-Driven Development

MANDATORY workflow:
1. Write test first (RED)
2. Run test - it should FAIL
3. Write minimal implementation (GREEN)
4. Run test - it should PASS
5. Refactor (IMPROVE)
6. Verify coverage (80%+)

## Troubleshooting Test Failures

1. Use **tdd-guide** agent
2. Check test isolation (reset Database/SharedPreferences between tests)
3. Verify Coroutine Dispatchers (Use `StandardTestDispatcher` / `TestScope`)
4. Verify JNI Bindings (Ensure `.so` libraries are loaded, use `UnsatisfiedLinkError` checks)
5. Fix implementation, not tests (unless tests are wrong)

## Agent Support

- **tdd-guide** - Use PROACTIVELY for new features, enforces write-tests-first
- **e2e-runner** - Android UI Automation specialist (Compose/Semantics)
