/* KANTA Hero — audio-reactive gradient canvas + animated stage mockup */
(function () {
  "use strict";
  var reduced = window.matchMedia("(prefers-reduced-motion: reduce)").matches;

  function initGradientCanvas() {
    var canvas = document.getElementById("hero-gradient");
    if (!canvas) return;
    var ctx = canvas.getContext("2d");
    var dpr = Math.min(2, window.devicePixelRatio || 1);
    var w, h, t = 0;

    function resize() {
      var rect = canvas.parentElement.getBoundingClientRect();
      w = rect.width; h = rect.height;
      canvas.width = w * dpr; canvas.height = h * dpr;
      canvas.style.width = w + "px"; canvas.style.height = h + "px";
      ctx.setTransform(dpr, 0, 0, dpr, 0, 0);
    }
    resize(); window.addEventListener("resize", resize);

    var bands = new Array(48).fill(0);
    function fakeAudio() {
      for (var i = 0; i < bands.length; i++) {
        var base = Math.sin(t * 0.9 + i * 0.35) * 0.5 + 0.5;
        var beat = Math.pow(Math.sin(t * 2.1), 8) * (i < 8 ? 1 : 0.3);
        bands[i] = Math.max(0, Math.min(1, base * 0.6 + beat * 0.5 + Math.random() * 0.08));
      }
    }

    function draw() {
      t += 0.016; fakeAudio();
      ctx.clearRect(0, 0, w, h);
      var avg = bands.reduce(function (a, b) { return a + b; }, 0) / bands.length;
      var blobs = [
        { x: w * (0.2 + 0.08 * Math.sin(t * 0.31)), y: h * (0.25 + 0.1 * Math.cos(t * 0.23)), r: 260 + avg * 120, c: "rgba(184,102,215,0.20)" },
        { x: w * (0.8 + 0.07 * Math.cos(t * 0.27)), y: h * (0.2 + 0.12 * Math.sin(t * 0.19)), r: 300 + bands[4] * 140, c: "rgba(27,61,122,0.22)" },
        { x: w * (0.5 + 0.1 * Math.sin(t * 0.17)), y: h * (0.85 + 0.06 * Math.cos(t * 0.29)), r: 340 + bands[20] * 120, c: "rgba(95,209,217,0.10)" },
        { x: w * (0.65 + 0.06 * Math.cos(t * 0.37)), y: h * (0.55 + 0.08 * Math.sin(t * 0.41)), r: 180 + bands[32] * 100, c: "rgba(247,201,102,0.08)" }
      ];
      blobs.forEach(function (b) {
        var g = ctx.createRadialGradient(b.x, b.y, 0, b.x, b.y, b.r);
        g.addColorStop(0, b.c); g.addColorStop(1, "rgba(0,0,0,0)");
        ctx.fillStyle = g; ctx.fillRect(0, 0, w, h);
      });
      ctx.beginPath(); var step = w / bands.length;
      ctx.moveTo(0, h);
      for (var i = 0; i < bands.length; i++) {
        var x = i * step; var y = h - 40 - bands[i] * 70; ctx.lineTo(x, y);
      }
      ctx.lineTo(w, h); ctx.closePath();
      var wg = ctx.createLinearGradient(0, h - 120, 0, h);
      wg.addColorStop(0, "rgba(184,102,215,0.16)"); wg.addColorStop(1, "rgba(95,209,217,0.02)");
      ctx.fillStyle = wg; ctx.fill();
      requestAnimationFrame(draw);
    }
    if (!reduced) requestAnimationFrame(draw); else { t = 3; fakeAudio(); draw(); }
  }

  var LYRICS = ["When the morning light breaks through…", "I will sing together with you…", "Starlight echo, carry me home…", "Every voice a flame, never alone…"];
  var SINGERS = [["Morning Light", "You"], ["Starlight Echo", "Alex"], ["Sing Together", "Sam"], ["Morning Light", "Ria"]];

  function initStageMockup() {
    var stage = document.querySelector("[data-mockup-stage]");
    if (!stage || reduced) return;
    var lineEl = stage.querySelector("[data-mockup-line]");
    var chipEl = stage.querySelector("[data-mockup-chip]");
    var queueEl = stage.querySelector("[data-mockup-queue]");
    var meterEl = stage.querySelector("[data-mockup-meter]");
    var idx = 0;

    function typeLine(text, done) {
      var i = 0; lineEl.textContent = "";
      (function tick() {
        if (i <= text.length) { lineEl.textContent = text.slice(0, i) + (i < text.length ? "▍" : ""); i++; setTimeout(tick, 45); }
        else { lineEl.textContent = text; setTimeout(done, 2600); }
      })();
    }

    function renderQueue(active) {
      if (!queueEl) return;
      queueEl.innerHTML = SINGERS.map(function (s, i) {
        var on = i === active;
        return '<div class="mq-row' + (on ? " on" : "") + '">' +
          '<span class="mq-n">' + (i + 1) + '</span>' +
          '<span class="mq-t">' + s[0] + '</span>' +
          '<span class="mq-s">' + s[1] + '</span>' +
          (on ? '<span class="mq-live">● LIVE</span>' : "") + "</div>";
      }).join("");
    }

    function meterTick() {
      if (!meterEl) return;
      var v = 0.35 + 0.5 * Math.abs(Math.sin(Date.now() / 700)) + Math.random() * 0.12;
      meterEl.style.setProperty("--level", Math.min(1, v).toFixed(2));
    }
    setInterval(meterTick, 120);

    function cycle() {
      renderQueue(idx % SINGERS.length);
      if (chipEl) chipEl.textContent = "Character-level timed lyrics active";
      typeLine(LYRICS[idx % LYRICS.length], function () { idx++; cycle(); });
    }
    cycle();
  }

  if (document.readyState === "loading") document.addEventListener("DOMContentLoaded", function () { initGradientCanvas(); initStageMockup(); });
  else { initGradientCanvas(); initStageMockup(); }
})();
