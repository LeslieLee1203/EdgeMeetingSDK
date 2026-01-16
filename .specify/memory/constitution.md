<!--
Sync Impact Report
- 版本變更: 未定義 → 1.0.0
- 原則變更: 新增「程式碼品質與可讀性」「測試先行與 TDD 紀律」「使用者體驗一致性」「效能預算與資源控制」
- 新增章節: 開發流程要求、品質門檻與驗證
- 移除章節: 無
- Templates requiring updates:
  - ✅ .specify/templates/plan-template.md
  - ✅ .specify/templates/spec-template.md
  - ✅ .specify/templates/tasks-template.md
  - ✅ .specify/templates/checklist-template.md
  - ⚠ .specify/templates/commands/*.md（找不到路徑）
- Follow-up TODOs:
  - TODO(RATIFICATION_DATE): 尚未提供原始採納日期
-->
# EdgeMeetingSDK Constitution

## Core Principles

### 程式碼品質與可讀性
程式碼必須可讀、可維護、可測。遵守 KISS/YAGNI/DRY/SOLID。
所有程式碼與建議必須包含錯誤處理與明確失敗路徑。

### 測試先行與 TDD 紀律
TDD 為強制流程：先寫測試且必須失敗，再進行最小實作。
產出程式碼前必須先以註解說明「為什麼要這樣做」，再寫程式碼。
每次只允許輸出一個 function 或一段程式碼，需經使用者驗證後才能繼續。

### 使用者體驗一致性
UI/UX 必須與既有設計語言與互動模式一致。
新增介面或文案需符合既有元件與樣式，避免未定義的視覺或行為。

### 效能預算與資源控制
關鍵路徑必須有明確效能目標與量測策略。
不得引入無必要的記憶體、CPU 或 I/O 開銷，需有可量化的理由。

## 開發流程要求
1. 先寫測試並確認失敗，再進行最小實作（TDD 小步快跑）。
2. 實作前先用註解說明理由，再撰寫程式碼。
3. 每次變更只包含一個 function 或一段程式碼，通過驗證再前進。
4. 所有變更需維持錯誤處理與可回復性。

## 品質門檻與驗證
- 測試覆蓋必須對應需求與使用情境，避免只測 happy path。
- UI/UX 變更需檢查一致性（元件、字型、間距、互動）。
- 效能變更需附量測基準或比較結果，無量測不得宣稱優化。

## Governance
- 本憲章優先於其他慣例與模板，違反須明確記錄原因與替代方案。
- 任何修改需附變更動機、影響範圍與遷移或教育計畫。
- 版本採語意化版本：破壞性變更升 MAJOR；新增原則或章節升 MINOR；文字澄清升 PATCH。
- 每次計畫、規格與任務文件需檢核憲章條款的符合性。

**Version**: 1.0.0 | **Ratified**: TODO(RATIFICATION_DATE): 尚未提供原始採納日期 | **Last Amended**: 2026-01-16
