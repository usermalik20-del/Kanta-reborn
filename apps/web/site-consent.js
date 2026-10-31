"use strict";

(() => {
  const consentCookie = "kanta_site_consent";
  const banner = document.getElementById("kanta-cookie-consent");
  const settings = document.getElementById("kanta-cookie-settings");
  const readConsent = () => document.cookie.split("; ").find((item) => item.startsWith(consentCookie + "="))?.split("=")[1] || "";
  const saveConsent = (value) => {
    document.cookie = consentCookie + "=" + value + "; Max-Age=15552000; Path=/; SameSite=Lax; Secure";
    window.location.reload();
  };

  function loadGoogleTools() {
    if (readConsent() !== "accepted") return;
    const analytics = document.createElement("script");
    analytics.defer = true;
    analytics.src = "/analytics.js";
    analytics.nonce = document.currentScript?.nonce || document.querySelector("script[nonce]")?.nonce || "";
    document.head.appendChild(analytics);

    const ads = document.createElement("script");
    ads.async = true;
    ads.src = "https://pagead2.googlesyndication.com/pagead/js/adsbygoogle.js?client=ca-pub-6290349358884298";
    ads.crossOrigin = "anonymous";
    ads.nonce = document.currentScript?.nonce || document.querySelector("script[nonce]")?.nonce || "";
    document.head.appendChild(ads);
  }

  if (banner && settings) {
    const consent = readConsent();
    banner.hidden = Boolean(consent);
    settings.addEventListener("click", () => { banner.hidden = false; });
    banner.querySelector('[data-consent="accept"]')?.addEventListener("click", () => saveConsent("accepted"));
    banner.querySelector('[data-consent="reject"]')?.addEventListener("click", () => saveConsent("rejected"));
  }
  loadGoogleTools();
})();
