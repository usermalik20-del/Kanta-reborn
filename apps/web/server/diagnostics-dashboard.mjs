export function dashboard(metrics) {
  const count = value => {
    const n = Number(value);
    if (!Number.isSafeInteger(n) || n < 0 || value === null || value === '') throw new Error('Invalid metric');
    return new Intl.NumberFormat('en-US').format(n);
  };
  const tile = (label, value, description) => `<div><dt>${label}</dt><dd class="value">${count(value)}</dd><dd class="detail">${description}</dd></div>`;
  return `<!doctype html>
<html lang="en"><head><meta charset="utf-8"><meta name="viewport" content="width=device-width, initial-scale=1">
<title>Launch diagnostics · KANTA</title>
<style>
:root{color-scheme:dark;font-family:system-ui,sans-serif;background:#10121e;color:#f2f4ff}
body{margin:0}main{max-width:58rem;margin:auto;padding:clamp(1.25rem,5vw,4rem)}
.brand{color:#b9adff;letter-spacing:.16em;font-weight:700}h1{font-size:clamp(1.75rem,5vw,3rem);margin:.5rem 0}
h2{margin:2rem 0 .75rem;font-size:1.1rem}p,dt{color:#c9cee2;line-height:1.5}
dl{display:grid;grid-template-columns:repeat(auto-fit,minmax(min(100%,15rem),1fr));gap:1rem;margin:0}
dl div{padding:1.2rem;background:#1b1e31;border:1px solid #3d4460;border-radius:.8rem}
.value{font-size:2.4rem;font-weight:700;margin:.35rem 0;font-variant-numeric:tabular-nums}.detail{color:#c9cee2;line-height:1.5;margin:.3rem 0 0}
</style></head><body><main><header><p class="brand">KANTA · PRIVATE</p><h1>Launch diagnostics</h1>
<p>Reports from Android installations that opted in. These counts do not represent people.</p></header>
<section aria-labelledby="activity"><h2 id="activity">Active installations</h2><dl>
${tile('Daily active', metrics.daily_active, 'Distinct installations reporting activity today (UTC).')}
${tile('Monthly active', metrics.monthly_active, 'Distinct installations reporting activity in the last 30 UTC dates, including today.')}
</dl></section><section aria-labelledby="crashes"><h2 id="crashes">Crash reports</h2><dl>
${tile('Today', metrics.daily_crashes, 'Distinct crash markers received today (UTC).')}
${tile('Last 30 days', metrics.monthly_crashes, 'Distinct crash markers received in the last 30 UTC dates.')}
</dl></section><section aria-labelledby="downloads"><h2 id="downloads">Downloads</h2>
<p>Not tracked here. A download is not an active installation or a crash report.</p></section>
<p>Windows use server receipt times. Counts may change when reports expire or are removed.</p>
</main></body></html>`;
}
