/* KANTA Theme Toggle — dark / light / system, persisted */
(function () {
  "use strict";
  var KEY = "kanta.theme";
  var root = document.documentElement;

  function stored() {
    try { return localStorage.getItem(KEY) || "system"; } catch (e) { return "system"; }
  }
  function apply(mode, animate) {
    if (animate) {
      root.classList.add("theme-transitioning");
      setTimeout(function () { root.classList.remove("theme-transitioning"); }, 350);
    }
    root.setAttribute("data-theme", mode);
    var meta = document.querySelector('meta[name="theme-color"]');
    if (meta) meta.setAttribute("content", mode === "light" ? "#f8f7f4" : "#080b14");
    document.querySelectorAll("[data-theme-toggle]").forEach(function (btn) {
      btn.setAttribute("aria-pressed", String(btn.dataset.themeToggle === mode));
      btn.classList.toggle("active", btn.dataset.themeToggle === mode);
    });
  }
  function save(mode) {
    try { localStorage.setItem(KEY, mode); } catch (e) {}
  }

  apply(stored(), false);

  var mq = window.matchMedia("(prefers-color-scheme: light)");
  var onOs = function () { if (stored() === "system") apply("system", true); };
  if (mq.addEventListener) mq.addEventListener("change", onOs);
  else if (mq.addListener) mq.addListener(onOs);

  document.addEventListener("DOMContentLoaded", function () {
    document.querySelectorAll("[data-theme-toggle]").forEach(function (btn) {
      btn.addEventListener("click", function () {
        var mode = btn.dataset.themeToggle;
        save(mode);
        apply(mode, true);
      });
    });
  });

  window.KantaTheme = { apply: apply, current: stored };
})();
