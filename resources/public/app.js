const state = {
  events: [],
  filters: { source: "", level: "", type: "", limit: 100 },
};

const $ = (id) => document.getElementById(id);

function escapeHtml(value) {
  return String(value ?? "")
    .replaceAll("&", "&amp;")
    .replaceAll("<", "&lt;")
    .replaceAll(">", "&gt;")
    .replaceAll('"', "&quot;")
    .replaceAll("'", "&#039;");
}

function levelClass(level) {
  return `level level-${escapeHtml(level)}`;
}

function shortTime(value) {
  if (!value) return "-";
  const date = new Date(value);
  if (Number.isNaN(date.getTime())) return value;
  return date.toLocaleString();
}

function setError(message) {
  const box = $("errorBox");
  if (!message) {
    box.classList.add("hidden");
    box.textContent = "";
    return;
  }
  box.textContent = message;
  box.classList.remove("hidden");
}

function queryString() {
  const params = new URLSearchParams();
  Object.entries(state.filters).forEach(([key, value]) => {
    if (value !== "" && value !== null && value !== undefined) params.set(key, value);
  });
  return params.toString();
}

function renderEvents() {
  const body = $("eventsBody");
  $("eventCountLabel").textContent = `${state.events.length} events`;
  if (!state.events.length) {
    body.innerHTML = '<tr><td colspan="6" class="empty">No events match the current filters.</td></tr>';
    return;
  }
  body.innerHTML = state.events.map((event) => {
    const metadata = JSON.stringify(event.metadata ?? {}, null, 2);
    return `<tr>
      <td>${escapeHtml(shortTime(event.received_at))}</td>
      <td><span class="${levelClass(event.level)}">${escapeHtml(event.level)}</span></td>
      <td>${escapeHtml(event.source)}</td>
      <td>${escapeHtml(event.type)}</td>
      <td>${escapeHtml(event.message)}</td>
      <td><pre>${escapeHtml(metadata)}</pre></td>
    </tr>`;
  }).join("");
}

function renderList(id, rows, emptyText) {
  const node = $(id);
  if (!rows || !rows.length) {
    node.innerHTML = `<p class="empty small">${emptyText}</p>`;
    return;
  }
  node.innerHTML = rows.map((row) =>
    `<div class="metric-row"><span>${escapeHtml(row.name)}</span><strong>${escapeHtml(row.count)}</strong></div>`
  ).join("");
}

function renderLevels(rows) {
  const node = $("levelBreakdown");
  if (!rows || !rows.length) {
    node.innerHTML = '<p class="empty small">No level data yet.</p>';
    return;
  }
  const max = Math.max(...rows.map((row) => row.count), 1);
  node.innerHTML = rows.map((row) => {
    const width = Math.max(4, Math.round((row.count / max) * 100));
    return `<div class="bar"><div class="bar-head"><span>${escapeHtml(row.name)}</span><strong>${escapeHtml(row.count)}</strong></div><i style="width:${width}%"></i></div>`;
  }).join("");
}

function countLevel(rows, level) {
  const found = (rows || []).find((row) => row.name === level);
  return found ? found.count : 0;
}

async function loadEvents() {
  const res = await fetch(`/api/events?${queryString()}`);
  if (!res.ok) throw new Error(`Events request failed with ${res.status}`);
  const data = await res.json();
  state.events = data.events || [];
  renderEvents();
}

async function loadStats() {
  const res = await fetch("/api/events/stats");
  if (!res.ok) throw new Error(`Stats request failed with ${res.status}`);
  const stats = await res.json();
  $("statTotal").textContent = stats.total ?? 0;
  $("stat5").textContent = stats.last_5_minutes ?? 0;
  $("stat15").textContent = stats.last_15_minutes ?? 0;
  $("stat60").textContent = stats.last_60_minutes ?? 0;
  $("statWarnings").textContent = countLevel(stats.by_level, "warning");
  $("statErrors").textContent = countLevel(stats.by_level, "error") + countLevel(stats.by_level, "critical");
  $("statSources").textContent = (stats.top_sources || []).length;
  $("statLast").textContent = shortTime(stats.last_event_at);
  renderLevels(stats.by_level);
  renderList("topSources", stats.top_sources, "No sources yet.");
  renderList("topTypes", stats.top_types, "No types yet.");
  $("lastRefresh").textContent = `Updated ${new Date().toLocaleTimeString()}`;
}

async function refreshAll() {
  try {
    setError("");
    await Promise.all([loadEvents(), loadStats()]);
  } catch (error) {
    setError(error.message);
  }
}

function readFilters() {
  state.filters = {
    source: $("filterSource").value.trim(),
    level: $("filterLevel").value,
    type: $("filterType").value.trim(),
    limit: Math.min(Math.max(Number($("filterLimit").value || 100), 1), 500),
  };
}

function setupFilters() {
  $("applyFilters").addEventListener("click", () => {
    readFilters();
    refreshAll();
  });
  $("clearFilters").addEventListener("click", () => {
    $("filterSource").value = "";
    $("filterLevel").value = "";
    $("filterType").value = "";
    $("filterLimit").value = 100;
    readFilters();
    refreshAll();
  });
}

function setupStream() {
  const status = $("streamStatus");
  if (!window.EventSource) {
    status.textContent = "Polling";
    status.className = "pill warning";
    setInterval(refreshAll, 5000);
    return;
  }
  const source = new EventSource("/api/events/stream");
  source.onopen = () => {
    status.textContent = "Live";
    status.className = "pill live";
  };
  source.onerror = () => {
    status.textContent = "Reconnecting";
    status.className = "pill warning";
  };
  source.onmessage = (message) => {
    try {
      const event = JSON.parse(message.data);
      if (event.event === "created") refreshAll();
    } catch (_) {
      refreshAll();
    }
  };
}

document.addEventListener("DOMContentLoaded", () => {
  setupFilters();
  refreshAll();
  setupStream();
});
