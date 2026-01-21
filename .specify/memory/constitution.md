<!--
Sync Impact Report
- 版本變更: 1.0.2 → 1.1.0
- 原則變更: 「測試先行與 TDD 紀律」- 驗證單元從「一個 function 或一段程式碼」改為「一個完整 TDD 循環 (RED → GREEN → REFACTOR)」
- 新增章節: 無
- 移除章節: 無
- Templates requiring updates:
  - ✅ .specify/templates/plan-template.md (已更新 Constitution Check)
  - ✅ .specify/templates/tasks-template.md (已更新 TDD 流程說明)
  - ✅ .specify/templates/spec-template.md (無需變更)
  - ⚠ .specify/templates/commands/*.md (路徑不存在)
- Follow-up TODOs:
  - TODO(RATIFICATION_DATE): 尚未提供原始採納日期
-->
# EdgeMeetingSDK Constitution

## Core Principles

### 程式碼品質與可讀性
程式碼必須可讀、可維護、可測。遵守 KISS/YAGNI/DRY/SOLID。
所有程式碼與建議必須包含錯誤處理與明確失敗路徑。

### 測試先行與 TDD 紀律
TDD 為強制流程：先寫測試且必須失敗，再進行最小實作，最後再重構（RED → GREEN → REFACTOR）。
執行上遵循小步快跑，盡量切割最小可驗證的單元。
產出程式碼前必須先以註解說明「為什麼要這樣做」，再寫程式碼。
註解必須使用台灣繁體中文（zh_TW）。
每次只允許完成一個循環（RED → GREEN → REFACTOR）作為運行單元，需經過驗證後才能繼續下個循環。

### 使用者體驗一致性
UI/UX 必須與既有設計語言與互動模式一致。
新增介面或文案需符合既有元件與樣式，避免未定義的視覺或行為。

### 效能預算與資源控制
關鍵路徑必須有明確效能目標與量測策略。
不得引入無必要的記憶體、CPU 或 I/O 開銷，需有可量化的理由。

## 開發流程要求
1. 先寫測試並確認失敗 → 最小實作 → 重構（RED → GREEN → REFACTOR）。
2. 小步快跑：每次只處理最小可驗證單元。
3. 實作前先用註解說明理由（zh_TW），再撰寫程式碼。
4. 每次變更只包含一個完整 TDD 循環（RED → GREEN → REFACTOR），通過驗證再前進下個循環。
5. 所有變更需維持錯誤處理與可回復性。

## 品質門檻與驗證
- 測試覆蓋必須對應需求與使用情境，避免只測 happy path。
- UI/UX 變更需檢查一致性（元件、字型、間距、互動）。
- 效能變更需附量測基準或比較結果，無量測不得宣稱優化。

## Governance
- 本憲章優先於其他慣例與模板，違反須明確記錄原因與替代方案。
- 任何修改需附變更動機、影響範圍與遷移或教育計畫。
- 版本採語意化版本：破壞性變更升 MAJOR；新增原則或章節升 MINOR；文字澄清升 PATCH。
- 每次計畫、規格與任務文件需檢核憲章條款的符合性。

**Version**: 1.1.0 | **Ratified**: TODO(RATIFICATION_DATE): 尚未提供原始採納日期 | **Last Amended**: 2026-01-21
