# Android App 通用開發指南

> 從實戰專案萃取的架構模式與最佳實踐 | 適用於所有 Android 專案

---

## 目錄

1. [多模組架構](#一多模組架構)
2. [Kotlin 編碼風格](#二kotlin-編碼風格)
3. [Jetpack Compose 最佳實踐](#三jetpack-compose-最佳實踐)
4. [狀態管理](#四狀態管理)
5. [非同步與 Coroutines](#五非同步與-coroutines)
6. [設計模式](#六設計模式)
7. [JNI / C++ 互通（進階）](#七jni--c-互通進階)
8. [測試策略](#八測試策略)
9. [Build 設定參考](#九build-設定參考)
10. [程式碼品質檢查清單](#十程式碼品質檢查清單)

---

## 一、多模組架構

### 1.1 為什麼要多模組？

- **編譯速度**：只重新編譯被修改的模組
- **關注分離**：強制模組邊界，防止耦合蔓延
- **可測試性**：`core` 模組不依賴 Android，可在本地 JVM 快速測試
- **可重用**：`core` 可被多個 app 共用

### 1.2 三層架構

```
┌─────────────────────────────────────────────┐
│  app (UI 整合層)                             │
│  • Jetpack Compose UI                        │
│  • DI 組裝                                   │
│  • 不含業務邏輯                               │
├─────────────────────────────────────────────┤
│  feature-*/data (平台實作層)                  │
│  • 實作 core 定義的介面                       │
│  • 存取 Android API、網路、資料庫             │
│  • JNI 橋接（如有需要）                       │
├─────────────────────────────────────────────┤
│  core (純 Kotlin 合約層)                      │
│  • 定義介面 (interface)                       │
│  • 定義資料模型 (data class)                  │
│  • 定義狀態 (sealed class/interface)          │
│  • 零 Android 依賴                            │
└─────────────────────────────────────────────┘
```

**依賴方向**：`app` → `data`/`feature` → `core`。`core` 不依賴任何人。

### 1.3 模組職責範例

| 模組 | 類型 | 職責 | Android 依賴 |
|------|------|------|-------------|
| `core` | Kotlin Library / Android Library (minimal) | 介面、資料模型、狀態定義 | 無 |
| `data` | Android Library | Repository 實作、網路/DB/JNI | 有 |
| `feature-chat` | Android Library | 特定功能實作 | 有 |
| `app` | Application | DI 組裝、UI、導航 | 有 |

### 1.4 core 模組範例

```kotlin
// core/src/main/kotlin/com/example/core/UserSession.kt

interface UserSession {
    val state: StateFlow<SessionState>
    val messages: Flow<List<Message>>

    suspend fun connect()
    suspend fun disconnect()
    suspend fun send(message: String)
    fun release()
}

// 狀態定義
sealed interface SessionState {
    data object Idle : SessionState
    data object Connecting : SessionState
    data object Connected : SessionState
    data class Error(val code: Int, val message: String) : SessionState
}

// 資料模型
data class Message(
    val id: String,
    val text: String,
    val senderId: String,
    val timestamp: Long,
    val isFinal: Boolean = true
)
```

**重點**：`core` 只用 Kotlin 標準庫 + Coroutines，不引入 Android 框架。

### 1.5 data 模組範例

```kotlin
// data/src/main/kotlin/com/example/data/RealUserSession.kt

class RealUserSession(
    private val networkClient: NetworkClient,
    private val dispatcher: CoroutineDispatcher = Dispatchers.IO
) : UserSession {

    private val _state = MutableStateFlow<SessionState>(SessionState.Idle)
    override val state: StateFlow<SessionState> = _state.asStateFlow()

    override suspend fun connect() {
        _state.value = SessionState.Connecting
        withContext(dispatcher) {
            try {
                networkClient.connect()
                _state.value = SessionState.Connected
            } catch (e: Exception) {
                _state.value = SessionState.Error(1001, e.message ?: "連線失敗")
            }
        }
    }
    // ...
}
```

---

## 二、Kotlin 編碼風格

### 2.1 不可變性（最重要的原則）

```kotlin
// 錯誤：直接修改物件
fun updateUser(user: User, name: String): User {
    user.name = name  // 副作用！不可預測！
    return user
}

// 正確：使用 data class copy
fun updateUser(user: User, name: String): User {
    return user.copy(name = name)
}
```

**原則**：
- 優先使用 `val`，除非有明確理由用 `var`
- 使用 `data class` 搭配 `copy()` 取代 mutable 物件
- 集合使用 `List` / `Map` / `Set`，不用 `MutableList`（除非在 builder scope 內）
- 狀態使用 `StateFlow`，不使用 mutable 變數

### 2.2 Sealed Class / Interface

用於建模有限數量的變體，搭配 `when` 做到窮盡式分支處理：

```kotlin
// 操作結果
sealed class OperationResult {
    data object Success : OperationResult()
    data class Failure(val code: Int, val message: String) : OperationResult()
}

// 使用時編譯器確保所有情況都有處理
fun handle(result: OperationResult) = when (result) {
    is OperationResult.Success -> showSuccess()
    is OperationResult.Failure -> showError(result.code, result.message)
    // 沒有 else → 新增變體時編譯器會強制更新
}
```

**什麼時候用 sealed class vs sealed interface？**
- `sealed interface`：純粹的型別區分，不需共享狀態 → 優先使用
- `sealed class`：需要共享屬性或方法時

### 2.3 錯誤處理

**方案一：Kotlin `Result<T>`**（適合簡單場景）

```kotlin
fun fetchData(): Result<Data> {
    return try {
        val data = api.call()
        Result.success(data)
    } catch (e: Exception) {
        Timber.e(e, "資料取得失敗")
        Result.failure(e)
    }
}

// 使用
fetchData()
    .onSuccess { data -> render(data) }
    .onFailure { error -> showError(error.message) }
```

**方案二：自訂 Sealed Class**（適合需要結構化錯誤碼的場景）

```kotlin
sealed class BridgeResult {
    data object Success : BridgeResult()
    data class Failure(val code: Int, val message: String) : BridgeResult()
}

// 定義錯誤碼常數
object ErrorCodes {
    const val ERR_NOT_FOUND = 1001
    const val ERR_NETWORK = 2001
    const val ERR_UNKNOWN = 9999
}
```

### 2.4 輸入驗證

在 ViewModel 或 UseCase 層驗證，不要在 UI 層：

```kotlin
data class UserInput(val email: String, val age: Int)

fun validate(input: UserInput): Result<UserInput> {
    return runCatching {
        require(Patterns.EMAIL_ADDRESS.matcher(input.email).matches()) { "Email 格式錯誤" }
        require(input.age in 0..150) { "年齡範圍不正確" }
        input
    }
}
```

### 2.5 檔案組織

| 規則 | 建議 |
|------|------|
| 檔案行數 | 200-400 行為佳，**不超過 800 行** |
| 函式行數 | **不超過 50 行** |
| 巢狀深度 | **不超過 4 層** |
| 一個檔案一個責任 | 避免把 ViewModel + UI + Model 塞同一個檔案 |
| 目錄結構 | 以功能/領域組織（`feature/login/ui`、`feature/login/domain`） |

### 2.6 命名慣例

```kotlin
// 類別：PascalCase
class UserRepository

// 函式/變數：camelCase
fun fetchUser(): User
val userName: String

// 常數：SCREAMING_SNAKE_CASE
const val MAX_RETRY_COUNT = 3

// Flow/StateFlow：不加底線前綴暴露
private val _state = MutableStateFlow<State>(State.Idle)
val state: StateFlow<State> = _state.asStateFlow()  // 只讀對外
```

### 2.7 日誌

```kotlin
// 錯誤：使用 println 或 Log
println("debug: $data")
Log.d("TAG", "message")

// 正確：使用 Timber（或其他可控制的日誌庫）
Timber.d("User loaded: %s", user.id)
Timber.e(exception, "載入使用者失敗")
```

**為什麼？** `Timber` 可在 Release build 關閉日誌，避免洩漏敏感資訊。

---

## 三、Jetpack Compose 最佳實踐

### 3.1 單向資料流（Unidirectional Data Flow）

```
┌─────────┐  State  ┌─────────┐  Event  ┌──────────┐
│ViewModel│ ──────▶ │   UI    │ ──────▶ │ViewModel │
│ (State  │         │(Compose)│         │ (處理)    │
│  Holder)│ ◀────── │         │ ◀────── │          │
└─────────┘  Action └─────────┘  Update └──────────┘
```

```kotlin
// State：不可變
data class ChatScreenState(
    val messages: List<Message> = emptyList(),
    val inputText: String = "",
    val isLoading: Boolean = false
)

// Action：使用者意圖
sealed interface ChatAction {
    data class TypeMessage(val text: String) : ChatAction
    data object SendMessage : ChatAction
    data object Refresh : ChatAction
}

// ViewModel
class ChatViewModel : ViewModel() {
    private val _state = MutableStateFlow(ChatScreenState())
    val state: StateFlow<ChatScreenState> = _state.asStateFlow()

    fun onAction(action: ChatAction) {
        when (action) {
            is ChatAction.TypeMessage -> _state.update { it.copy(inputText = action.text) }
            is ChatAction.SendMessage -> sendMessage()
            is ChatAction.Refresh -> refresh()
        }
    }
}
```

### 3.2 Composable 無副作用

```kotlin
// 錯誤：在 Composable 中直接產生副作用
@Composable
fun UserProfile(user: User) {
    user.lastViewed = System.currentTimeMillis()  // 修改外部狀態！
    Text(user.name)
}

// 正確：使用 SideEffect API
@Composable
fun UserProfile(user: User, onViewed: () -> Unit) {
    LaunchedEffect(user.id) {
        onViewed()  // 只在 user.id 變化時觸發
    }
    Text(user.name)
}
```

### 3.3 State Hoisting（狀態提升）

```kotlin
// 錯誤：狀態藏在內部，外部無法控制
@Composable
fun SearchBar() {
    var text by remember { mutableStateOf("") }  // 無法從外部預設或讀取
    TextField(value = text, onValueChange = { text = it })
}

// 正確：狀態提升，由呼叫者控制
@Composable
fun SearchBar(
    text: String,
    onTextChange: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    TextField(
        value = text,
        onValueChange = onTextChange,
        modifier = modifier
    )
}
```

**原則**：Composable 的狀態盡量「往上提」，讓 Composable 本身是 stateless 的。

### 3.4 Effect 選擇指南

| Effect | 用途 | 生命週期 |
|--------|------|----------|
| `LaunchedEffect(key)` | 啟動 coroutine，key 變化時重啟 | 跟隨 Composition |
| `DisposableEffect(key)` | 需要清理的副作用（如 listener） | 跟隨 Composition |
| `SideEffect` | 每次 recomposition 都執行 | 無 |
| `rememberCoroutineScope()` | 在 callback 中啟動 coroutine | 手動管理 |

```kotlin
@Composable
fun TimerScreen(session: UserSession) {
    // 收集 Flow → 用 LaunchedEffect
    val state by session.state.collectAsStateWithLifecycle()

    // 需要清理的監聽 → 用 DisposableEffect
    DisposableEffect(Unit) {
        val listener = session.addListener { /* ... */ }
        onDispose { listener.remove() }
    }

    // 只在特定條件觸發一次性動作 → LaunchedEffect + key
    LaunchedEffect(state) {
        if (state is SessionState.Error) {
            snackbarHostState.showSnackbar("發生錯誤")
        }
    }
}
```

---

## 四、狀態管理

### 4.1 StateFlow + Sealed Interface 狀態機

這是 Android 應用中最推薦的狀態管理模式：

```kotlin
// 定義有限狀態
sealed interface ScreenState {
    data object Idle : ScreenState
    data object Loading : ScreenState
    data class Ready(val data: List<Item>) : ScreenState
    data class Error(val message: String) : ScreenState
}

// ViewModel 管理狀態轉換
class ItemViewModel(
    private val repository: ItemRepository
) : ViewModel() {

    private val _state = MutableStateFlow<ScreenState>(ScreenState.Idle)
    val state: StateFlow<ScreenState> = _state.asStateFlow()

    fun load() {
        _state.value = ScreenState.Loading
        viewModelScope.launch {
            repository.getItems()
                .onSuccess { items -> _state.value = ScreenState.Ready(items) }
                .onFailure { e -> _state.value = ScreenState.Error(e.message ?: "未知錯誤") }
        }
    }
}
```

### 4.2 通用狀態機範本

大多數功能都能套用這個模式：

```
Idle ──(load)──▶ Loading ──(成功)──▶ Ready ──(操作)──▶ Active
  ▲                  │                  ▲                 │
  │                  │(失敗)            │(stop)            │(錯誤)
  │                  ▼                  │                  ▼
  └───(release)─── Error ──(retry)──────┘            Error
```

**設計要點**：
- 每個狀態轉換都是明確的、可測試的
- 用 `when` 窮盡處理，新增狀態時編譯器會告訴你哪裡沒處理
- 不要在任意地方修改狀態 — 集中在一個函式或 ViewModel 中

### 4.3 SharedFlow 用於一次性事件

`StateFlow` 適合「持續性狀態」（螢幕上的東西），`SharedFlow` 適合「事件」（一次性通知）：

```kotlin
class ChatViewModel : ViewModel() {
    // 持續狀態 → StateFlow
    private val _state = MutableStateFlow(ChatScreenState())
    val state: StateFlow<ChatScreenState> = _state.asStateFlow()

    // 一次性事件 → SharedFlow
    private val _events = MutableSharedFlow<UiEvent>()
    val events: SharedFlow<UiEvent> = _events.asSharedFlow()

    sealed interface UiEvent {
        data class ShowSnackbar(val message: String) : UiEvent
        data object NavigateBack : UiEvent
    }

    fun send() {
        viewModelScope.launch {
            repository.send(state.value.inputText)
                .onSuccess { _events.emit(UiEvent.ShowSnackbar("已發送")) }
                .onFailure { _events.emit(UiEvent.ShowSnackbar("發送失敗")) }
        }
    }
}
```

### 4.4 在 Compose 中收集

```kotlin
@Composable
fun ChatScreen(viewModel: ChatViewModel) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    // 一次性事件
    LaunchedEffect(Unit) {
        viewModel.events.collect { event ->
            when (event) {
                is UiEvent.ShowSnackbar -> snackbarHostState.showSnackbar(event.message)
                is UiEvent.NavigateBack -> navController.popBackStack()
            }
        }
    }

    // UI 根據 state 渲染
    when (val s = state) {
        is ScreenState.Loading -> CircularProgressIndicator()
        is ScreenState.Ready -> ItemList(s.data)
        is ScreenState.Error -> ErrorView(s.message)
        // ...
    }
}
```

---

## 五、非同步與 Coroutines

### 5.1 結構化併發

**核心概念**：每個 coroutine 都有一個 scope，scope 被取消時所有子 coroutine 一起取消。

```kotlin
class DataSyncService(
    private val scope: CoroutineScope  // 注入 scope，可控制生命週期
) {
    fun startSync() {
        scope.launch {
            // 父 scope 取消 → 這裡自動取消
            while (isActive) {
                sync()
                delay(30_000)
            }
        }
    }
}

// 在 ViewModel 中
class MyViewModel : ViewModel() {
    private val syncService = DataSyncService(viewModelScope)
    // viewModelScope 在 ViewModel 清除時自動取消
}
```

### 5.2 Dispatcher 選擇指南

| Dispatcher | 用途 | 範例 |
|------------|------|------|
| `Dispatchers.Main` | UI 操作、StateFlow 更新 | 預設，Compose 已在此 |
| `Dispatchers.IO` | 網路、檔案、資料庫 | `withContext(Dispatchers.IO) { api.call() }` |
| `Dispatchers.Default` | CPU 密集運算 | 排序、解析大量資料 |
| 自訂 `CoroutineDispatcher` | 測試可控 | 注入取代硬編碼 |

**重要**：在 class 中不要硬編碼 `Dispatchers.IO`，而是注入：

```kotlin
// 正確：可測試
class UserRepository(
    private val api: UserApi,
    private val dispatcher: CoroutineDispatcher = Dispatchers.IO
) {
    suspend fun getUser(id: String): User = withContext(dispatcher) {
        api.fetchUser(id)
    }
}

// 測試時注入 TestDispatcher
val testDispatcher = StandardTestDispatcher()
val repo = UserRepository(fakeApi, testDispatcher)
```

### 5.3 Flow 收集最佳實踐

```kotlin
// 在 ViewModel 中轉換 Flow
class OrderViewModel(
    private val orderRepo: OrderRepository
) : ViewModel() {

    val orders: StateFlow<List<Order>> = orderRepo.observeOrders()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),  // 5 秒後無訂閱者就停止
            initialValue = emptyList()
        )
}
```

**`WhileSubscribed(5_000)` 的意義**：螢幕旋轉時有 5 秒緩衝，不會立刻斷開上游 Flow。

### 5.4 併發控制

```kotlin
// Mutex：保護共享資源
private val mutex = Mutex()
private val items = mutableListOf<Item>()

suspend fun addItem(item: Item) {
    mutex.withLock {
        items.add(item)
    }
}

// Semaphore：限制同時進行的操作數
private val semaphore = Semaphore(3)  // 最多 3 個同時翻譯

suspend fun translate(text: String): String {
    semaphore.withPermit {
        return translator.translate(text)
    }
}
```

---

## 六、設計模式

### 6.1 Bridge Pattern（可測試邊界）

**問題**：直接呼叫平台 API（JNI、資料庫、網路）導致無法單元測試。

**解法**：在 Kotlin 層定義介面，實作與測試分離。

```kotlin
// 定義邊界介面
interface StorageBridge {
    fun save(key: String, value: String): OperationResult
    fun load(key: String): Result<String>
    fun delete(key: String): OperationResult
}

// 真實實作（依賴 Android API）
class SharedPrefStorageBridge(
    private val prefs: SharedPreferences
) : StorageBridge {
    override fun save(key: String, value: String): OperationResult {
        return try {
            prefs.edit().putString(key, value).apply()
            OperationResult.Success
        } catch (e: Exception) {
            OperationResult.Failure(1001, e.message ?: "儲存失敗")
        }
    }
    // ...
}

// 測試用 Fake（不依賴 Android）
class FakeStorageBridge : StorageBridge {
    private val store = mutableMapOf<String, String>()

    override fun save(key: String, value: String): OperationResult {
        store[key] = value
        return OperationResult.Success
    }
    override fun load(key: String) = store[key]?.let { Result.success(it) }
        ?: Result.failure(NoSuchElementException("Key not found: $key"))
    override fun delete(key: String): OperationResult {
        store.remove(key)
        return OperationResult.Success
    }
}
```

**效益**：業務邏輯的測試完全在 JVM 上執行，快速且穩定。

### 6.2 Strategy Pattern（可替換實作）

**問題**：同一個功能可能有多種實作方式（如不同的 AI 模型、不同的資料來源）。

```kotlin
// 定義策略介面
interface TextProcessor {
    suspend fun process(input: String): Result<String>
    fun release()
}

// 實作 A：本地處理
class LocalTextProcessor : TextProcessor {
    override suspend fun process(input: String): Result<String> {
        return Result.success(input.uppercase())
    }
    override fun release() { /* no-op */ }
}

// 實作 B：遠端 API
class RemoteTextProcessor(private val api: TextApi) : TextProcessor {
    override suspend fun process(input: String): Result<String> {
        return runCatching { api.process(input) }
    }
    override fun release() { /* 清理連線 */ }
}

// 使用方不需知道具體實作
class DocumentEditor(private val processor: TextProcessor) {
    suspend fun format(text: String): String {
        return processor.process(text).getOrDefault(text)
    }
}
```

### 6.3 Observer Pattern（StateFlow / SharedFlow）

Kotlin Flow 本身就是 Observer Pattern 的現代化實作：

```kotlin
class SensorManager {
    private val _readings = MutableSharedFlow<SensorReading>(
        replay = 0,
        extraBufferCapacity = 64,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )
    val readings: SharedFlow<SensorReading> = _readings.asSharedFlow()

    fun onNewReading(reading: SensorReading) {
        _readings.tryEmit(reading)
    }
}

// 多個觀察者可獨立訂閱
sensorManager.readings.collect { reading -> updateChart(reading) }
sensorManager.readings.collect { reading -> checkAlerts(reading) }
```

### 6.4 Repository Pattern

封裝資料存取細節，對外提供統一介面：

```kotlin
// 介面在 core 模組
interface UserRepository {
    fun observeUser(id: String): Flow<User>
    suspend fun getUser(id: String): Result<User>
    suspend fun saveUser(user: User): Result<Unit>
}

// 實作在 data 模組
class UserRepositoryImpl(
    private val api: UserApi,
    private val db: UserDao,
    private val dispatcher: CoroutineDispatcher = Dispatchers.IO
) : UserRepository {

    override fun observeUser(id: String): Flow<User> {
        return db.observeUser(id).flowOn(dispatcher)
    }

    override suspend fun getUser(id: String): Result<User> = withContext(dispatcher) {
        runCatching {
            // 先查本地，沒有再打 API
            db.getUser(id) ?: api.fetchUser(id).also { db.insert(it) }
        }
    }

    override suspend fun saveUser(user: User): Result<Unit> = withContext(dispatcher) {
        runCatching {
            api.updateUser(user)
            db.update(user)
        }
    }
}
```

---

## 七、JNI / C++ 互通（進階）

> 本章適用於需要整合 C/C++ 原生程式碼的專案。純 Kotlin 專案可跳過。

### 7.1 架構原則

```
Kotlin 層              JNI 邊界              C++ 層
┌──────────┐     ┌──────────────┐     ┌──────────────┐
│ Session  │ ──▶ │ EngineBridge │ ──▶ │ native-lib   │
│ (業務)   │     │ (介面)       │     │ (JNI 入口)   │
└──────────┘     └──────────────┘     └──────────────┘
                        │
               ┌────────┴────────┐
               │ JniBridgeImpl   │  ← 唯一使用 external fun 的類別
               │ (JNI 實作)      │
               └─────────────────┘
```

**關鍵**：JNI 呼叫集中在一個 Bridge 類別中，業務邏輯不直接碰 JNI。

### 7.2 GlobalRef 管理

```cpp
// 設定 Callback 時建立 GlobalRef
static jobject gCallbackObj = nullptr;

extern "C" JNIEXPORT void JNICALL
Java_com_example_NativeBridge_nativeSetCallback(JNIEnv *env, jobject, jobject callback) {
    // 先清理舊的
    if (gCallbackObj != nullptr) {
        env->DeleteGlobalRef(gCallbackObj);
    }
    // 建立新的 GlobalRef（防止 GC 回收）
    gCallbackObj = env->NewGlobalRef(callback);
}

// 釋放時刪除 GlobalRef
extern "C" JNIEXPORT void JNICALL
Java_com_example_NativeBridge_nativeRelease(JNIEnv *env, jobject) {
    if (gCallbackObj != nullptr) {
        env->DeleteGlobalRef(gCallbackObj);
        gCallbackObj = nullptr;
    }
}
```

**不刪除 GlobalRef = 記憶體洩漏**。每個 `NewGlobalRef()` 必有對應的 `DeleteGlobalRef()`。

### 7.3 背景執行緒的 JNI 呼叫

原生執行緒（非 JVM 建立的）必須先 attach 到 JVM 才能呼叫 JNI：

```cpp
// JNI_OnLoad 時快取 JavaVM
static JavaVM* gJavaVM = nullptr;

JNIEXPORT jint JNI_OnLoad(JavaVM* vm, void*) {
    gJavaVM = vm;
    return JNI_VERSION_1_6;
}

// 在背景執行緒呼叫 JNI
void callbackFromNativeThread(const char* result) {
    JNIEnv* env = nullptr;
    bool didAttach = false;

    int status = gJavaVM->GetEnv(reinterpret_cast<void**>(&env), JNI_VERSION_1_6);
    if (status == JNI_EDETACHED) {
        gJavaVM->AttachCurrentThread(&env, nullptr);
        didAttach = true;
    }

    if (env != nullptr && gCallbackObj != nullptr) {
        jclass cls = env->GetObjectClass(gCallbackObj);
        jmethodID method = env->GetMethodID(cls, "onResult", "(Ljava/lang/String;)V");
        jstring jResult = env->NewStringUTF(result);
        env->CallVoidMethod(gCallbackObj, method, jResult);

        // 檢查例外
        if (env->ExceptionCheck()) {
            env->ExceptionDescribe();
            env->ExceptionClear();
        }
    }

    if (didAttach) {
        gJavaVM->DetachCurrentThread();
    }
}
```

### 7.4 Kotlin 端安全呼叫

```kotlin
// 使用 @Keep 防止 ProGuard 移除
@Keep
class JniEngineBridge : EngineBridge {
    companion object {
        init {
            System.loadLibrary("native-lib")
        }
    }

    // 所有 external fun 集中在此
    private external fun nativeInit(config: String): Int
    private external fun nativeStart(): Int
    private external fun nativeStop(): Int
    private external fun nativeRelease()

    override fun init(config: EngineConfig): OperationResult {
        return try {
            val code = nativeInit(config.toJson())
            if (code == 0) OperationResult.Success
            else OperationResult.Failure(code, "Native init 失敗")
        } catch (e: UnsatisfiedLinkError) {
            OperationResult.Failure(9999, "Native library 未載入")
        }
    }
}
```

---

## 八、測試策略

### 8.1 TDD 流程

```
🔴 RED → 🟢 GREEN → 🔵 REFACTOR → 重複
```

1. **RED**：先寫測試，執行 → 失敗（證明測試有意義）
2. **GREEN**：寫最少的程式碼讓測試通過
3. **REFACTOR**：整理程式碼，測試必須持續通過

### 8.2 測試類型與工具

| 類型 | Source Set | 環境 | 工具 | 速度 |
|------|-----------|------|------|------|
| 單元測試 | `test` | 本地 JVM | JUnit, MockK, Turbine | 快 |
| 整合測試 | `androidTest` | 裝置/模擬器 | Espresso, Room test | 中 |
| UI 測試 | `androidTest` | 裝置/模擬器 | Compose UI Test | 慢 |
| Native 測試 | `cpp/test` | 裝置 | Google Test (GTest) | 中 |

### 8.3 覆蓋率目標

**最低 80%** — 不追求 100%，但核心邏輯必須覆蓋：

| 優先測試 | 可不測 |
|----------|--------|
| 狀態轉換邏輯 | 簡單的 data class |
| 業務規則 | Android 框架膠水程式碼 |
| 錯誤處理路徑 | 純 UI 佈局 |
| 邊界條件 | 第三方庫的封裝 |

### 8.4 Coroutine 測試技巧

```kotlin
class UserViewModelTest {

    @Test
    fun `load should transition from Loading to Ready`() = runTest {
        // Arrange
        val fakeRepo = FakeUserRepository(
            users = listOf(User("1", "Alice"))
        )
        val viewModel = UserViewModel(fakeRepo, StandardTestDispatcher(testScheduler))

        // 收集狀態
        val states = mutableListOf<ScreenState>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.state.collect { states.add(it) }
        }

        // Act
        viewModel.load()
        advanceUntilIdle()

        // Assert
        assertThat(states).containsExactly(
            ScreenState.Idle,
            ScreenState.Loading,
            ScreenState.Ready(listOf(User("1", "Alice")))
        ).inOrder()
    }
}
```

**關鍵**：
- `runTest` 提供虛擬時間，`delay()` 不會真的等待
- `StandardTestDispatcher` 需要手動推進時間（`advanceUntilIdle()`）
- `UnconfinedTestDispatcher` 立即執行（適合收集 Flow 的背景 coroutine）

### 8.5 Fake vs Mock

| | Fake | Mock (MockK/Mockito) |
|---|------|------|
| 定義 | 手寫的簡化實作 | 框架自動產生 |
| 適用 | 有複雜行為的依賴 | 簡單的驗證呼叫次數/參數 |
| 維護 | 需要跟介面同步更新 | 自動跟隨 |
| 可讀性 | 高（測試邏輯透明） | 中（需理解 Mock DSL） |

**建議**：核心邊界（Bridge、Repository）用 Fake，周邊依賴用 Mock。

```kotlin
// Fake 範例
class FakeUserRepository : UserRepository {
    var shouldFail = false
    private val users = mutableListOf<User>()

    fun addUser(user: User) { users.add(user) }

    override suspend fun getUser(id: String): Result<User> {
        if (shouldFail) return Result.failure(Exception("模擬失敗"))
        return users.find { it.id == id }
            ?.let { Result.success(it) }
            ?: Result.failure(NoSuchElementException("找不到使用者"))
    }
}

// 測試中使用
@Test
fun `getUser failure should show error state`() = runTest {
    val fakeRepo = FakeUserRepository().apply { shouldFail = true }
    val viewModel = UserViewModel(fakeRepo)

    viewModel.load("123")
    advanceUntilIdle()

    assertThat(viewModel.state.value).isInstanceOf(ScreenState.Error::class.java)
}
```

### 8.6 測試品質要求

- **禁止佔位測試**：不允許 `assertTrue(true)` 或空測試
- **測試必須驗證行為**：每個測試至少一個有意義的 assertion
- **修正實作而非測試**：測試失敗時，優先檢查實作是否有 bug，而非修改測試讓它通過
- **測試命名**：使用反引號描述行為 `` `should transition to Error when network fails` ``

---

## 九、Build 設定參考

### 9.1 Version Catalog（`gradle/libs.versions.toml`）

集中管理所有依賴版本，避免版本散落各處：

```toml
[versions]
kotlin = "2.3.0"
compose-bom = "2025.01.00"
coroutines = "1.9.0"
lifecycle = "2.8.0"
timber = "5.0.1"

[libraries]
kotlin-stdlib = { group = "org.jetbrains.kotlin", name = "kotlin-stdlib", version.ref = "kotlin" }
compose-bom = { group = "androidx.compose", name = "compose-bom", version.ref = "compose-bom" }
coroutines-core = { group = "org.jetbrains.kotlinx", name = "kotlinx-coroutines-core", version.ref = "coroutines" }
coroutines-test = { group = "org.jetbrains.kotlinx", name = "kotlinx-coroutines-test", version.ref = "coroutines" }
timber = { group = "com.jakewharton.timber", name = "timber", version.ref = "timber" }

[plugins]
android-application = { id = "com.android.application", version = "8.7.0" }
android-library = { id = "com.android.library", version = "8.7.0" }
kotlin-compose = { id = "org.jetbrains.kotlin.plugin.compose", version.ref = "kotlin" }
```

### 9.2 根 `build.gradle.kts`

```kotlin
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.android.library) apply false
    alias(libs.plugins.kotlin.compose) apply false
}
```

### 9.3 多模組 `settings.gradle.kts`

```kotlin
pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "MyApp"
include(":app")
include(":core")
include(":data")
// include(":feature-chat")
```

### 9.4 常用 Gradle 指令

```bash
# 確認 JAVA_HOME（Gradle 需要正確的 JDK）
echo "$JAVA_HOME"
JAVA_HOME="$("/usr/libexec/java_home")"

# 建置
./gradlew build                           # 全部建置
./gradlew :app:assembleDebug              # Debug APK
./gradlew clean                           # 清除建置

# 測試
./gradlew test                            # 所有模組的單元測試
./gradlew :core:test                      # 單一模組
./gradlew :core:test --tests "com.example.core.SessionStateTest"  # 單一測試類別
./gradlew :app:connectedAndroidTest       # 裝置測試

# 檢查
./gradlew dependencies                    # 檢視依賴樹
./gradlew :app:signingReport              # 簽名資訊
```

### 9.5 Android Library 模組範本

```kotlin
// feature-xxx/build.gradle.kts
plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "com.example.feature.xxx"
    compileSdk = 36

    defaultConfig {
        minSdk = 26
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildFeatures {
        compose = true
    }

    // JVM 單元測試時 Android API 回傳預設值而非拋例外
    testOptions {
        unitTests.isReturnDefaultValues = true
    }
}

dependencies {
    implementation(project(":core"))
    implementation(libs.coroutines.core)

    testImplementation(libs.junit)
    testImplementation(libs.coroutines.test)
}
```

---

## 十、程式碼品質檢查清單

每次提交前檢查：

### 可讀性
- [ ] 命名清晰，符合 Kotlin 慣例（camelCase / PascalCase）
- [ ] 函式 < 50 行，職責單一
- [ ] 檔案 < 800 行，高內聚
- [ ] 巢狀 < 4 層（善用 early return）
- [ ] 無硬編碼數值（使用 `res/values` 或常數）

### 不可變性
- [ ] 使用 `val`，非必要不用 `var`
- [ ] 狀態修改透過 `copy()` 或 `StateFlow`
- [ ] 集合用 `List` / `Map`（不暴露 `MutableList`）

### Compose
- [ ] Composable 無副作用（不在 Composition 中修改外部狀態）
- [ ] 狀態已提升（State Hoisting）
- [ ] 正確使用 Effect API（`LaunchedEffect` / `DisposableEffect`）

### 安全性
- [ ] JNI 方法處理 null pointer 和例外
- [ ] 錯誤處理完善（`Result` / sealed class）
- [ ] 輸入已驗證

### 測試
- [ ] 核心邏輯有對應的單元測試
- [ ] 測試覆蓋率 ≥ 80%
- [ ] 無佔位測試（`SUCCEED()` / `assertTrue(true)`）
- [ ] 測試驗證實際行為

### 效能
- [ ] 無 `println`（使用 `Timber`）
- [ ] 長時間操作使用 `withContext(Dispatchers.IO)`
- [ ] Flow 有背壓策略（`BufferOverflow`）

### 記憶體
- [ ] `GlobalRef` 有對應的 `DeleteGlobalRef()`
- [ ] Coroutine scope 與生命週期綁定（`viewModelScope`）
- [ ] `DisposableEffect` 有 `onDispose` 清理

---

## 附錄 A：推薦技術堆疊

| 領域 | 推薦 | 備註 |
|------|------|------|
| 語言 | Kotlin 2.x | 預設選擇 |
| UI | Jetpack Compose + Material 3 | 聲明式 UI |
| 非同步 | Kotlin Coroutines + Flow | 結構化併發 |
| DI | Hilt / Koin | Hilt 適合大專案，Koin 適合輕量 |
| 網路 | Retrofit + OkHttp / Ktor Client | Ktor 更 Kotlin-native |
| 資料庫 | Room | 官方推薦 |
| 圖片 | Coil | Compose-first |
| 日誌 | Timber | 可控制的日誌 |
| 測試 | JUnit + MockK + Turbine | Turbine 測 Flow |
| 記憶體檢測 | LeakCanary（debug only） | 自動偵測記憶體洩漏 |
| Build | Gradle Kotlin DSL + Version Catalog | 型別安全 |

## 附錄 B：專案初始化 Checklist

開新專案時照著走：

1. [ ] 建立多模組結構（`core` + `data`/`feature` + `app`）
2. [ ] 設定 Version Catalog（`gradle/libs.versions.toml`）
3. [ ] `core` 模組定義介面與資料模型
4. [ ] 設定 `testOptions.unitTests.isReturnDefaultValues = true`
5. [ ] 加入 Timber（app 模組 Application 初始化）
6. [ ] 加入 LeakCanary（debug only）
7. [ ] 建立 Fake 實作供單元測試使用
8. [ ] 設定 `.gitignore`
9. [ ] 建立 CHANGELOG.md

---

*文件版本：1.0 | 建立日期：2026 年 2 月 10 日*
