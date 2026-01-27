---
description: Enforce test-driven development workflow. Scaffold interfaces, generate tests FIRST, then implement minimal code to pass. Ensure 80%+ coverage.
---

# TDD Command

This command invokes the **tdd-guide** agent to enforce test-driven development methodology.

## What This Command Does

1. **Scaffold Interfaces** - Define types/interfaces first
2. **Generate Tests First** - Write failing tests (RED)
3. **Implement Minimal Code** - Write just enough to pass (GREEN)
4. **Refactor** - Improve code while keeping tests green (REFACTOR)
5. **Verify Coverage** - Ensure 80%+ test coverage

## When to Use

Use `/tdd` when:
- Implementing new features
- Adding new functions/components
- Fixing bugs (write test that reproduces bug first)
- Refactoring existing code
- Building critical business logic

## How It Works

The tdd-guide agent will:

1. **Define interfaces** for inputs/outputs
2. **Write tests that will FAIL** (because code doesn't exist yet)
3. **Run tests** and verify they fail for the right reason
4. **Write minimal implementation** to make tests pass
5. **Run tests** and verify they pass
6. **Refactor** code while keeping tests green
7. **Check coverage** and add more tests if below 80%

## TDD Cycle (Baby Steps)

Follow the **"Baby Steps"** principle: keep iterations small and feedback fast.

```
RED → GREEN → REFACTOR → REPEAT

RED:      Write a failing test
GREEN:    Write minimal code to pass
REFACTOR: Improve code, keep tests passing
REPEAT:   Next feature/scenario
```

**CRITICAL RULE**: If a task (e.g., bug fix) requires modifying **more than 3 files**, you MUST break it down into smaller, independent TDD tasks.

## Example Usage

```
User: /tdd I need a function/UseCase to calculate loan interest

Agent (tdd-guide):
# TDD Session: Loan Interest Calculator

## Step 1: Define Interface (SCAFFOLD)

```kotlin
// domain/usecase/CalculateInterestUseCase.kt
interface CalculateInterestUseCase {
    operator fun invoke(principal: Double, rate: Double, months: Int): Result<Double>
}
```

## Step 2: Write Failing Test (RED)

```kotlin
// test/domain/usecase/CalculateInterestUseCaseTest.kt
class CalculateInterestUseCaseTest {

    private lateinit var useCase: CalculateInterestUseCase

    @Before
    fun setup() {
        useCase = CalculateInterestUseCaseImpl() // Class doesn't exist yet!
    }

    @Test
    fun `should calculate simple interest correctly`() {
        val result = useCase(1000.0, 0.05, 12)
        assertEquals(50.0, result.getOrThrow(), 0.01)
    }

    @Test
    fun `should return error for negative principal`() {
        val result = useCase(-1000.0, 0.05, 12)
        assertTrue(result.isFailure)
    }
}
```

## Step 3: Run Tests - Verify FAIL

```bash
./gradlew test

> Task :app:testDebugUnitTest FAILED
Unresolved reference: CalculateInterestUseCaseImpl
```

✅ tests fail (compilation error counts as RED).

## Step 4: Implement Minimal Code (GREEN)

```kotlin
// domain/usecase/CalculateInterestUseCaseImpl.kt
class CalculateInterestUseCaseImpl : CalculateInterestUseCase {
    override fun invoke(principal: Double, rate: Double, months: Int): Result<Double> {
        if (principal < 0) return Result.failure(IllegalArgumentException("Negative principal"))
        
        val interest = principal * rate * (months / 12.0)
        return Result.success(interest)
    }
}
```

## Step 5: Run Tests - Verify PASS

```bash
./gradlew test

> Task :app:testDebugUnitTest PASSED
```

✅ All tests passing!

## Step 6: Refactor (IMPROVE)

```kotlin
// domain/usecase/CalculateInterestUseCaseImpl.kt
// Refactor magic numbers and improve readability
class CalculateInterestUseCaseImpl : CalculateInterestUseCase {
    
    companion object {
        private const val MONTHS_IN_YEAR = 12.0
    }

    override fun invoke(principal: Double, rate: Double, months: Int): Result<Double> {
        require(principal >= 0) { "Principal must be non-negative" }
        require(rate >= 0) { "Rate must be non-negative" }

        return runCatching {
             principal * rate * (months / MONTHS_IN_YEAR)
        }
    }
}
```

## Step 7: Verify Tests Still Pass

```bash
./gradlew test
PASSED
```

## Step 8: Check Coverage

```bash
# Using Kover or JaCoCo
./gradlew koverHtmlReport
# View app/build/reports/kover/html/index.html
```
```

## TDD Best Practices

**DO:**
- ✅ Write the test FIRST (RED state)
- ✅ Use JUnit 4/5 and Mockk for Unit Tests
- ✅ Use `runTest` and `TestDispatcher` for Coroutines
- ✅ Aim for 80%+ coverage

**DON'T:**
- ❌ Write implementation before tests
- ❌ Ignore failing tests
- ❌ Mock everything (Use Fakes for Repositories if possible)

## Test Types to Include

**Unit Tests** (Local JVM):
- Domain Logic (UseCases)
- ViewModels (State compilation)
- Utility Classes
- JNI Interface (Testing pure JNI mapping)

**Instrumented Tests** (Android Device):
- Room Database Migrations
- Context-dependent Logic

**UI Tests** (Compose):
- Critical User Flows (`ComposeTestRule`)
- Navigation Logic


## Coverage Requirements

- **80% minimum** for all code
- **100% required** for:
  - Financial calculations
  - Authentication logic
  - Security-critical code
  - Core business logic

## Important Notes

**MANDATORY**: Tests must be written BEFORE implementation. The TDD cycle is:

1. **RED** - Write failing test
2. **GREEN** - Implement to pass
3. **REFACTOR** - Improve code

Never skip the RED phase. Never write code before tests.

## Integration with Other Commands

- Use `/plan` first to understand what to build
- Use `/tdd` to implement with tests
- Use `/build-and-fix` if build errors occur
- Use `/code-review` to review implementation
- Use `/test-coverage` to verify coverage

## Related Agents

This command invokes the `tdd-guide` agent located at:
`~/.claude/agents/tdd-guide.md`

And can reference the `tdd-workflow` skill at:
`~/.claude/skills/tdd-workflow/`
