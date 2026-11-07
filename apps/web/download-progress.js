/* KANTA Download Progress — sticky top bar on /android + /ios-download */
(function () {
  "use strict";
  var CONFIG = {
    "/android": { label: "Android bundle", run: "42265183784", artifact: "13526817269", sha: "0d2f0c57aed6c1cbff62a15f8d0a1ec43e16035ca908270f8fb2eb9ab5a8d392" },
    "/ios-download": { label: "iOS bundle", run: "41474398118", artifact: "13157543816", sha: "95471bb58625150f6e691a5bd97aded81b8ab11850ae87d96288397b20f364bf" }
  };
  var path = location.pathname.replace(/\/+$/, "") || "/";
  var cfg = CONFIG[path];
  if (!cfg) return;
  var DISMISS_KEY = "kanta.dlbar.dismissed." + path;
  try { if (sessionStorage.getItem(DISMISS_KEY)) return; } catch (e) {}

  var bar = document.createElement("div");
  bar.className = "dl-bar";
  bar.setAttribute("role", "status");
  bar.innerHTML =
    '<div class="dl-bar-inner">' +
    '  <span class="dl-dot" aria-hidden="true"></span>' +
    '  <span class="dl-text">Checking ' + cfg.label + ' artifact…</span>' +
    '  <span class="dl-progress" aria-hidden="true"><i></i></span>' +
    '  <a class="dl-link" href="#download" hidden>Jump to download ↓</a>' +
    '  <button class="dl-close" type="button" aria-label="Dismiss download status">×</button>' +
    '</div>';
  document.body.appendChild(bar);

  var text = bar.querySelector(".dl-text"), dot = bar.querySelector(".dl-dot"), link = bar.querySelector(".dl-link"), prog = bar.querySelector(".dl-progress i");

  bar.querySelector(".dl-close").addEventListener("click", function () {
    bar.classList.add("gone"); setTimeout(function () { bar.remove(); }, 350);
    try { sessionStorage.setItem(DISMISS_KEY, "1"); } catch (e) {}
  });

  function setState(state, message) {
    bar.dataset.state = state; text.textContent = message;
    if (state === "ready") { link.hidden = false; prog.style.width = "100%"; }
  }

  var steps = [["fetch", "Locating verified CI artifact…", 900], ["verify", "Verifying SHA-256 provenance…", 1100], ["ready", cfg.label + " ready · GitHub sign-in required to fetch", 0]];
  var i = 0;
  (function next() { if (i >= steps.length) return; var s = steps[i++]; setState(s[0], s[1]); prog.style.width = (i / steps.length) * 100 + "%"; if (s[2]) setTimeout(next, s[2]); })();

  link.addEventListener("mouseenter", function () { if (bar.dataset.state === "ready") text.textContent = "SHA-256 " + cfg.sha.slice(0, 16) + "…"; });
  link.addEventListener("mouseleave", function () { if (bar.dataset.state === "ready") text.textContent = steps[steps.length - 1][1]; });
})();
