/* KANTA Download Status — manifest-backed sticky bar on /android + /ios-download */
(function () {
  "use strict";

  var CONFIG = {
    "/android": {
      label: "Android bundle",
      platform: "android",
      artifactName: "KANTA-Android-preview-download",
      digestKey: "apkSha256",
      digestLabel: "APK"
    },
    "/ios-download": {
      label: "iOS bundle",
      platform: "ios",
      artifactName: "KANTA-iOS-personal-unsigned-download",
      digestKey: "ipaSha256",
      digestLabel: "IPA"
    }
  };

  var path = location.pathname.replace(/\/+$/, "") || "/";
  var cfg = CONFIG[path];
  if (!cfg || !document.body) return;

  var dismissKey = "kanta.dlbar.dismissed." + path;
  try {
    if (sessionStorage.getItem(dismissKey)) return;
  } catch (error) {}

  var bar = document.createElement("div");
  bar.className = "dl-bar";
  bar.setAttribute("role", "status");
  bar.setAttribute("aria-live", "polite");

  var inner = document.createElement("div");
  inner.className = "dl-bar-inner";

  var dot = document.createElement("span");
  dot.className = "dl-dot";
  dot.setAttribute("aria-hidden", "true");

  var text = document.createElement("span");
  text.className = "dl-text";
  text.textContent = "Checking published " + cfg.label + " record…";

  var progress = document.createElement("span");
  progress.className = "dl-progress";
  progress.setAttribute("aria-hidden", "true");
  var fill = document.createElement("i");
  progress.appendChild(fill);

  var link = document.createElement("a");
  link.className = "dl-link";
  link.href = "#download";
  link.hidden = true;
  link.textContent = "View download details ↓";

  var close = document.createElement("button");
  close.className = "dl-close";
  close.type = "button";
  close.setAttribute("aria-label", "Dismiss download status");
  close.textContent = "×";

  inner.appendChild(dot);
  inner.appendChild(text);
  inner.appendChild(progress);
  inner.appendChild(link);
  inner.appendChild(close);
  bar.appendChild(inner);
  document.body.appendChild(bar);

  close.addEventListener("click", function () {
    bar.classList.add("gone");
    window.setTimeout(function () {
      bar.remove();
    }, 350);
    try {
      sessionStorage.setItem(dismissKey, "1");
    } catch (error) {}
  });

  function setState(state, message) {
    bar.dataset.state = state;
    text.textContent = message;
  }

  function isPositiveInteger(value) {
    return Number.isSafeInteger(value) && value > 0;
  }

  function isHex(value, length) {
    return typeof value === "string"
      && new RegExp("^[a-f0-9]{" + length + "}$", "i").test(value);
  }

  function validatePublishedEntry(manifest) {
    if (!manifest
        || manifest.repository !== "usermalik20-del/Kanta-reborn"
        || manifest.signInRequired !== true
        || typeof manifest.verifiedAt !== "string"
        || !Number.isFinite(Date.parse(manifest.verifiedAt))) {
      throw new Error("Invalid download manifest");
    }

    var entry = manifest.platforms && manifest.platforms[cfg.platform];
    if (!entry
        || entry.artifactName !== cfg.artifactName
        || !isPositiveInteger(entry.packagingRun)
        || !isPositiveInteger(entry.packagingArtifact)
        || !isPositiveInteger(entry.bytes)
        || !isPositiveInteger(entry.retentionDays)
        || !isHex(entry.source, 40)
        || !isHex(entry.sha256, 64)
        || !isHex(entry[cfg.digestKey], 64)
        || typeof entry.downloadUrl !== "string") {
      throw new Error("Invalid platform artifact record");
    }

    var artifactURL = new URL(entry.downloadUrl, location.origin);
    var expectedPath = "/usermalik20-del/Kanta-reborn/actions/runs/"
      + entry.packagingRun + "/artifacts/" + entry.packagingArtifact;
    if (artifactURL.origin !== "https://github.com"
        || artifactURL.pathname !== expectedPath
        || artifactURL.search
        || artifactURL.hash
        || artifactURL.username
        || artifactURL.password) {
      throw new Error("Unexpected artifact URL");
    }

    var downloadLink = document.querySelector("#download a.button.primary");
    if (!downloadLink || downloadLink.href !== artifactURL.href) {
      throw new Error("Page link does not match the published manifest");
    }

    return {
      source: entry.source,
      artifactDigest: entry.sha256,
      applicationDigest: entry[cfg.digestKey]
    };
  }

  if (typeof fetch !== "function") {
    setState("error", "Published " + cfg.label + " record unavailable · see details below");
    return;
  }

  fetch("/downloads.json", {
    cache: "no-store",
    credentials: "same-origin",
    headers: { Accept: "application/json" }
  })
    .then(function (response) {
      if (!response.ok) throw new Error("Download manifest request failed");
      return response.json();
    })
    .then(function (manifest) {
      var record = validatePublishedEntry(manifest);
      var readyMessage = cfg.label
        + " publication record is current · GitHub sign-in required";
      bar.title = "Source: " + record.source
        + "\nArtifact ZIP SHA-256: " + record.artifactDigest
        + "\n" + cfg.digestLabel + " SHA-256: " + record.applicationDigest;
      link.title = "Artifact ZIP SHA-256: " + record.artifactDigest
        + "\n" + cfg.digestLabel + " SHA-256: " + record.applicationDigest;
      fill.style.width = "100%";
      link.hidden = false;
      setState("ready", readyMessage);
    })
    .catch(function () {
      fill.style.width = "0%";
      link.hidden = true;
      setState("error", "Published " + cfg.label + " record unavailable · see details below");
    });
})();
