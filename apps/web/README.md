# KANTA public website

Static first-party pages recovered from the captured October 15 website evidence. Android and iOS remain separate native applications under their own directories.

The Vercel project root is `apps/web`. Public HTML routes are delivered through `api/page.js`, which creates a cryptographically random per-response nonce and a matching Content Security Policy. The renderer keeps HTML responses private/no-store and excludes the private `/songbook` route from third-party integrations. `vercel.json` maps public pages to the request-time handler; preview verification must confirm that static-file routing cannot bypass it.

Google Analytics (`G-L0QV01MWR4`) and Google AdSense (`ca-pub-6290349358884298`) load only after the visitor explicitly accepts optional cookies. The consent choice can be rejected, changed or revoked through the persistent Privacy choices control. The existing `ads.txt` publisher declaration is retained. This code enables the website-side integration only; actual ad delivery still depends on Google's publisher approval, Auto Ads configuration and any applicable Google consent-platform requirements. No Google analytics or advertising script is bundled in either native app. The private Songbook is excluded from both integrations.

Downloads are published only after the exact same main-branch commit passes Android validation, unsigned iPhoneOS archive creation and both iPhone/iPad simulator walkthroughs. The publication workflow writes `downloads.json` and both platform pages, then dispatches the existing Vercel production deployment. Verified bundles have up-to-30-day retention, with the manifest recording GitHub's actual expiry. The Android APK is debug-signed. The iPhoneOS IPA is unsigned, needs separate trusted personal signing and has no iCloud Save; it is not a Safari installer. Neither is a store release.

Run `python3 tools/verify-website.py`, `python3 -m unittest tools.tests.test_website_security_contract`, `node --check apps/web/api/page.js`, `node --check apps/web/site-consent.js`, and `node --test tools/tests/test_site_page_handler.test.js` from the repository root. `website-validation.yml` owns these checks; it does not allocate native build runners. `documentation-sync.yml` separately verifies that implementation and workflow changes update their required README, architecture and testing documentation. Vercel production deployment has no pull-request trigger and preserves the previous deployment for rollback.

The recovery evidence in `docs/evidence/website/2026-10-15` remains immutable. Original legal and software notices are retained; current Android implementation and signing statements supersede unavailable-repository feature claims.



**Consent loading invariant:** `api/page.js` strips the legacy static `/analytics.js` script tag from recovered HTML before rendering. Do not reintroduce an eager Analytics tag in any source page: `site-consent.js` is the sole loader and may inject Analytics and AdSense only after the saved consent value is `accepted`. `tools/tests/test_site_page_handler.test.js` covers removal of the legacy tag from rendered output. This is important because adding a CSP nonce to an eager script does not make its execution consent-gated.
