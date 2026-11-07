/* KANTA Guide Parallax — pinned phone illustration + palette shift per step */
(function () {
  "use strict";
  var reduced = window.matchMedia("(prefers-reduced-motion: reduce)").matches;
  if (!document.body.hasAttribute("data-guide-parallax")) return;

  var STEPS = [
    { id: "first-run", hue: "#b866d7", screen: "home", title: "Open KANTA" },
    { id: "library", hue: "#5fd1d9", screen: "import", title: "Import your music" },
    { id: "songbook", hue: "#f7c968", screen: "book", title: "Private Songbook" },
    { id: "soundcard", hue: "#f2a5e8", screen: "mic", title: "Mic & voice" },
    { id: "personal-stage", hue: "#6d4fd0", screen: "stage", title: "Your stage" },
    { id: "remote", hue: "#5fd1d9", screen: "remote", title: "Companion control" }
  ];

  var wrap = document.createElement("div");
  wrap.className = "guide-rail";
  wrap.setAttribute("aria-hidden", "true");
  wrap.innerHTML = '<div class="gr-phone"><div class="gr-notch"></div><div class="gr-screen" id="gr-screen"></div><div class="gr-step"><b id="gr-num">1</b><span id="gr-title"></span></div></div>';
  document.body.appendChild(wrap);
  var screenEl = wrap.querySelector("#gr-screen"), numEl = wrap.querySelector("#gr-num"), titleEl = wrap.querySelector("#gr-title");

  var SCREENS = {
    home: '<div class="grs-home"><div class="grs-logo">✦</div><div class="grs-name">KANTA</div><div class="grs-cards"><i></i><i></i><i></i></div></div>',
    import: '<div class="grs-import"><div class="grs-bar"><i style="width:0%"></i></div><div class="grs-files"><span>song.mid</span><span>bank.sf2</span><span>track.mp3</span></div></div>',
    book: '<div class="grs-book"><div class="grs-page l"><i></i><i></i><i></i></div><div class="grs-page r"><i></i><i></i><i></i></div></div>',
    mic: '<div class="grs-mic"><div class="grs-wave">' + new Array(28).fill("<i></i>").join("") + '</div><div class="grs-db">-12 dB</div></div>',
    stage: '<div class="grs-stage"><div class="grs-lyric">sing it loud…</div><div class="grs-bg"></div></div>',
    remote: '<div class="grs-remote"><div class="grs-code">A1B2-C3D4</div><div class="grs-btns"><i></i><i></i><i></i></div></div>'
  };

  var activeIdx = -1;
  function setStep(i) {
    if (i === activeIdx) return;
    activeIdx = i; var s = STEPS[i];
    document.documentElement.style.setProperty("--guide-hue", s.hue);
    screenEl.innerHTML = SCREENS[s.screen];
    numEl.textContent = String(i + 1); titleEl.textContent = s.title;
    wrap.classList.remove("pulse"); void wrap.offsetWidth; wrap.classList.add("pulse");
    if (s.screen === "import") { var bar = screenEl.querySelector(".grs-bar i"), p = 0; var t = setInterval(function () { p = (p + 1.4) % 100; if (bar) bar.style.width = p + "%"; }, 40); setTimeout(function () { clearInterval(t); }, 4000); }
    if (s.screen === "mic") { var waves = screenEl.querySelectorAll(".grs-wave i"); (function dance() { waves.forEach(function (w, k) { w.style.height = (6 + Math.abs(Math.sin(Date.now() / 220 + k * 0.6)) * 30) + "px"; }); if (activeIdx === i) requestAnimationFrame(dance); })(); }
  }

  function init() {
    var sections = STEPS.map(function (s) { return document.getElementById(s.id); }).filter(Boolean);
    if (!sections.length) return;
    if (reduced || !("IntersectionObserver" in window)) { setStep(0); return; }
    var io = new IntersectionObserver(function (entries) { entries.forEach(function (e) { if (e.isIntersecting) { var idx = STEPS.findIndex(function (s) { return s.id === e.target.id; }); if (idx >= 0) setStep(idx); } }); }, { threshold: 0.4 });
    sections.forEach(function (s) { io.observe(s); }); setStep(0);
  }

  if (document.readyState === "loading") document.addEventListener("DOMContentLoaded", init); else init();
})();
