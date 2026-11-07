/* KANTA Micro-Interactions — ripple, magnetic buttons, 3D tilt, scroll reveals */
(function () {
  "use strict";
  var reduced = window.matchMedia("(prefers-reduced-motion: reduce)").matches;

  function ripple(e) {
    var el = e.currentTarget;
    var rect = el.getBoundingClientRect();
    el.style.setProperty("--ripple-x", ((e.clientX - rect.left) / rect.width) * 100 + "%");
    el.style.setProperty("--ripple-y", ((e.clientY - rect.top) / rect.height) * 100 + "%");
    el.classList.remove("rippling");
    void el.offsetWidth;
    el.classList.add("rippling");
    setTimeout(function () { el.classList.remove("rippling"); }, 600);
  }
  document.addEventListener("click", function (e) {
    var btn = e.target.closest(".button, .btn");
    if (btn && !reduced) ripple(e);
  }, true);

  function magnetize(el) {
    var strength = 0.18;
    el.addEventListener("mousemove", function (e) {
      if (reduced) return;
      var rect = el.getBoundingClientRect();
      var dx = e.clientX - (rect.left + rect.width / 2);
      var dy = e.clientY - (rect.top + rect.height / 2);
      el.style.transform = "translate(" + dx * strength + "px," + (dy * strength - 2) + "px)";
    });
    el.addEventListener("mouseleave", function () { el.style.transform = ""; });
  }
  document.querySelectorAll(".button.primary, .btn.primary, .nav-cta").forEach(magnetize);

  function tilt(card) {
    var max = 6;
    card.addEventListener("mousemove", function (e) {
      if (reduced) return;
      var rect = card.getBoundingClientRect();
      var px = (e.clientX - rect.left) / rect.width - 0.5;
      var py = (e.clientY - rect.top) / rect.height - 0.5;
      card.style.setProperty("--tilt-y", (px * max) + "deg");
      card.style.setProperty("--tilt-x", (-py * max) + "deg");
    });
    card.addEventListener("mouseleave", function () {
      card.style.setProperty("--tilt-x", "0deg");
      card.style.setProperty("--tilt-y", "0deg");
    });
  }
  document.querySelectorAll(".feature-card").forEach(tilt);

  function initReveals() {
    if (reduced || !("IntersectionObserver" in window)) {
      document.querySelectorAll(".reveal, .reveal-stagger").forEach(function (el) { el.classList.add("visible"); });
      return;
    }
    var io = new IntersectionObserver(function (entries) {
      entries.forEach(function (entry) {
        if (entry.isIntersecting) { entry.target.classList.add("visible"); io.unobserve(entry.target); }
      });
    }, { threshold: 0.12, rootMargin: "0px 0px -40px 0px" });
    document.querySelectorAll(".reveal, .reveal-stagger").forEach(function (el) { io.observe(el); });
  }

  function countUp(el) {
    var target = parseFloat(el.dataset.countTo || el.textContent);
    var suffix = el.dataset.countSuffix || "";
    var dur = 1200, start = null;
    function step(ts) {
      if (!start) start = ts;
      var p = Math.min(1, (ts - start) / dur);
      var eased = 1 - Math.pow(1 - p, 3);
      el.textContent = Math.round(target * eased) + suffix;
      if (p < 1) requestAnimationFrame(step);
    }
    requestAnimationFrame(step);
  }
  function initCounters() {
    if (reduced || !("IntersectionObserver" in window)) return;
    var io = new IntersectionObserver(function (entries) {
      entries.forEach(function (entry) {
        if (entry.isIntersecting) { countUp(entry.target); io.unobserve(entry.target); }
      });
    }, { threshold: 0.6 });
    document.querySelectorAll("[data-count-to]").forEach(function (el) { io.observe(el); });
  }

  if (document.readyState === "loading") {
    document.addEventListener("DOMContentLoaded", function () { initReveals(); initCounters(); });
  } else { initReveals(); initCounters(); }
})();
