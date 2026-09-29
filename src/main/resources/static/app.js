const exampleLegs = [
  { fromStationNo: "S1", toStationNo: "S2", distanceM: 30.004, azimuthDeg: 89.98 },
  { fromStationNo: "S2", toStationNo: "S3", distanceM: 20.006, azimuthDeg: 0.03 },
  { fromStationNo: "S3", toStationNo: "S4", distanceM: 29.991, azimuthDeg: 269.97 },
  { fromStationNo: "S4", toStationNo: "S1", distanceM: 19.985, azimuthDeg: 180.02 },
];

const form = document.querySelector("#traverse-form");
const timeZone = document.querySelector("#time-zone");
const legRows = document.querySelector("#leg-rows");
const submitButton = document.querySelector("#submit-button");
const formMessage = document.querySelector("#form-message");
const resultEmpty = document.querySelector("#result-empty");
const resultContent = document.querySelector("#result-content");
const historyContent = document.querySelector("#history-content");

const fieldDefinitions = [
  { name: "fromStationNo", label: "起站", type: "text" },
  { name: "toStationNo", label: "迄站", type: "text" },
  { name: "distanceM", label: "水平距離 m", type: "number" },
  { name: "azimuthDeg", label: "方位角 °", type: "number" },
];

function addLeg(leg = {}) {
  const row = document.createElement("div");
  row.className = "leg-row";
  const index = document.createElement("span");
  index.className = "leg-index";
  row.append(index);

  for (const field of fieldDefinitions) {
    const label = document.createElement("label");
    const labelText = document.createElement("span");
    labelText.textContent = field.label;
    const input = document.createElement("input");
    input.name = field.name;
    input.type = field.type;
    input.required = true;
    input.value = leg[field.name] ?? "";
    if (field.type === "text") {
      input.maxLength = 100;
      input.autocomplete = "off";
    } else {
      input.step = "any";
      input.inputMode = "decimal";
    }
    label.append(labelText, input);
    row.append(label);
  }

  const remove = document.createElement("button");
  remove.type = "button";
  remove.className = "remove-leg";
  remove.textContent = "×";
  remove.addEventListener("click", () => {
    row.remove();
    updateLegLabels();
  });
  row.append(remove);
  legRows.append(row);
  updateLegLabels();
}

function updateLegLabels() {
  const rows = [...legRows.children];
  rows.forEach((row, index) => {
    row.querySelector(".leg-index").textContent = String(index + 1).padStart(2, "0");
    row.querySelector(".remove-leg").disabled = rows.length <= 3;
    row.querySelector(".remove-leg").setAttribute("aria-label", `移除第 ${index + 1} 段`);
    fieldDefinitions.forEach((field, fieldIndex) => {
      row.querySelectorAll("input")[fieldIndex].setAttribute("aria-label", `第 ${index + 1} 段${field.label}`);
    });
  });
}

function showMessage(type, message) {
  formMessage.hidden = false;
  formMessage.className = `form-message ${type}`;
  formMessage.setAttribute("role", type === "error" ? "alert" : "status");
  formMessage.textContent = message;
}

function clearMessage() {
  formMessage.hidden = true;
  formMessage.textContent = "";
}

function resetResult() {
  resultContent.replaceChildren();
  resultContent.hidden = true;
  resultEmpty.hidden = false;
}

function resetForm() {
  timeZone.value = Intl.DateTimeFormat().resolvedOptions().timeZone || "Asia/Taipei";
  legRows.replaceChildren();
  for (let i = 0; i < 3; i++) addLeg();
  clearMessage();
  resetResult();
}

function loadExample() {
  timeZone.value = "Asia/Taipei";
  legRows.replaceChildren();
  exampleLegs.forEach(addLeg);
  resetResult();
  showMessage("info", "已填入展示範例；按「計算並儲存」後才會建立紀錄。");
}

