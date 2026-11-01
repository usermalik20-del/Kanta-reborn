"use strict";
// The host owns every song and reservation; this client keeps only a bounded projection.
(function (root) {
  const encoder = new TextEncoder();
  function encodeRequest(id, action, argument = "", singer = "") {
    if (!/^[0-9a-f]{8}(-[0-9a-f]{4}){3}-[0-9a-f]{12}$/i.test(id)) throw Error("Invalid command identity");
    if (!["snapshot", "search", "reserve", "cancel", "play", "pause", "next", "stop"].includes(action)) throw Error("Unsupported command");
    if (action === "search" && !argument.trim()) throw Error("Enter a song code, title or artist");
    const fields = [action, argument, singer].map((value, i) => {
      if (typeof value !== "string" || value.length > [16, 128, 80][i]) throw Error("Input is too long");
      return encoder.encode(value);
    });
    const bytes = new Uint8Array(22 + fields.reduce((n, value) => n + value.length, 0));
    bytes.set(id.replaceAll("-", "").match(/../g).map(value => parseInt(value, 16)));
    let offset = 16;
    for (const field of fields) {
      new DataView(bytes.buffer).setUint16(offset, field.length); offset += 2;
      bytes.set(field, offset); offset += field.length;
    }
    return bytes;
  }
  function snapshot(value, request) {
    const text = (v, n) => typeof v === "string" && v.length <= n;
    if (!value || value.request.toLowerCase() !== request.toLowerCase() || value.version !== 1 ||
        value.source !== "local" || typeof value.playing !== "boolean" || !text(value.current, 128) ||
        !Array.isArray(value.songs) || value.songs.length > 10 || !Array.isArray(value.queue) || value.queue.length > 10 ||
        !Array.isArray(value.capabilities) || value.capabilities.length > 12 ||
        !value.capabilities.every(c => text(c, 16))) throw Error("Invalid host response");
    if (!value.songs.every(s => text(s.id, 128) && s.id && text(s.title, 128) && text(s.artist, 80) && text(s.code, 32)) ||
        !value.queue.every(s => text(s.id, 36) && /^[0-9a-f-]{36}$/i.test(s.id) && text(s.title, 128) && text(s.singer, 80)) ||
        new Set(value.songs.map(s => s.id)).size !== value.songs.length || new Set(value.queue.map(s => s.id)).size !== value.queue.length)
      throw Error("Invalid host metadata");
    return value;
  }
  const api = {encodeRequest, snapshot};
  if (typeof module !== "undefined") module.exports = api;
  if (!root.document) return;
  const $ = id => root.document.getElementById(id);
  if (!root.crypto?.randomUUID || !root.AbortController) {
    $("status").textContent = "This browser cannot run secure companion control. Update the browser or use a current KANTA app.";
    $("pair-form").querySelector("button").disabled = true;
    return;
  }
  let token = "", epoch = 0, busy = false, state = null, timer = null, active = null;
  const notice = message => { $("notice").textContent = message; };
  function controls() {
    for (const [id, action] of [["play-pause", state?.playing ? "pause" : "play"], ["next", "next"], ["stop", "stop"], ["lookup", "search"], ["refresh", "snapshot"]]) {
      $(id).disabled = busy || !token || (state !== null && !state.capabilities.includes(action));
    }
    for (const button of root.document.querySelectorAll("[data-command]")) button.disabled = busy || !token;
    $("play-pause").textContent = state?.playing ? "Pause" : "Play";
  }
  const nativePending = new Map();
  root.addEventListener("kanta-response", event => {
    const value = event.detail, pending = value && nativePending.get(value.id);
    if (pending) { nativePending.delete(value.id); pending(value); }
  });
  async function post(path, body, credential, signal) {
    if (root.KantaSecureTransport) {
      const id = crypto.randomUUID();
      const response = await new Promise((resolve, reject) => {
        if (signal?.aborted) { reject(Error("Cancelled")); return; }
        const cleanup = () => { nativePending.delete(id); reject(Error("Cancelled")); };
        signal?.addEventListener("abort", cleanup, {once: true});
        nativePending.set(id, value => {signal?.removeEventListener("abort", cleanup); resolve(value);});
        root.KantaSecureTransport.post(id, path, btoa(String.fromCharCode(...body)), credential);
      });
      if (response.status !== 200 || typeof response.body !== "string" || response.body.length > 16384) throw Error("Pinned host rejected request");
      return response.body;
    }
    const headers = {"Content-Type": "application/octet-stream"};
    if (credential) headers.Authorization = "Bearer " + credential;
    const response = await fetch(path, {method: "POST", headers, body, signal, credentials: "omit", cache: "no-store", redirect: "error"});
    if (!response.ok) throw Error(response.status === 401 ? "Session expired or revoked. Pair again." : "Host rejected the request. Check pairing and host availability.");
    const text = await response.text();
    if (text.length > 16384) throw Error("Host response limit exceeded");
    return text;
  }
  function disconnect(message = "Remote disconnected. Host playback continues.") {
    const previous = token;
    epoch++; token = ""; busy = false; state = null;
    active?.abort(); active = null; clearInterval(timer); timer = null;
    if (previous) { const closing = new AbortController(); setTimeout(() => closing.abort(), 2500); post("/leave", new Uint8Array(), previous, closing.signal).catch(() => {}); }
    $("controller").hidden = true; $("pairing").hidden = false; $("code").value = "";
    $("results").replaceChildren(); $("queue").replaceChildren(); $("status").textContent = "Not connected";
    notice(message); controls();
  }
  async function command(action, argument = "", singer = "") {
    if (!token || busy || root.document.hidden) return;
    const run = epoch, id = crypto.randomUUID();
    busy = true; controls(); const controller = new AbortController(); active = controller;
    const timeout = setTimeout(() => controller.abort(), 8000);
    try {
      const value = JSON.parse(await post("/command", encodeRequest(id, action, argument, singer), token, controller.signal));
      if (epoch !== run) return;
      if (value.error) { notice(String(value.error).slice(0, 160)); return; }
      const next = snapshot(value, id);
      if (action !== "search" && state) next.songs = state.songs;
      state = next; notice(""); render();
    } catch (_) {
      if (epoch === run) disconnect("Connection lost or response rejected. A command may have completed; pair again and inspect the host queue before repeating it.");
    } finally { clearTimeout(timeout); if (epoch === run) {busy = false; active = null; controls();} }
  }
  function coverNumber(value) {
    let hash = 0;
    for (const character of String(value || "")) hash = (Math.imul(hash, 31) + character.charCodeAt(0)) | 0;
    return String(((hash & 0x7fffffff) % 8) + 1).padStart(2, "0");
  }
  function row(title, detail, buttonText, action, coverKey) {
    const wrapper = root.document.createElement("div"); wrapper.className = "row";
    const cover = root.document.createElement("img");
    cover.className = "cover-art"; cover.alt = ""; cover.loading = "lazy"; cover.decoding = "async";
    cover.src = "/assets/covers/" + coverNumber(coverKey || title) + ".webp";
    const text = root.document.createElement("div"), strong = root.document.createElement("strong"), description = root.document.createElement("p");
    strong.textContent = title; description.textContent = detail; text.append(strong, description);
    const button = root.document.createElement("button"); button.type = "button"; button.textContent = buttonText;
    button.dataset.command = "true"; button.addEventListener("click", action); wrapper.append(cover, text, button); return wrapper;
  }
  function render() {
    $("current").textContent = state.current; $("transport-state").textContent = state.playing ? "Playing" : "Paused / stopped";
    $("now-art").src = "/assets/covers/" + coverNumber(state.current) + ".webp";
    $("results").replaceChildren(...state.songs.map(song => row(song.title, [song.artist, song.code].filter(Boolean).join(" · "), "Reserve", () => command("reserve", song.id, $("singer").value), song.id || song.title)));
    $("queue").replaceChildren(...state.queue.map(item => row(item.title, item.singer, "Cancel", () => command("cancel", item.id), item.title)));
    if (!state.queue.length) $("queue").textContent = "No queued songs";
    controls();
  }
  $("pair-form").addEventListener("submit", async event => {
    event.preventDefault(); if (busy || root.document.hidden) return;
    if (!$("verified").checked) {notice("Verify the host's certificate fingerprint before pairing."); return;}
    const code = $("code").value; $("code").value = "";
    if (!/^[a-f0-9]{32}$/i.test(code)) {notice("Enter the host's full 32-character pairing code."); return;}
    const run = ++epoch; busy = true; const button = event.target.querySelector("button"); button.disabled = true;
    const controller = new AbortController(); active = controller;
    const timeout = setTimeout(() => controller.abort(), 8000);
    try {
      const result = await post("/pair", encoder.encode(code.toLowerCase()), "", controller.signal);
      if (epoch !== run) return;
      if (!/^[a-f0-9]{64}$/.test(result)) throw Error("Invalid session");
      token = result; $("pairing").hidden = true; $("controller").hidden = false; $("status").textContent = "Connected · encrypted host";
      busy = false; notice(""); await command("snapshot");
      if (epoch === run && token) timer = setInterval(() => command("snapshot"), 10000);
    } catch (_) { if (epoch === run) disconnect("Pairing failed. Check the code, certificate and shared Wi-Fi. Restart hosting if pairing has expired or was locked."); }
    finally {clearTimeout(timeout); button.disabled = false; if (epoch === run) {busy = false; active = null; controls();}}
  });
  $("search-form").addEventListener("submit", event => {event.preventDefault(); const query = $("query").value.trim(); if (query) command("search", query); else notice("Enter a code, title or artist.");});
  for (const button of root.document.querySelectorAll("[data-digit]")) button.addEventListener("click", () => {if ($("query").value.length < 128) $("query").value += button.dataset.digit;});
  $("clear").addEventListener("click", () => {$("query").value = "";});
  $("backspace").addEventListener("click", () => {$("query").value = Array.from($("query").value).slice(0, -1).join("");});
  $("play-pause").addEventListener("click", () => command(state?.playing ? "pause" : "play"));
  $("next").addEventListener("click", () => command("next")); $("stop").addEventListener("click", () => command("stop"));
  $("refresh").addEventListener("click", () => command("snapshot")); $("disconnect").addEventListener("click", () => disconnect());
  $("queue-shortcut").addEventListener("click", () => {$("queue-card").scrollIntoView({block:"start"}); $("queue-card").focus();});
  root.document.addEventListener("visibilitychange", () => {if (root.document.hidden) disconnect("Remote closed when this page left the foreground. Pair again to continue.");});
  root.addEventListener("pagehide", () => disconnect()); controls();
})(globalThis);
