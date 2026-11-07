/* KANTA Stage Preview — fixed phone frame on /features that updates as you scroll sections */
(function () {
  "use strict";
  var reduced = window.matchMedia("(prefers-reduced-motion: reduce)").matches;
  if (!document.body.hasAttribute("data-stage-preview")) return;

  var SCENES = {
    "starter-songs": { label: "Three original songs", kind: "queue", lines: ["Morning Light", "Sing Together", "Starlight Echo"] },
    "player": { label: "Playback & lyrics", kind: "lyrics", lines: ["When the morning light", "breaks through…"] },
    "favorites": { label: "Saved sets", kind: "queue", lines: ["Friday night set · 8 songs", "Birthday set · 12 songs"] },
    "singers-playlists": { label: "Singer queue", kind: "queue", lines: ["1. You — Morning Light", "2. Alex — Starlight Echo", "3. Sam — Sing Together"] },
    "stage-display": { label: "Dedicated stage", kind: "stage", lines: ["NOW PERFORMING", "External display ready"] },
    "party-sfx": { label: "Party effects", kind: "pads", lines: ["Applause", "Cheering", "Drum Roll", "Air Horn", "Laughter", "Ta-Da", "Ding", "Sad Trombone"] },
    "intermission": { label: "Between-song music", kind: "stage", lines: ["INTERMISSION", "Your idle playlist"] },
    "pitch-training": { label: "Pitch training", kind: "meter", lines: ["Target: A4 · 440 Hz"] },
    "inputs": { label: "Microphone tools", kind: "meter", lines: ["Input level · Dry monitor"] },
    "android-stage-v2": { label: "Score reveal", kind: "score", lines: ["Great Singer", "92"] }
  };

  var frame = document.createElement("aside");
  frame.className = "stage-preview";
  frame.setAttribute("aria-hidden", "true");
  frame.innerHTML = '<div class="sp-phone"><div class="sp-notch"></div><div class="sp-screen"><div class="sp-label"></div><div class="sp-body"></div></div></div>';
  document.body.appendChild(frame);
  var labelEl = frame.querySelector(".sp-label"), bodyEl = frame.querySelector(".sp-body");
  var current = null, meterTimer = null;

  function stopMeter() { if (meterTimer) { clearInterval(meterTimer); meterTimer = null; } }

  function renderScene(key) {
    var scene = SCENES[key];
    if (!scene || key === current) return;
    current = key; stopMeter();
    labelEl.textContent = scene.label;
    bodyEl.className = "sp-body sp-" + scene.kind; bodyEl.innerHTML = "";
    if (scene.kind === "queue") { bodyEl.innerHTML = scene.lines.map(function (l, i) { return '<div class="sp-row' + (i === 0 ? " on" : "") + '">' + l + "</div>"; }).join(""); }
    else if (scene.kind === "lyrics") { bodyEl.innerHTML = '<div class="sp-lyric">' + scene.lines.join("<br>") + "</div>" + '<div class="sp-chip">character-level highlight</div>'; var lyric = bodyEl.querySelector(".sp-lyric"); var p = 0; (function sweep() { p = (p + 0.6) % 100; lyric.style.backgroundImage = "linear-gradient(90deg, rgba(247,201,102,.85) " + p + "%, rgba(255,255,255,.9) " + p + "%)"; lyric.style.webkitBackgroundClip = "text"; lyric.style.backgroundClip = "text"; lyric.style.color = "transparent"; requestAnimationFrame(sweep); })(); }
    else if (scene.kind === "stage") { bodyEl.innerHTML = '<div class="sp-stage-title">' + scene.lines[0] + "</div>" + '<div class="sp-stage-sub">' + scene.lines[1] + "</div>" + '<div class="sp-stage-bars">' + new Array(24).fill('<i></i>').join("") + "</div>"; var bars = bodyEl.querySelectorAll(".sp-stage-bars i"); (function dance() { bars.forEach(function (b, i) { b.style.height = (18 + Math.abs(Math.sin(Date.now() / 300 + i * 0.5)) * 46) + "px"; }); requestAnimationFrame(dance); })(); }
    else if (scene.kind === "pads") { bodyEl.innerHTML = '<div class="sp-pads">' + scene.lines.map(function (l) { return '<span class="sp-pad">' + l + "</span>"; }).join("") + "</div>"; var pads = bodyEl.querySelectorAll(".sp-pad"), pi = 0; meterTimer = setInterval(function () { pads.forEach(function (p, i) { p.classList.toggle("hit", i === pi % pads.length); }); pi++; }, 420); }
    else if (scene.kind === "meter") { bodyEl.innerHTML = '<div class="sp-meter-label">' + scene.lines[0] + "</div>" + '<div class="sp-meter"><i class="sp-needle"></i><span class="sp-target"></span></div>' + '<div class="sp-meter-scale"><b>♭</b><b>●</b><b>♯</b></div>'; var needle = bodyEl.querySelector(".sp-needle"); meterTimer = setInterval(function () { var v = Math.sin(Date.now() / 500) * 34 + Math.random() * 6 - 3; needle.style.transform = "translateX(" + v + "px)"; }, 90); }
    else if (scene.kind === "score") { bodyEl.innerHTML = '<div class="sp-score-tier">' + scene.lines[0] + "</div>" + '<div class="sp-score-num" data-to="' + scene.lines[1] + '">0</div>'; var num = bodyEl.querySelector(".sp-score-num"); var target = parseInt(scene.lines[1], 10); var v = 0; meterTimer = setInterval(function () { v += 2; if (v >= target) { v = target; stopMeter(); } num.textContent = v; }, 28); }
    frame.classList.remove("swap"); void frame.offsetWidth; frame.classList.add("swap");
  }

  function init() {
    var sections = document.querySelectorAll("main .section[id]");
    if (!("IntersectionObserver" in window) || reduced) { renderScene("player"); return; }
    var io = new IntersectionObserver(function (entries) { entries.forEach(function (e) { if (e.isIntersecting && SCENES[e.target.id]) renderScene(e.target.id); }); }, { threshold: 0.35 });
    sections.forEach(function (s) { if (SCENES[s.id]) io.observe(s); }); renderScene("starter-songs");
  }

  if (document.readyState === "loading") document.addEventListener("DOMContentLoaded", init); else init();
})();
