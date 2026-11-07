/* KANTA Command Palette — ⌘K / Ctrl+K spotlight search */
(function () {
  "use strict";
  var PAGES = [
    { title: "Home", url: "/", keywords: "home start landing", icon: "✦" },
    { title: "iPhone / iPad Download", url: "/ios-download", keywords: "ios iphone ipad ipa apple download install", icon: "" },
    { title: "Android Download", url: "/android", keywords: "android apk download install google", icon: "🤖" },
    { title: "How to Use (Guide)", url: "/guide", keywords: "guide how to use setup tutorial first run library songbook microphone remote", icon: "📘" },
    { title: "Features", url: "/features", keywords: "features comparison platform matrix lyrics queue stage pitch", icon: "⚡" },
    { title: "My Songbook", url: "/songbook", keywords: "songbook private catalog unlock encrypted reader", icon: "📖" },
    { title: "Help & Support", url: "/support", keywords: "help support faq troubleshooting contact", icon: "❓" },
    { title: "Privacy Policy", url: "/privacy", keywords: "privacy policy data", icon: "🔒" },
    { title: "Music Rights", url: "/legal", keywords: "legal music rights licensing", icon: "⚖️" },
    { title: "Open-Source Licenses", url: "/licenses", keywords: "licenses open source oss attribution", icon: "📜" },
    { title: "Guide: Your first song", url: "/guide#first-run", keywords: "first song starter morning light install", icon: "🎤" },
    { title: "Guide: Add your own music", url: "/guide#library", keywords: "import midi kar mp3 wav soundfont sf2 library", icon: "🎵" },
    { title: "Guide: Private Songbook", url: "/guide#songbook", keywords: "songbook export encrypted code unlock", icon: "🔐" },
    { title: "Guide: Microphone & voice", url: "/guide#soundcard", keywords: "microphone mic voice monitoring effects scoring pitch correction", icon: "🎙️" },
    { title: "Guide: Your Android stage", url: "/guide#personal-stage", keywords: "stage scenic photo video camera background fullscreen", icon: "🎬" },
    { title: "Guide: Remote control", url: "/guide#remote", keywords: "remote companion control pairing code host", icon: "📱" },
    { title: "Features: Pitch training", url: "/features#pitch-training", keywords: "pitch training practice key spice model", icon: "🎯" },
    { title: "Features: Party effects", url: "/features#party-sfx", keywords: "party effects applause cheering drum roll air horn", icon: "🎉" },
    { title: "Features: Platform comparison", url: "/features#comparison", keywords: "comparison android ios table availability", icon: "📊" },
    { title: "Downloads section", url: "/#downloads", keywords: "downloads builds releases platform", icon: "⬇️" }
  ];

  var open = false, overlay, panel, input, list, hint, items = [], activeIndex = 0;

  function build() {
    overlay = document.createElement("div");
    overlay.className = "cmdk-overlay";
    overlay.setAttribute("role", "dialog"); overlay.setAttribute("aria-modal", "true");
    overlay.setAttribute("aria-label", "Command palette");
    overlay.innerHTML =
      '<div class="cmdk-panel">' +
      '  <div class="cmdk-input-row">' +
      '    <span class="cmdk-icon" aria-hidden="true">✦</span>' +
      '    <input id="cmdk-input" type="text" placeholder="Search pages, features, guide steps…" autocomplete="off" spellcheck="false" aria-label="Search">' +
      '    <kbd class="cmdk-esc">esc</kbd>' +
      '  </div>' +
      '  <ul id="cmdk-list" role="listbox" aria-label="Results"></ul>' +
      '  <div class="cmdk-foot"><span><kbd>↑</kbd><kbd>↓</kbd> navigate</span><span><kbd>↵</kbd> open</span><span id="cmdk-count"></span></div>' +
      '</div>';
    document.body.appendChild(overlay);
    input = overlay.querySelector("#cmdk-input");
    list = overlay.querySelector("#cmdk-list");
    hint = overlay.querySelector("#cmdk-count");
    overlay.addEventListener("mousedown", function (e) { if (e.target === overlay) close(); });
    input.addEventListener("input", render);
    input.addEventListener("keydown", onKey);
  }

  function fuzzy(query, text) {
    query = query.toLowerCase(); text = text.toLowerCase();
    if (!query) return 1;
    if (text.indexOf(query) !== -1) return 2;
    var qi = 0;
    for (var i = 0; i < text.length && qi < query.length; i++) if (text[i] === query[qi]) qi++;
    return qi === query.length ? 0.5 : 0;
  }

  function render() {
    var q = input.value.trim();
    items = PAGES.map(function (p) { return { p: p, score: Math.max(fuzzy(q, p.title), fuzzy(q, p.keywords) * 0.9) }; })
      .filter(function (r) { return r.score > 0; }).sort(function (a, b) { return b.score - a.score; }).slice(0, 9);
    activeIndex = 0;
    list.innerHTML = "";
    items.forEach(function (r, i) {
      var li = document.createElement("li");
      li.setAttribute("role", "option"); li.setAttribute("aria-selected", String(i === activeIndex));
      li.className = "cmdk-item" + (i === activeIndex ? " active" : "");
      li.innerHTML = '<span class="cmdk-item-icon" aria-hidden="true">' + r.p.icon + '</span>' +
        '<span class="cmdk-item-title">' + r.p.title + '</span>' +
        '<span class="cmdk-item-url">' + r.p.url + '</span>';
      li.addEventListener("click", function () { go(r.p.url); });
      li.addEventListener("mousemove", function () { setActive(i); });
      list.appendChild(li);
    });
    hint.textContent = items.length + " result" + (items.length === 1 ? "" : "s");
  }

  function setActive(i) {
    activeIndex = i;
    list.querySelectorAll(".cmdk-item").forEach(function (el, idx) {
      el.classList.toggle("active", idx === i); el.setAttribute("aria-selected", String(idx === i));
    });
    var el = list.children[i];
    if (el && el.scrollIntoView) el.scrollIntoView({ block: "nearest" });
  }

  function onKey(e) {
    if (e.key === "ArrowDown") { e.preventDefault(); setActive(Math.min(items.length - 1, activeIndex + 1)); }
    else if (e.key === "ArrowUp") { e.preventDefault(); setActive(Math.max(0, activeIndex - 1)); }
    else if (e.key === "Enter") { e.preventDefault(); if (items[activeIndex]) go(items[activeIndex].p.url); }
    else if (e.key === "Escape") { e.preventDefault(); close(); }
  }

  function go(url) { close(); location.href = url; }

  function openPalette() {
    if (!overlay) build();
    open = true; overlay.classList.add("open"); document.body.style.overflow = "hidden";
    input.value = ""; render(); setTimeout(function () { input.focus(); }, 30);
  }
  function close() { if (!open) return; open = false; overlay.classList.remove("open"); document.body.style.overflow = ""; }

  document.addEventListener("keydown", function (e) {
    if ((e.metaKey || e.ctrlKey) && (e.key === "k" || e.key === "K")) { e.preventDefault(); open ? close() : openPalette(); }
    else if (e.key === "Escape" && open) close();
  });

  document.addEventListener("DOMContentLoaded", function () {
    if (window.matchMedia("(max-width: 768px)").matches) return;
    var fab = document.createElement("button");
    fab.className = "cmdk-fab"; fab.type = "button";
    fab.setAttribute("aria-label", "Open command palette");
    fab.innerHTML = '✦ <kbd>⌘K</kbd>';
    fab.addEventListener("click", openPalette);
    document.body.appendChild(fab);
  });
})();