function readObservations() {
  const legs = [...legRows.children].map((row) => {
    const values = Object.fromEntries([...row.querySelectorAll("input")].map((input) => [input.name, input.value]));
    return {
      fromStationNo: values.fromStationNo.trim(),
      toStationNo: values.toStationNo.trim(),
      distanceM: Number(values.distanceM),
      azimuthDeg: Number(values.azimuthDeg),
    };
  });

  const zone = timeZone.value.trim();
  try {
    Intl.DateTimeFormat("en-US", { timeZone: zone });
  } catch {
    throw new Error("請輸入有效時區，例如 Asia/Taipei。");
  }
  for (let i = 0; i < legs.length; i++) {
    const leg = legs[i];
    const next = legs[(i + 1) % legs.length];
    if (!leg.fromStationNo || !leg.toStationNo) throw new Error(`第 ${i + 1} 段缺少測站編號。`);
    if (!Number.isFinite(leg.distanceM) || leg.distanceM <= 0) throw new Error(`第 ${i + 1} 段距離須大於零。`);
    if (!Number.isFinite(leg.azimuthDeg) || leg.azimuthDeg < 0 || leg.azimuthDeg >= 360) {
      throw new Error(`第 ${i + 1} 段方位角須介於 0（含）至 360（不含）度。`);
    }
    if (leg.toStationNo !== next.fromStationNo) {
      throw new Error(`第 ${i + 1} 段的迄站須接續下一段起站；最後一段須返回第一站。`);
    }
  }
  return { timeZone: zone, legs };
}

async function requestJson(path, options) {
  const response = await fetch(path, options);
  const body = await response.json().catch(() => null);
  if (!response.ok) throw new Error(body?.message || `請求失敗（HTTP ${response.status}）。`);
  if (!body) throw new Error("伺服器沒有回傳可讀取的資料。");
  return body;
}

function readableError(error) {
  return error instanceof TypeError ? "無法連接後端，請確認服務仍在執行。" : error.message;
}

function decimal(value, places = 6) {
  const rounded = Math.abs(value) < 0.5 * 10 ** -places ? 0 : value;
  return Number(rounded).toFixed(places);
}

function displayTime(value) {
  return new Date(value).toLocaleString("zh-TW", { dateStyle: "medium", timeStyle: "short" });
}

function createTable(headers, rows) {
  const wrapper = document.createElement("div");
  wrapper.className = "table-scroll";
  const table = document.createElement("table");
  const thead = document.createElement("thead");
  const headerRow = document.createElement("tr");
  headers.forEach((header) => {
    const cell = document.createElement("th");
    cell.scope = "col";
    cell.textContent = header;
    headerRow.append(cell);
  });
  thead.append(headerRow);
  const tbody = document.createElement("tbody");
  rows.forEach((row) => {
    const tr = document.createElement("tr");
    row.forEach((value) => {
      const cell = document.createElement("td");
      cell.textContent = value;
      tr.append(cell);
    });
    tbody.append(tr);
  });
  table.append(thead, tbody);
  wrapper.append(table);
  return wrapper;
}

function metric(label, value) {
  const item = document.createElement("div");
  item.className = "metric";
  const caption = document.createElement("span");
  caption.textContent = label;
  const number = document.createElement("strong");
  number.textContent = value;
  item.append(caption, number);
  return item;
}

