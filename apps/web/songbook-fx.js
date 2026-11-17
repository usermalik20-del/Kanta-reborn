/* KANTA Songbook FX — decorative particle dust only.
 * Reader state, page navigation and accessibility keys belong to songbook.js.
 */
(function () {
  "use strict";
  var reduced = window.matchMedia("(prefers-reduced-motion: reduce)").matches;
  var main = document.querySelector(".book-main");
  if (!main || reduced) return;

  var canvas = document.createElement("canvas");
  canvas.className = "book-particles";
  canvas.setAttribute("aria-hidden", "true");
  var card = document.querySelector(".book-card");
  if (!card) return;
  card.style.position = "relative";
  card.appendChild(canvas);
  var context = canvas.getContext("2d");
  if (!context) { canvas.remove(); return; }

  var width = 0, height = 0;
  function resize() {
    width = canvas.width = card.offsetWidth;
    height = canvas.height = card.offsetHeight;
  }
  resize();
  window.addEventListener("resize", resize, { passive: true });
  var particles = new Array(40).fill(0).map(function () {
    return {
      x: Math.random() * width, y: Math.random() * height,
      vx: (Math.random() - 0.5) * 0.12, vy: (Math.random() - 0.5) * 0.08,
      radius: Math.random() * 1.2 + 0.4, opacity: Math.random() * 0.4 + 0.1,
      hue: [184, 247, 95][Math.floor(Math.random() * 3)]
    };
  });
  function draw() {
    if (document.visibilityState === "visible") {
      context.clearRect(0, 0, width, height);
      particles.forEach(function (particle) {
        particle.x += particle.vx; particle.y += particle.vy;
        if (particle.x < 0 || particle.x > width) particle.vx *= -1;
        if (particle.y < 0 || particle.y > height) particle.vy *= -1;
        context.beginPath();
        context.arc(particle.x, particle.y, particle.radius, 0, Math.PI * 2);
        context.fillStyle = "hsla(" + particle.hue + ", 85%, 65%, " + particle.opacity + ")";
        context.fill();
      });
    }
    requestAnimationFrame(draw);
  }
  requestAnimationFrame(draw);
})();