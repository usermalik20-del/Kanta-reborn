/* KANTA Songbook FX — 3D page flip, particle dust, state transitions */
(function () {
  "use strict";
  var reduced = window.matchMedia("(prefers-reduced-motion: reduce)").matches;
  var main = document.querySelector(".book-main");
  if (!main) return;

  function initParticles() {
    if (reduced) return;
    var canvas = document.createElement("canvas");
    canvas.className = "book-particles"; canvas.setAttribute("aria-hidden", "true");
    var card = document.querySelector(".book-card, #unlock, .physical-book");
    if (!card) return; card.style.position = "relative"; card.appendChild(canvas);
    var ctx = canvas.getContext("2d"); var w, h;
    function resize() { w = canvas.width = card.offsetWidth; h = canvas.height = card.offsetHeight; }
    resize(); window.addEventListener("resize", resize);
    var pts = new Array(40).fill(0).map(function () { return { x: Math.random() * w, y: Math.random() * h, vx: (Math.random() - 0.5) * 0.12, vy: (Math.random() - 0.5) * 0.08, r: Math.random() * 1.2 + 0.4, a: Math.random() * 0.4 + 0.1, hue: [184, 247, 95][Math.floor(Math.random() * 3)] }; });
    function draw() { ctx.clearRect(0, 0, w, h); pts.forEach(function (p) { p.x += p.vx; p.y += p.vy; if (p.x < 0 || p.x > w) p.vx *= -1; if (p.y < 0 || p.y > h) p.vy *= -1; ctx.beginPath(); ctx.arc(p.x, p.y, p.r, 0, Math.PI * 2); ctx.fillStyle = "hsla(" + p.hue + ", 85%, 65%, " + p.a + ")"; ctx.fill(); }); requestAnimationFrame(draw); }
    requestAnimationFrame(draw);
  }

  function initPageFlip() {
    if (reduced) return;
    var book = document.querySelector(".physical-book");
    var left = book?.querySelector(".page-left");
    var right = book?.querySelector(".page-right");
    var prev = document.getElementById("prev");
    var next = document.getElementById("next");
    if (!book || !left || !right) return;
    var flipping = false;
    function flip(dir) {
      if (flipping) return; flipping = true;
      var page = dir === "next" ? right : left;
      page.style.transition = "transform 0.7s cubic-bezier(.4,0,.2,1)";
      page.style.transformOrigin = dir === "next" ? "right center" : "left center";
      page.style.transform = dir === "next" ? "rotateY(-160deg)" : "rotateY(160deg)";
      page.style.boxShadow = dir === "next" ? "40px 0 80px rgba(0,0,0,0.6)" : "-40px 0 80px rgba(0,0,0,0.6)";
      setTimeout(function () {
        page.style.transition = "none"; page.style.transform = dir === "next" ? "rotateY(180deg)" : "rotateY(-180deg)";
        var leftSongs = left.querySelector("#songs-left"), rightSongs = right.querySelector("#songs-right");
        if (leftSongs && rightSongs) { var tmp = leftSongs.innerHTML; leftSongs.innerHTML = rightSongs.innerHTML; rightSongs.innerHTML = tmp; }
        page.style.transform = dir === "next" ? "rotateY(0deg)" : "rotateY(0deg)";
        page.style.boxShadow = "";
        requestAnimationFrame(function () { page.style.transition = "transform 0.7s cubic-bezier(.4,0,.2,1)"; setTimeout(function () { flipping = false; page.style.transition = ""; }, 700); });
      }, 700);
    }
    prev?.addEventListener("click", function () { flip("prev"); });
    next?.addEventListener("click", function () { flip("next"); });
    document.addEventListener("keydown", function (e) { if (e.target.tagName === "INPUT") return; if (e.key === "ArrowLeft") flip("prev"); else if (e.key === "ArrowRight") flip("next"); });
  }

  function initSearchHighlight() {
    var search = document.getElementById("search");
    if (!search) return;
    search.addEventListener("input", function () {
      var q = this.value.toLowerCase().trim();
      document.querySelectorAll(".song-list li").forEach(function (li) {
        var text = li.textContent.toLowerCase();
        var match = q && text.includes(q);
        li.classList.toggle("highlight", match);
      });
      var matches = document.querySelector("#matches");
      if (matches) { var count = document.querySelectorAll(".song-list li.highlight").length; matches.textContent = q ? count + " match" + (count === 1 ? "" : "es") : ""; }
    });
  }

  function initLockState() {
    var unlock = document.getElementById("unlock"), reader = document.getElementById("reader");
    var btn = document.getElementById("unlock-btn"), lockBtn = document.getElementById("lock");
    if (!unlock || !reader || !btn) return;
    btn.addEventListener("click", function () {
      unlock.style.transition = "opacity 0.3s ease, transform 0.3s ease"; unlock.style.opacity = "0"; unlock.style.transform = "translateY(-10px)";
      setTimeout(function () { unlock.hidden = true; reader.hidden = false; reader.style.opacity = "0"; reader.style.transform = "translateY(10px)"; requestAnimationFrame(function () { reader.style.transition = "opacity 0.4s ease, transform 0.4s ease"; reader.style.opacity = "1"; reader.style.transform = "translateY(0)"; }); }, 300);
    });
    lockBtn?.addEventListener("click", function () {
      reader.style.transition = "opacity 0.3s ease, transform 0.3s ease"; reader.style.opacity = "0"; reader.style.transform = "translateY(10px)";
      setTimeout(function () { reader.hidden = true; unlock.hidden = false; unlock.style.opacity = "0"; unlock.style.transform = "translateY(-10px)"; requestAnimationFrame(function () { unlock.style.transition = "opacity 0.4s ease, transform 0.4s ease"; unlock.style.opacity = "1"; unlock.style.transform = "translateY(0)"; }); }, 300);
    });
  }

  if (document.readyState === "loading") document.addEventListener("DOMContentLoaded", function () { initParticles(); initPageFlip(); initSearchHighlight(); initLockState(); });
  else { initParticles(); initPageFlip(); initSearchHighlight(); initLockState(); }
})();
