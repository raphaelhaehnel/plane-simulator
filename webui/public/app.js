/*
 * Generic renderer: builds the nav, the forms, and the response panel entirely
 * from the ENDPOINTS catalog in endpoints.js. To extend the UI, edit that file
 * — this one only needs changes for new *kinds* of behavior (e.g. a new input
 * kind), not for new endpoints or fields.
 */
import { ENDPOINTS } from "./endpoints.js";

const nav = document.getElementById("nav");
const formPanel = document.getElementById("form-panel");
const responsePanel = document.getElementById("response-panel");

/** Field values entered so far, per endpoint id, so switching tabs keeps input. */
const savedValues = {};
/** Scenario ids seen in any response, newest first. */
let knownIds = [];

let current = ENDPOINTS[0];

function init() {
  for (const ep of ENDPOINTS) {
    const btn = document.createElement("button");
    btn.className = "nav-item";
    btn.dataset.endpoint = ep.id;
    btn.innerHTML = `<span class="method method-${ep.method.toLowerCase()}">${ep.method}</span> ${ep.title}`;
    btn.addEventListener("click", () => select(ep));
    nav.appendChild(btn);
  }
  select(current);
}

function select(ep) {
  current = ep;
  for (const btn of nav.querySelectorAll(".nav-item")) {
    btn.classList.toggle("active", btn.dataset.endpoint === ep.id);
  }
  renderForm();
}

function values() {
  return savedValues[current.id] || (savedValues[current.id] = defaults(current));
}

function defaults(ep) {
  const v = {};
  for (const f of ep.fields) {
    v[f.name] = f.value !== undefined ? String(f.value) : "";
  }
  return v;
}

function renderForm() {
  const ep = current;
  const v = values();
  formPanel.innerHTML = "";

  const header = document.createElement("div");
  header.className = "form-header";
  header.innerHTML = `
    <h2><span class="method method-${ep.method.toLowerCase()}">${ep.method}</span> ${ep.title}</h2>
    <code class="path">${ep.path}</code>
    <p class="description">${ep.description}</p>`;
  formPanel.appendChild(header);

  const form = document.createElement("form");
  form.addEventListener("submit", (e) => {
    e.preventDefault();
    send();
  });

  for (const field of ep.fields) {
    if (field.showIf && !field.showIf(v)) continue;

    const row = document.createElement("label");
    row.className = "field";

    const caption = document.createElement("span");
    caption.className = "field-label";
    caption.textContent = field.label + (field.required ? " *" : "");
    row.appendChild(caption);

    row.appendChild(makeInput(field, v));

    if (field.hint) {
      const hint = document.createElement("span");
      hint.className = "hint";
      hint.textContent = field.hint;
      row.appendChild(hint);
    }
    form.appendChild(row);
  }

  const actions = document.createElement("div");
  actions.className = "actions";
  const sendBtn = document.createElement("button");
  sendBtn.type = "submit";
  sendBtn.className = "send";
  sendBtn.textContent = "Send request";
  actions.appendChild(sendBtn);
  form.appendChild(actions);

  formPanel.appendChild(form);
}

function makeInput(field, v) {
  let input;
  if (field.kind === "select") {
    input = document.createElement("select");
    for (const opt of field.options) {
      const o = document.createElement("option");
      o.value = o.textContent = opt;
      input.appendChild(o);
    }
    input.value = v[field.name] || field.options[0];
  } else if (field.kind === "checkbox") {
    input = document.createElement("input");
    input.type = "checkbox";
    input.checked = v[field.name] === "true";
  } else {
    input = document.createElement("input");
    input.type = "text";
    if (field.kind === "number") input.inputMode = "decimal";
    input.value = v[field.name] || "";
    input.placeholder = field.placeholder || "";
  }
  input.name = field.name;
  input.addEventListener("input", () => {
    v[field.name] = field.kind === "checkbox" ? String(input.checked) : input.value;
    // A changed value can toggle other fields' visibility (showIf) — re-render.
    if (field.kind === "select" || field.kind === "checkbox") renderForm();
  });
  return input;
}

