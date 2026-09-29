# 二維材料拉伸模擬 API：專案記錄

記錄日期：2026-09-28。目標是在 2026-10-01 前完成一個可執行、可用 `curl` 展示、能解釋設計決策的後端作品。這是練習題目，不代表 Garmin 的實際系統或指定技術。以下記錄目前討論的模擬方向；實作前仍可縮減範圍。

## 第一版範圍

使用者送出一組模擬設定。模型採用固定尺寸的二維長方形、固定邊界條件與水平位移控制；Python 使用平面應力有限元素法計算各步的水平正應變場 εxx，後端保存每步的摘要，供使用者查詢。這與原先「設備上傳已算好的量測值」是不同流程。

| 操作 | 預期行為 |
| --- | --- |
| `POST /simulations` | 接收模擬設定，驗證後產生每步結果；成功建立模擬回 `201` 與系統產生的 `simulationId`，無效輸入回 `400`。 |
| `GET /simulations/{simulationId}/steps` | 回傳該模擬的每步位移與 εxx 摘要，依 `step` 遞增；模擬不存在回 `404`。 |
| `GET /simulations/{simulationId}/summary` | 回傳完成步數及各步 `maxExx` 中的最大值；模擬不存在回 `404`。 |

第一版的使用者輸入為四個欄位：`youngModulusMpa`、`poissonRatio`、`displacementIncrementMm`、`stepCount`。長方形尺寸、邊界條件與二維模型假設由程式固定，並在文件或回應中列明。可先展示 3 次模擬、每次 10 步，共 30 筆步驟結果。Python 對外產生水平正應變 εxx map；後端只保存與回傳每步的 `meanExx`、`maxExx` 摘要，不儲存整張 map。

`POST /simulations` 範例輸入：

```json
{
  "youngModulusMpa": 1000,
  "poissonRatio": 0.3,
  "displacementIncrementMm": 0.1,
  "stepCount": 10
}
```

系統產生 `simulationId`；每一步的結果包含 `step`、`appliedDisplacementMm`、`meanExx`、`maxExx`。例如每步增加 0.1 mm、共 10 步，會產生 10 筆結果，前三步累積位移為 0.1、0.2、0.3 mm，最後一步為 1.0 mm。`specimenId` 不再是必要欄位，因為此處識別的是一次模擬，不一定是真實試片。

## 先說清楚的資料規則

- `youngModulusMpa` 必須為正的有限數值；`poissonRatio` 必須為有限數值且在 0～0.49（含端點）。
- `displacementIncrementMm` 是每步增加的水平位移，單位 mm；它是指定位移，不是力。它必須為正的有限數值；`stepCount` 必須為 1～100 的整數，且 `stepCount × displacementIncrementMm ≤ 1 mm`。單步增量上限隨步數變動，為 `1 / stepCount` mm；例如 10 步最多每步 0.1 mm。無效輸入回 `400`。
- `meanExx`、`maxExx` 專指水平正應變 εxx，是無單位比例；`0.002` 表示 0.2%，不是 0.002%。Python 使用 100 × 50 格、每格 1 × 1 mm 的網格，於元素中心產生 εxx map；摘要固定從未變形試片中央的 `20 ≤ x ≤ 80 mm、10 ≤ y ≤ 40 mm` 取樣，共 1,800 格。`meanExx` 為平均值，`maxExx` 為最大取樣值。
- 固定幾何均以 mm 定義：水平長度 L₀ = 100 mm、垂直高度 H = 50 mm、z 方向厚度 t = 1 mm；採平面應力假設。左邊界夾具固定，`ux = uy = 0`；右邊界指定水平位移、`uy` 自由；上下邊界為自由表面。E 的輸入單位為 MPa、ν 無單位；Python 在求解入口集中轉為 Pa、m，Java 不做單位換算。詳見 [計算力學流程](flow.md)。
- 對均質線彈性材料，在理想單軸、位移控制的情況，水平應變約為 `appliedDisplacementMm / lengthMm`。改變楊氏模數 E 主要改變應力或反作用力，不一定改變 εxx；在理想單軸情況，蒲松比 ν 主要影響橫向收縮。因此材料係數雖可保留在輸入與紀錄中，不能聲稱更改它們必然讓 εxx map 改變。
- 若所有步驟使用相同增量，使用 `displacementIncrementMm + stepCount`；若日後要逐步指定不同位移，改用位移清單，步數由清單長度決定。
- 一次模擬有一個 `simulationId`，步驟結果以 `simulationId + step` 唯一識別。原先對「重送單筆量測」的 `200`／`409` 規則不再適用於第一版；重送整份模擬設定預設建立新模擬。
- 第一版建議使用 **H2 embedded 檔案模式**：Spring Boot 與 H2 在同一個 Java 程序內，資料寫入本機檔案，服務重啟後仍保留。JDBC 位置由設定決定，不必放在專案資料夾。記憶體模式可留給測試；它在程序結束後會清空資料。
- 即使使用 H2 檔案模式，也要避免在每次啟動時重建資料表；展示時另準備可重跑的重設與範例模擬流程。資料庫檔案不加入 Git；本專案目前位於非同步的本機資料夾。

