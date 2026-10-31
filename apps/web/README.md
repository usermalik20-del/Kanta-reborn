# KANTA public website

Static first-party pages recovered from the captured October 15 website evidence. Android and iOS remain separate native applications under their own directories.

Deploy this directory as a static site, with no install or build command. `vercel.json` applies the same restrictive Content Security Policy to every public HTML route. Pages use only same-origin scripts, and `connect-src 'none'` prevents browser-side network requests. The public site does not load third-party analytics or advertising scripts. The browser Songbook script and stylesheet remain byte-identical to the immutable evidence; its files, codes and searches are processed locally under the strict route policy.

Downloads are published automatically only after the exact same main-branch commit passes Android validation, unsigned iPhoneOS archive creation, and both iPhone/iPad simulator walkthroughs. The publication workflow writes `downloads.json` and both platform pages, then dispatches the existing Vercel production deployment. The resulting verified bundles require GitHub sign-in and have up-to-30-day retention, with the manifest recording GitHub's actual expiry. The Android APK is debug-signed. The iPhoneOS IPA is unsigned, needs separate trusted personal signing and has no iCloud Save; it is not a Safari installer. Neither is a store release.

Run `python3 tools/verify-website.py`, `python3 -m unittest tools.tests.test_website_security_contract`, `node --check apps/web/songbook.js`, from the repository root. Website validation checks every route's CSP and the deployment workflow gates production publishing on the same contract. Native apps are not rebuilt by the website-only workflow. Preserve the previous deployment for rollback.

The recovery evidence in `docs/evidence/website/2026-10-15` remains immutable. Original legal and software notices are retained; current Android implementation and signing statements supersede unavailable-repository feature claims.