/** Builds the JSON body from visible fields; dot names create nested objects. */
function buildBody(ep, v) {
  const body = {};
  for (const field of ep.fields) {
    if (field.showIf && !field.showIf(v)) continue;

    const raw = v[field.name] ?? "";
    if (raw === "") {
      if (field.required) throw new Error(`"${field.label}" is required`);
      continue; // blank optional field: omit from the body
    }

    let value = raw;
    if (field.kind === "number") {
      value = Number(raw);
      if (Number.isNaN(value)) throw new Error(`"${field.label}" must be a number`);
    } else if (field.kind === "checkbox") {
      value = raw === "true";
    }

    const keys = field.name.split(".");
    let target = body;
    for (const key of keys.slice(0, -1)) {
      target = target[key] ?? (target[key] = {});
    }
    target[keys.at(-1)] = value;
  }
  return body;
}

async function send() {
  const ep = current;
  let options = { method: ep.method };
  if (ep.method !== "GET") {
    let body;
    try {
      body = buildBody(ep, values());
    } catch (e) {
      showResult({ error: e.message });
      return;
    }
    options.headers = { "Content-Type": "application/json" };
    options.body = JSON.stringify(body);
  }

  const started = performance.now();
  try {
    const res = await fetch("/api" + ep.path, options);
    const text = await res.text();
    let parsed;
    try {
      parsed = JSON.parse(text);
    } catch {
      parsed = text;
    }
    collectIds(parsed);
    showResult({ status: res.status, ok: res.ok, ms: performance.now() - started, data: parsed });
  } catch (e) {
    showResult({ error: "Request failed: " + e.message });
  }
}

function showResult(result) {
  responsePanel.innerHTML = "";

  const status = document.createElement("div");
  status.className = "response-status";
  if (result.error) {
    status.innerHTML = `<span class="badge badge-error">Error</span> ${escapeHtml(result.error)}`;
  } else {
    const cls = result.ok ? "badge-ok" : "badge-error";
    status.innerHTML = `<span class="badge ${cls}">${result.status}</span> <span class="ms">${result.ms.toFixed(0)} ms</span>`;
  }
  responsePanel.appendChild(status);

  if (result.data !== undefined) {
    const pre = document.createElement("pre");
    pre.textContent = typeof result.data === "string" ? result.data : JSON.stringify(result.data, null, 2);
    responsePanel.appendChild(pre);
  }

  renderKnownIds();
}

/** Remembers every "id" string found in a response, for one-click reuse. */
function collectIds(node) {
  if (Array.isArray(node)) {
    node.forEach(collectIds);
  } else if (node && typeof node === "object") {
    if (typeof node.id === "string") {
      knownIds = [node.id, ...knownIds.filter((i) => i !== node.id)].slice(0, 10);
    }
    Object.values(node).forEach(collectIds);
  }
}

function renderKnownIds() {
  if (knownIds.length === 0) return;
  const box = document.createElement("div");
  box.className = "known-ids";
  box.innerHTML = `<span class="known-ids-title">Scenario ids seen (click to fill the id field):</span>`;
  for (const id of knownIds) {
    const chip = document.createElement("button");
    chip.className = "chip";
    chip.textContent = id;
    chip.title = "Fill the current form's id field";
    chip.addEventListener("click", () => {
      if (current.fields.some((f) => f.name === "id")) {
        values()["id"] = id;
        renderForm();
      } else {
        navigator.clipboard?.writeText(id);
      }
    });
    box.appendChild(chip);
  }
  responsePanel.appendChild(box);
}

function escapeHtml(s) {
  return s.replace(/[&<>"']/g, (c) => `&#${c.charCodeAt(0)};`);
}

init();