作品重點是後端工程：清楚的請求驗證、Java 與 Python 的資料交接、模擬與每步結果保存、排序查詢、錯誤回應，以及能重跑的測試和展示。力學輸出維持在 εxx，不擴充其他物理量。

本專案統一使用名為 `backend` 的 conda 環境，環境定義見 [environment.yaml](environment.yaml)。Java 與後續的 Python 計算、建置及測試都應在此環境中執行。Python 計算所需套件為 `scikit-fem`、`numpy`、`scipy`，需 Python 3.10 以上；若要輸出圖片，再選裝 `matplotlib`。目前 `backend` 尚未安裝 Python 或這些套件；實作 Python 計算前，須將它們加入同一環境。安裝與數值實作方式詳見 [計算力學流程](flow.md)。

Python 計算異常或輸出無效時，API 回 `500`、`{"code":"CALCULATION_FAILED","message":"Calculation failed."}`；超過 30 秒時回 `500`、`{"code":"CALCULATION_TIMEOUT","message":"Calculation timed out."}`。失敗或逾時均不保存部分模擬結果。

## 技術與學習路線

選用 Java 21、Spring Boot、Maven Wrapper、Spring Web、Validation、Spring Data JPA、H2、Git 和 `curl`。先完成流程，再視時間考慮 PostgreSQL、Docker 或圖表。

| 新名詞 | 以已知概念理解 |
| --- | --- |
| HTTP API | 類似 Python 函式的公開入口，但輸入來自網路請求，輸出是狀態碼和 JSON。 |
| Controller | 對應網址與 HTTP 方法，將請求交給 Java 程式處理。 |
| DTO | 專門描述 API 輸入或輸出的 Java 類別，和資料庫物件分開。 |
| Validation | 在寫入之前檢查欄位，類似函式開頭的參數檢查。 |
| Service | 放模擬流程、步驟結果與摘要等業務規則的 Java 類別。 |
| Entity | 對應資料庫表格一列的 Java 類別。 |
| Repository / JPA | 用 Java 介面讀寫資料；JPA 會替常見操作產生 SQL，但仍須理解資料表、唯一鍵與查詢。 |
| SQL | 向關聯式資料庫查詢資料的語言；本題先學 `WHERE`、`ORDER BY`、`COUNT`、`MAX` 和唯一限制即可。 |
| Maven Wrapper (`./mvnw`) | 專案附帶的建置工具入口，用來下載依賴、執行與測試，無須先另裝 Maven。 |

建議順序：

1. 使用 `backend` 環境中的 Java 21，產生 Spring Initializr 專案，先跑起最小程式。
2. 固定長方形尺寸、二維假設及邊界條件；先用 Python 對一組輸入產生每步 εxx map 與摘要，與 `ΔL/L₀` 基準比較。
3. 用 Java 類別定義四個請求欄位與驗證規則，完成 `POST /simulations`；明確定義 Java 與 Python 之間的輸入、輸出及計算失敗處理。
4. 加入 H2、Entity、Repository 與唯一限制，完成步驟查詢和摘要。
5. 用整合測試驗證正常模擬、位移累積、排序、摘要、無效輸入，以及 Python 計算失敗時的回應。
6. 用 `curl` 執行完整展示：送出模擬、查詢各步與摘要，將命令和預期結果寫入 NOTES.md。

AI Coding 時一次交付一個可驗收的小任務。每次請 AI 說明新增的類別與資料流程；自己閱讀修改，執行測試，再用 `curl` 驗證。若程式碼能跑但無法解釋「設定如何從 JSON 進入 Java、交給 Python 計算、保存至 H2、再回到 JSON」，就先停下來釐清。

## 目前環境與待處理事項

- 此資料夾目前尚無 Java 或 Python 程式；Git 已初始化，但目前檔案尚未提交。
- `backend` conda 環境目前有 OpenJDK 21.0.10，`environment.yaml` 指定 `openjdk=21`；執行本專案的 Java 指令時須使用此環境。目前該環境尚無 Python，Python 部分開始實作前須補齊環境與套件設定。
- Git 和 `curl` 已可用。`code` 命令不存在，只代表 VS Code 的命令列入口尚未設定，不能據此判定是否安裝 VS Code。
- 此目錄已位於本機非同步資料夾。

## 參考資料

- [Spring：Building a RESTful Web Service](https://spring.io/guides/gs/rest-service)
- [Spring：Accessing Data with JPA](https://spring.io/guides/gs/accessing-data-jpa/)
- [Spring Boot：Validation](https://docs.spring.io/spring-boot/reference/io/validation.html)
- [COMSOL：線彈性結構的位移、應變與反作用力](https://www.comsol.com/blogs?p=29033)
