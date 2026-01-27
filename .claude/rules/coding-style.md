# Coding Style

## Immutability (CRITICAL)

Prefer `val` over `var` and Data Classes for immutability:

```kotlin
// WRONG: Mutation
fun updateUser(user: User, name: String): User {
    user.name = name  // MUTATION!
    return user
}

// CORRECT: Immutability (Data Class copy)
fun updateUser(user: User, name: String): User {
    return user.copy(name = name)
}
```

## Setup Jetpack Compose

Declarative UI patterns:

```kotlin
// WRONG: Side effects in Composable
@Composable
fun UserProfile(user: User) {
    user.lastViewed = System.currentTimeMillis() // BAD: Composition side-effect!
    Text(user.name)
}

// CORRECT: Unidirectional Data Flow / SideEffect API
@Composable
fun UserProfile(user: User, onViewed: () -> Unit) {
    LaunchedEffect(user.id) {
        onViewed()
    }
    Text(user.name)
}
```

## JNI / C++ Interop

Safety boundaries:

```kotlin
// WRONG: Direct unsafe call
external fun nativeCrashIfNull(str: String)

// CORRECT: Null safety and Exception handling
@Keep
external fun nativeSafeCall(str: String?): Int

// In C++ (JNI): Check for exceptions after calling Java methods!
// if (env->ExceptionCheck()) { ... }
```

## File Organization

MANY SMALL FILES > FEW LARGE FILES:
- High cohesion, low coupling
- 200-400 lines typical, 800 max
- Extract Composables/Utility functions
- Organize by feature/domain (e.g., `feature/login/ui`, `feature/login/domain`)

## Error Handling

Use Sealed Classes (Result<T>) or Kotlin Exceptions:

```kotlin
fun riskyOperation(): Result<Data> {
    return try {
        val result = api.call()
        Result.success(result)
    } catch (e: Exception) {
        Timber.e(e, "Operation failed")
        Result.failure(UIFriendlyException("Connection error"))
    }
}
```

## Input Validation

ALWAYS validate user input (ViewModel level):

```kotlin
data class UserInput(val email: String, val age: Int)

fun validate(input: UserInput): Boolean {
    require(Patterns.EMAIL_ADDRESS.matcher(input.email).matches()) { "Invalid Email" }
    require(input.age in 0..150) { "Invalid Age" }
    return true
}
```

## Code Quality Checklist

Before marking work complete:
- [ ] Code is readable and well-named (Kotlin conventions)
- [ ] Composables are idempotent and side-effect free
- [ ] JNI methods handle null pointers and exceptions safely
- [ ] Functions are small (<50 lines)
- [ ] Files are focused (<800 lines)
- [ ] No deep nesting (>4 levels)
- [ ] Proper error handling (Result/Sealed classes)
- [ ] No `println` statements (Use `Timber`)
- [ ] No hardcoded values (Use `res/values`)
- [ ] No mutation (prefer `val`, `copy()`, `StateFlow`)