function renderResult(data) {
  resultEmpty.hidden = true;
  resultContent.hidden = false;
  resultContent.replaceChildren();

  const meta = document.createElement("p");
  meta.className = "result-meta";
  meta.textContent = `紀錄 ID ${data.id} · 建檔時間 ${displayTime(data.createdAt)} · 時區紀錄 ${data.timeZone} · 演算法 ${data.adjustmentVersion}`;
  const metrics = document.createElement("div");
  metrics.className = "metric-grid";
  metrics.append(
    metric("總原始邊長", `${decimal(data.totalLengthM, 3)} m`),
    metric("原始閉合差長度", `${decimal(Math.hypot(data.closureDxM, data.closureDyM))} m`),
    metric("東向閉合差 fX", `${decimal(data.closureDxM)} m`),
    metric("北向閉合差 fY", `${decimal(data.closureDyM)} m`),
  );
  resultContent.append(meta, metrics);

  const stationTitle = document.createElement("h3");
  stationTitle.className = "section-title";
  stationTitle.textContent = "改正後測站座標";
  const stationRows = [[data.legs[0].fromStationNo, decimal(0), decimal(0)]];
  data.legs.forEach((leg) => stationRows.push([leg.toStationNo, decimal(leg.adjustedEndXM), decimal(leg.adjustedEndYM)]));
  resultContent.append(stationTitle, createTable(["測站", "X 向東 m", "Y 向北 m"], stationRows));

  const details = document.createElement("details");
  const summary = document.createElement("summary");
  summary.textContent = "查看每段計算明細";
  const help = document.createElement("p");
  help.className = "detail-help";
  help.textContent = "數值僅在頁面顯示時取六位小數；原始觀測值保存不變。";
  const rows = data.legs.map((leg) => [
    `${leg.sequenceNo}. ${leg.fromStationNo} → ${leg.toStationNo}`,
    decimal(leg.distanceM, 3), decimal(leg.azimuthDeg, 3),
    decimal(leg.rawDxM), decimal(leg.rawDyM),
    decimal(leg.correctionDxM), decimal(leg.correctionDyM),
    decimal(leg.adjustedDxM), decimal(leg.adjustedDyM),
  ]);
  details.append(summary, help, createTable(
    ["觀測段", "距離 m", "方位角 °", "原始 ΔX", "原始 ΔY", "改正 cX", "改正 cY", "改正 ΔX", "改正 ΔY"], rows,
  ));
  resultContent.append(details);
}

async function loadTraverse(id) {
  const data = await requestJson(`/traverses/${encodeURIComponent(id)}`);
  renderResult(data);
  return data;
}

async function loadHistory() {
  try {
    const traverses = await requestJson("/traverses");
    historyContent.replaceChildren();
    if (traverses.length === 0) {
      const empty = document.createElement("p");
      empty.className = "muted";
      empty.textContent = "尚無紀錄。送出第一圈觀測後會顯示在這裡。";
      historyContent.append(empty);
      return;
    }
    const list = document.createElement("div");
    list.className = "history-list";
    traverses.forEach((traverse) => {
      const button = document.createElement("button");
      button.type = "button";
      button.className = "history-item";
      button.setAttribute("aria-label", `查看 ${traverse.id} 的測量結果`);
      const identity = document.createElement("span");
      const heading = document.createElement("strong");
      heading.textContent = `${displayTime(traverse.createdAt)} · ${traverse.timeZone}`;
      const id = document.createElement("small");
      id.textContent = traverse.id;
      identity.append(heading, id);
      const length = document.createElement("span");
      length.className = "number";
      length.textContent = `邊長 ${decimal(traverse.totalLengthM, 3)} m`;
      const closure = document.createElement("span");
      closure.className = "number";
      closure.textContent = `閉合差 ${decimal(Math.hypot(traverse.closureDxM, traverse.closureDyM))} m`;
      const open = document.createElement("span");
      open.className = "open";
      open.textContent = "查看 →";
      button.append(identity, length, closure, open);
      button.addEventListener("click", async () => {
        try {
          await loadTraverse(traverse.id);
          document.querySelector("#result-title").scrollIntoView({ behavior: "smooth", block: "start" });
        } catch (error) {
          showMessage("error", readableError(error));
        }
      });
      list.append(button);
    });
    historyContent.append(list);
  } catch (error) {
    historyContent.textContent = `無法載入紀錄：${readableError(error)}`;
  }
}

form.addEventListener("submit", async (event) => {
  event.preventDefault();
  clearMessage();
  let request;
  try {
    request = readObservations();
  } catch (error) {
    showMessage("error", error.message);
    return;
  }

  submitButton.disabled = true;
  submitButton.textContent = "計算並儲存中…";
  try {
    const created = await requestJson("/traverses", {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify(request),
    });
    showMessage("success", `已儲存測量紀錄：${created.id}`);
    await loadTraverse(created.id);
    await loadHistory();
  } catch (error) {
    showMessage("error", readableError(error));
  } finally {
    submitButton.disabled = false;
    submitButton.textContent = "計算並儲存 →";
  }
});

document.querySelector("#add-leg-button").addEventListener("click", () => addLeg());
document.querySelector("#example-button").addEventListener("click", loadExample);
document.querySelector("#clear-button").addEventListener("click", resetForm);
document.querySelector("#refresh-button").addEventListener("click", loadHistory);

loadExample();
loadHistory();
