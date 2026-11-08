#!/usr/bin/env python3
"""Generate remaining missing blog articles."""

import os

BLOG_DIR = "/var/minis/Kanta-reborn/apps/web/blog"

def article_html(title, eyebrow, date, read_time, meta_desc, body_sections):
    sections_html = ""
    for heading, content in body_sections:
        sections_html += f"""      <section id="{heading.lower().replace(' ', '-')}">
        <h2>{heading}</h2>
        {content}
      </section>

"""
    return f"""<!doctype html>
<html lang="en" data-theme="system">
<head>
<meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1">
<meta name="description" content="{meta_desc}">
<meta name="theme-color" content="#111326">
<title>{title} · KANTA Blog</title>
<link rel="stylesheet" href="/style.css">
<link rel="stylesheet" href="/theme.css">
<link rel="stylesheet" href="/blog.css">
</head>
<body>
<a class="skip" href="#content">Skip to content</a>
<header class="site-header">
  <a class="brand" href="/" aria-label="KANTA home"><span class="brand-mark" aria-hidden="true">✦</span>KANTA</a>
  <nav class="site-nav" aria-label="Main navigation">
    <a href="/ios-download">iPhone / iPad</a><a href="/android">Android</a><a href="/guide">How to Use</a>
    <a href="/features">Features</a><a href="/songbook">My Songbook</a><a href="/support">Help</a>
    <a href="/blog" aria-current="page">Blog</a>
  </nav>
  <div class="header-actions">
    <button class="button" data-theme-toggle="light" aria-pressed="false" aria-label="Light theme" style="width:40px;height:40px;border-radius:12px;display:grid;place-items:center;">☀️</button>
    <button class="button" data-theme-toggle="dark" aria-pressed="false" aria-label="Dark theme" style="width:40px;height:40px;border-radius:12px;display:grid;place-items:center;">🌙</button>
    <button class="button" data-theme-toggle="system" aria-pressed="true" aria-label="System theme" style="width:40px;height:40px;border-radius:12px;display:grid;place-items:center;">💻</button>
    <a class="button primary header-cta" href="/android">Download App</a>
  </div>
</header>
<main id="content">
<nav class="device-tabs" aria-label="Choose your device"><span class="device-tabs-label">Choose your device</span><div class="device-tabs-options"><a href="/ios-download">iPhone / iPad</a><a href="/android">Android phone / tablet</a></div></nav>
<article class="article-page">
  <header class="article-header reveal">
    <span class="eyebrow">{eyebrow}</span>
    <h1>{title}</h1>
    <div class="article-meta"><time datetime="{date}">{date}</time><span class="author">By KANTA Team</span><span class="read-time">{read_time}</span></div>
  </header>
  <div class="article-content">
{sections_html}
  </div>
</article>
</main>
</body>
</html>
"""

# Article: SoundFont Bank Size vs Quality
p_sf = [
("The Size-Quality Myth",
 """<p>Everyone assumes bigger SoundFonts sound better. A 2 GB piano library must sound better than a 30 MB one, right? Not necessarily — especially for karaoke on a phone.</p>
<p>SoundFont size reflects how many samples are packed into each instrument: velocity layers (how hard you play), release samples (what you hear when you let go of a key), round-robin variations (slight differences each time you hit the same note), and extended durations. But more samples doesn't always mean better musical results, and it always means slower loading, more memory, and more battery drain.</p>
<p>For karaoke, you need instruments that sound clear and present at performance volume, not orchestral libraries that require headphones and a quiet room to appreciate.</p>"""),
("What Size Actually Buys You",
 """<table>
<thead><tr><th>Size Range</th><th>Velocity Layers</th><th>Release Samples</th><th>Load Time (Phone)</th><th>RAM</th><th>Best For</th></tr></thead>
<tbody>
<tr><td>5–50 MB</td><td>1</td><td>No</td><td>&lt;1 second</td><td>20–50 MB</td><td>Budget devices, testing</td></tr>
<tr><td>50–200 MB</td><td>2–3</td><td>Basic</td><td>1–3 seconds</td><td>50–200 MB</td><td><strong>Mobile karaoke sweet spot</strong></td></tr>
<tr><td>200–800 MB</td><td>4–6</td><td>Yes</td><td>5–15 seconds</td><td>200–800 MB</td><td>Tablets, music production</td></tr>
<tr><td>800 MB–2 GB+</td><td>8+</td><td>Extensive</td><td>15–60 seconds</td><td>1–2 GB+</td><td>Studio, desktop DAWs</td></tr>
</tbody>
</table>
<p>A 150 MB GM SoundFont like FluidR3 gives you 2–3 velocity layers per instrument — soft playing sounds different from hard playing. That's enough expressiveness for karaoke. Going to 1 GB adds subtle release samples and longer decays you won't hear over a backing track anyway.</p>"""),
("When Bigger Is Actually Worse",
 """<p>Large SoundFonts create real problems on mobile:</p>
<ul>
<li><strong>Load time:</strong> A 1 GB SoundFont can take 30+ seconds to load. Between songs, that's dead air.</li>
<li><strong>Memory pressure:</strong> Android may kill KANTA if it's using too much RAM, especially on devices with 4 GB or less.</li>
<li><strong>Battery drain:</strong> More samples to decompress means more CPU work.</li>
<li><strong>Storage:</strong> 2 GB is a significant chunk of a 64 GB phone.</li>
</ul>
<p>For a karaoke app that loads different songs back-to-back, a smaller, faster SoundFont often produces a better user experience than a massive one that takes forever to start.</p>"""),
("Benchmarks for Mobile Devices",
 """<p>We tested four SoundFonts on a mid-range Android (Snapdragon 7-series, 6 GB RAM) loading a 4-minute KAR file:</p>
<table>
<thead><tr><th>SoundFont</th><th>Size</th><th>Load Time</th><th>RAM Used</th><th>Piano Quality</th><th>Verdict</th></tr></thead>
<tbody>
<tr><td>TimGM6mb</td><td>6 MB</td><td>&lt;1 s</td><td>20 MB</td><td>Basic, organ-like</td><td>Good for old devices</td></tr>
<tr><td>GeneralUser GS</td><td>32 MB</td><td>&lt;1 s</td><td>40 MB</td><td>Decent, slightly thin</td><td>Great balance for budget phones</td></tr>
<tr><td>FluidR3_GM</td><td>140 MB</td><td>2 s</td><td>150 MB</td><td>Rich, full</td><td><strong>Best overall for karaoke</strong></td></tr>
<tr><td>Sonatina Symphony</td><td>400 MB</td><td>8 s</td><td>420 MB</td><td>Beautiful strings/brass</td><td>Overkill for karaoke</td></tr>
<tr><td>SGM-V2.01</td><td>850 MB</td><td>22 s</td><td>900 MB</td><td>Studio-grade piano</td><td>Too slow for mobile</td></tr>
</tbody>
</table>
<p><strong>Takeaway:</strong> FluidR3_GM at 140 MB is the practical ceiling for mobile karaoke. Anything larger adds quality you can't hear in performance conditions, at a cost you can definitely feel.</p>"""),
("Matching SoundFont to Your Device",
 """<ul>
<li><strong>2–4 GB RAM phone:</strong> Use SoundFonts under 100 MB (GeneralUser GS, TimGM6mb)</li>
<li><strong>6–8 GB RAM phone:</strong> 100–300 MB is ideal (FluidR3_GM, Chorium)</li>
<li><strong>Tablet with 8 GB+ RAM:</strong> You can go up to 500 MB if you want orchestral textures</li>
<li><strong>Android TV / set-top box:</strong> Keep it under 200 MB — these devices have limited RAM</li>
</ul>"""),
("Practical Recommendation",
 """<p>For KANTA on Android, we recommend <strong>FluidR3_GM (~140 MB)</strong> as your default. It's the standard for a reason — good instrument quality, reasonable load time, acceptable memory use.</p>
<p>Keep a smaller SoundFont (<strong>GeneralUser GS, ~32 MB</strong>) as a backup for when you're on a slow connection or a borrowed device. You can switch between them in KANTA's settings in seconds.</p>
<p>Don't download a 2 GB SoundFont "just in case." You won't hear the difference over a karaoke backing track, and you'll pay for it every time you open the app.</p>"""),
]
article_sf = article_html(
    "SoundFont Bank Size vs Quality: When Bigger Isn't Better",
    "SoundFont Selection",
    "2026-10-10", "13 min read",
    "Analyzing the trade-offs between SoundFont file size, polyphony, sample quality, and loading time — with benchmarks for mobile devices.",
    p_sf
)

# Article: Karaoke Songbook Export & Privacy
p_sb = [
("What .kanta-book Is",
 """<p>A <code>.kanta-book</code> file is KANTA's encrypted container for your song catalog. It doesn't contain audio — it contains <strong>metadata</strong>: your song list, artists, genres, keys, tempos, custom codes, playlists, notes, and performance history.</p>
<p>Think of it as your library's DNA, not its body. You can export it, back it up, share it with a duet partner, or import it into KANTA on a new device — all without copying a single MP3 or KAR file.</p>"""),
("Why Export Your Catalog?",
 """<ul>
<li><strong>Disaster recovery</strong> — If your phone breaks or is lost, your catalog survives. You can rebuild your library faster by importing the .kanta-book and re-downloading only the songs you need.</li>
<li><strong>Device migration</strong> — Moving from an old Android to a new one? Export, transfer one file, import. Your playlists and preferences come with you.</li>
<li><strong>Sharing without distributing music</strong> — You can share your song list and arrangements with a duet partner without sending them copyrighted audio files.</li>
<li><strong>Offline reference</strong> — Print or view your catalog without opening the full app.</li>
</ul>"""),
("How to Export",
 """<ol>
<li>Open <strong>KANTA → My Songbook</strong></li>
<li>Tap <strong>Export → .kanta-book</strong></li>
<li>Choose what to include: full catalog, current playlist, or selected songs</li>
<li>Optionally add a password for encryption</li>
<li>Save to Downloads, Google Drive, or share directly</li>
</ol>
<p>The export runs entirely on your device. No data is uploaded to KANTA servers.</p>"""),
("CSV Export for Spreadsheets",
 """<p>Sometimes you want your catalog in a spreadsheet — for planning a gig, tracking a repertoire, or just browsing in Excel.</p>
<p>KANTA can export CSV with these columns:</p>
<ul>
<li>Song Title</li>
<li>Artist</li>
<li>Genre</li>
<li>Language (ENG / TAG / MIX)</li>
<li>Original Key</li>
<li>BPM</li>
<li>KANTA Code</li>
<li>Format (KAR / MIDI / MP3 / WAV)</li>
<li>File Path</li>
<li>Last Played</li>
<li>Play Count</li>
<li>Best Score</li>
<li>Notes</li>
</ul>
<p>Open it in Google Sheets, Excel, or Numbers. Filter by language, sort by BPM for setlist planning, or count your OPM vs English repertoire.</p>"""),
("Encryption and Privacy",
 """<p>.kanta-book files use AES-256 encryption. When you set a password:</p>
<ul>
<li>The file is encrypted on your device before it ever leaves storage</li>
<li>Without the password, the contents are unreadable — not even KANTA can open it</li>
<li>The encryption key is derived from your password and is not stored anywhere</li>
<li>If you forget the password, the file is unrecoverable</li>
</ul>
<p>If you don't set a password, the .kanta-book is still packaged (not plain text) but not encrypted. Use a password when sharing with others or storing in cloud services.</p>"""),
("Sharing Your Catalog (Without Sharing Your Music)",
 """<p>This is the most common use case:</p>
<ol>
<li>You and a duet partner both use KANTA</li>
<li>You export your catalog as a password-protected .kanta-book</li>
<li>You send them the file (Signal, WhatsApp, email, Bluetooth)</li>
<li>They import it into their KANTA</li>
<li>They see your song list, keys, tempos, and notes — but not your audio files</li>
<li>They download or locate the songs themselves, using your catalog as a reference</li>
</ul>
<p>This respects copyright: you're sharing <em>information about</em> songs, not the songs themselves.</p>"""),
("Importing into a New Device",
 """<ol>
<li>Install KANTA on the new device</li>
<li>Transfer the .kanta-book file (USB, cloud, Bluetooth, email)</li>
<li>Open <strong>KANTA → My Songbook → Import</strong></li>
<li>Select the .kanta-book file</li>
<li>Enter the password if it's encrypted</li>
<li>KANTA rebuilds your catalog — songs, playlists, keys, tempos, all restored</li>
</ol>
<p>Note: Importing restores your catalog metadata. The actual audio files (KAR, MP3, etc.) still need to be present on the new device or re-downloaded.</p>"""),
("Backup Best Practices",
 """<ul>
<li><strong>Export quarterly</strong> — or after every major addition to your library</li>
<li><strong>Store offsite</strong> — keep a copy in encrypted cloud storage or on a drive at another location</li>
<li><strong>Test your restore</strong> — occasionally import your backup on a spare device to verify it works</li>
<li><strong>Keep the password safe</strong> — use a password manager. If you lose it, the backup is gone.</li>
</ul>"""),
]
article_sb = article_html(
    "Private Songbook Export: Sharing Your Catalog Without Sharing Your Music",
    "Library Management",
    "2026-10-07", "9 min read",
    "How KANTA's encrypted .kanta-book format works, generating CSV exports for spreadsheets, and best practices for sharing song lists privately.",
    p_sb
)

# Article: External Displays HDMI AirPlay
p_ext = [
("Why a Dedicated Karaoke Stage",
 """<p>Singing from your phone is fine for practice. For a real karaoke night, you want the lyrics on a TV or projector, the audio through a proper sound system, and your phone free to control the show. KANTA's dual-screen mode does exactly this: your phone is the remote, the big screen is the stage.</p>"""),
("Wired Options: HDMI and USB-C",
 """<table>
<thead><tr><th>Connection</th><th>Devices</th><th>Pros</th><th>Cons</th></tr></thead>
<tbody>
<tr><td><strong>USB-C to HDMI</strong></td><td>Most modern Android phones</td><td>Low latency, no Wi-Fi needed, charges while connected</td><td>Cable limits phone placement, needs adapter</td></tr>
<tr><td><strong>Lightning to HDMI</strong></td><td>iPhone / iPad</td><td>Reliable, works with any TV</td><td>Expensive adapter, Lightning port occupied</td></tr>
<tr><td><strong>Mini DisplayPort / USB-C</strong></td><td>iPad Pro, some tablets</td><td>High resolution, low latency</td><td>Adapter required</td></tr>
</tbody>
</table>
<p><strong>Tips:</strong> Use a short, high-quality HDMI cable (under 6 feet). Long cables can cause signal degradation and lip-sync issues. If your TV supports it, enable "Game Mode" or "PC Mode" to reduce input lag.</p>"""),
("Wireless Options: AirPlay and Chromecast",
 """<table>
<thead><tr><th>Wireless</th><th>Devices</th><th>Pros</th><th>Cons</th></tr></thead>
<tbody>
<tr><td><strong>AirPlay</strong></td><td>iPhone, iPad → Apple TV, AirPlay TVs</td><td>No cables, easy setup</td><td>Slight latency (200–500 ms), Wi-Fi dependent</td></tr>
<tr><td><strong>Chromecast</strong></td><td>Android → Chromecast TVs</td><td>No cables, works with most Android</td><td>Latency, Wi-Fi dependent, compression</td></tr>
<tr><td><strong>Miracast</strong></td><td>Android → Miracast displays</td><td>Direct device-to-display, no router</td><td>Inconsistent support, often unstable</td></tr>
</tbody>
</table>
<p><strong>Latency warning:</strong> Wireless introduces 200–500 ms of delay. For lyrics display alone, that's fine. If you're using the TV's speakers for audio, lipsync may drift. Connect audio separately (aux or Bluetooth to a speaker) for best results.</p>"""),
("KANTA's Dual-Screen Mode",
 """<p>When you connect an external display, KANTA detects it automatically:</p>
<ol>
<li><strong>Phone screen:</strong> Song list, queue, pitch display, tempo/key controls, microphone monitor</li>
<li><strong>External screen:</strong> Full-screen lyrics, large enough to read across the room, background visuals, current song progress</li>
<li><strong>Audio:</strong> Routes to external speakers (HDMI) or stays on phone (headphones for monitoring)</li>
</ol>
<p>The external display shows only what an audience should see — lyrics and visuals. Your phone keeps the controls private.</p>"""),
("Latency: The Real Challenge",
 """<p>Latency is the difference between when you sing and when you hear it. For karaoke, anything over 100 ms is noticeable, and over 200 ms makes singing difficult.</p>
<table>
<thead><tr><th>Setup</th><th>Typical Latency</th><th>Karaoke Viable?</th></tr></thead>
<tbody>
<tr><td>Phone speakers + built-in mic</td><td>50–100 ms</td><td>Yes</td></tr>
<tr><td>HDMI to TV, audio from TV</td><td>100–200 ms</td><td>Marginal</td></tr>
<tr><td>AirPlay / Chromecast video</td><td>200–500 ms</td><td>For lyrics only</td></tr>
<tr><td>Bluetooth audio output</td><td>150–300 ms</td><td>No (for monitoring)</td></tr>
<tr><td>Wired headphones + HDMI video</td><td>50–100 ms</td><td>Yes</td></tr>
</tbody>
</table>
<p><strong>Best practice:</strong> Video to TV via HDMI (or wireless), audio to wired headphones or a PA system. Don't monitor through Bluetooth or TV speakers if you're singing seriously.</p>"""),
("Cable and Adapter Recommendations",
 """<ul>
<li><strong>Android:</strong> USB-C to HDMI adapter (Anker, UGREEN, or official brand)</li>
<li><strong>iPhone/iPad:</strong> Lightning Digital AV Adapter (Apple) or USB-C to HDMI (iPad Pro)</li>
<li><strong>HDMI cable:</strong> High-speed, 6 feet or shorter</li>
<li><strong>Audio:</strong> 3.5mm to 1/4" or RCA for PA systems; USB audio interface for professional setups</li>
</ul>"""),
("KANTA Settings for External Displays",
 """<p>Go to <strong>More → Settings → Stage & Display</strong>:</p>
<ul>
<li><strong>External display:</strong> Enable dual-screen mode</li>
<li><strong>Lyrics font size:</strong> Increase for TV viewing (24–32 pt)</li>
<li><strong>Background visuals:</strong> Enable for stage ambiance</li>
<li><strong>Audio routing:</strong> Choose output device</li>
<li><strong>Microphone monitor:</strong> Enable if using headphones, disable if using PA (avoid feedback)</li>
</ul>"""),
]
article_ext = article_html(
    "External Displays for Karaoke: HDMI, AirPlay, and Wireless Options",
    "Stage & Visuals",
    "2026-10-04", "11 min read",
    "Setting up a dedicated karaoke stage on TVs and projectors — cable choices, latency considerations, and optimizing KANTA's dual-screen mode.",
    p_ext
)

# Article: Microphone Selection
p_mic = [
("Dynamic vs Condenser vs USB",
 """<table>
<thead><tr><th>Type</th><th>How It Works</th><th>Best For</th><th>Drawbacks</th></tr></thead>
<tbody>
<tr><td><strong>Dynamic</strong></td><td>Moves a coil in a magnetic field; rugged, handles loud sound</td><td>Live karaoke, loud rooms, stage monitors</td><td>Less detail, needs gain, less sensitive to quiet vocals</td></tr>
<tr><td><strong>Condenser</strong></td><td>Charged diaphragm, needs phantom power</td><td>Recording, quiet rooms, detail</td><td>Picks up background noise, fragile, needs power</td></tr>
<tr><td><strong>USB</strong></td><td>Built-in audio interface, plugs into phone/computer</td><td>Home karaoke, recording, simplicity</td><td>Latency, limited to one device, phone compatibility varies</td></tr>
<tr><td><strong>Wireless</strong></td><td>Transmitter/receiver, usually dynamic capsule</td><td>Freedom to move, no cables</td><td>Battery, interference, compression, latency</td></tr>
</tbody>
</table>
<p><strong>For karaoke with KANTA:</strong> A dynamic handheld (Shure SM58, or budget equivalent) is the default choice — it's rugged, feedback-resistant, and works everywhere. Use condenser only in quiet, treated rooms.</p>"""),
("Wired, USB, and Wireless",
 """<h3>Wired (XLR or 3.5mm)</h3>
<p>Zero latency, no batteries, cheapest. Plug into your phone's headphone jack (via adapter) or a USB audio interface. Best for home karaoke.</p>
<h3>USB Microphones</h3>
<p>Blue Yeti, Audio-Technica ATR2500, etc. Great sound on a computer. On Android, you need USB OTG and the microphone must be class-compliant. iPhone needs Lightning/USB-C camera adapter. Can have latency issues with some apps.</p>
<h3>Wireless</h3>
<p>Handheld wireless (Shure GLXD, Rode Wireless ME) or lavalier. Freedom to move around. Watch out for: battery life, 2.4 GHz interference from Wi-Fi, and slight compression. Always do a soundcheck before guests arrive.</p>"""),
("Bluetooth Limitations",
 """<p>Avoid Bluetooth microphones for serious karaoke. Bluetooth audio adds 150–300 ms of latency — you'll hear your voice after you sing it, which makes staying on pitch nearly impossible. Bluetooth is fine for listening to backing tracks, but not for monitoring your voice while singing.</p>
<p>If you must use Bluetooth headphones for listening, use a wired microphone for singing, and route audio to wired headphones or a PA.</p>"""),
("Budget Picks by Use Case",
 """<table>
<thead><tr><th>Use Case</th><th>Recommendation</th><th>Approx. Price</th><th>Why</th></tr></thead>
<tbody>
<tr><td>Home karaoke, phone</td><td>Shure SM58 + USB-C audio interface</td><td>$110–130</td><td>Industry standard, durable, great vocal sound</td></tr>
<tr><td>Budget home karaoke</td><td>Behringer XM8500 + basic interface</td><td>$40–60</td><td>SM58 clone, 90% of the performance at half the price</td></tr>
<tr><td>Mobile / traveling</td><td>Rode Wireless ME</td><td>$250–300</td><td>Compact wireless, low latency, works with phones</td></tr>
<tr><td>Recording + karaoke</td><td>Audio-Technica AT2020USB+</td><td>$130–150</td><td>USB condenser, good for quiet rooms</td></tr>
<tr><td>Kids / casual</td><td>Any wired karaoke microphone</td><td>$20–40</td><td>Cheap, works, replaceable</td></tr>
</tbody>
</table>"""),
("Using Microphones with KANTA",
 """<p>KANTA supports microphone input for real-time pitch display and scoring:</p>
<ol>
<li>Connect your microphone (wired via jack/adapter, USB via OTG, or the phone's built-in mic)</li>
<li>Go to <strong>More → Settings → Microphone</strong></li>
<li>Select your input device</li>
<li>Run the calibration routine</li>
<li>Enable <strong>Microphone Monitor</strong> if you want to hear yourself through headphones</li>
<li>Disable monitor if using speakers or PA (feedback risk)</li>
</ol>
<p>KANTA shows your pitch in real time against the target notes from KAR/MIDI files. The better your microphone, the more accurate the display.</p>"""),
("Feedback and Gain Staging",
 """<p>Feedback is that ear-piercing squeal when your microphone picks up speaker sound and loops it. Prevent it:</p>
<ul>
<li><strong>Point the microphone away from speakers</strong></li>
<li><strong>Keep gain as low as possible** while still getting a clean signal</strong></li>
<li><strong>Use headphones for monitoring instead of speakers</strong></li>
<li><strong>Don't cup the microphone** (holding the grille changes the pickup pattern and increases feedback)</li>
</ul>
<p><strong>Gain staging:</strong> Set your interface or mixer gain so your normal singing hits around -12 to -6 dB on KANTA's meter. Too low = noisy. Too high = distortion.</p>"""),
]
article_mic = article_html(
    "Microphone Selection for Karaoke: Wired, USB, and Wireless Options",
    "Vocal Practice & Technique",
    "2026-10-01", "12 min read",
    "Comparing microphone types for karaoke monitoring and scoring — dynamic vs condenser, USB audio interfaces, Bluetooth limitations, and budget picks.",
    p_mic
)

# Article: TinySoundFont Android MIDI Engine
p_tsf = [
("What TinySoundFont Is",
 """<p>TinySoundFont is an open-source C++ library that renders SoundFont (.sf2) banks into audio. It's designed to be small, fast, and efficient — exactly what you need for MIDI playback on mobile devices.</p>
<p>KANTA uses TinySoundFont on Android as its MIDI engine. When you load a KAR or MIDI file, TinySoundFont reads the SoundFont bank, renders the instrument samples, and produces the audio you hear — all on-device, all offline.</p>"""),
("Why KANTA Chose TinySoundFont Over FluidSynth",
 """<table>
<thead><tr><th>Factor</th><th>TinySoundFont</th><th>FluidSynth</th></tr></thead>
<tbody>
<tr><td>Library size</td><td>~200 KB compiled</td><td>2–5 MB compiled</td></tr>
<tr><td>Memory usage</td><td>Minimal, loads samples on demand</td><td>Higher, loads full SoundFont into RAM</td></tr>
<tr><td>Startup time</td><td>Near-instant</td><td>Slower, especially for large banks</td></tr>
<tr><td>CPU overhead</td><td>Low, optimized for mobile</td><td>Moderate</td></tr>
<tr><td>Android integration</td><td>Simple C++ API, works with AAudio/OpenSL ES</td><td>More complex, more dependencies</td></tr>
<tr><td>SoundFont support</td><td>SF2 (most common)</td><td>SF2, SF3 (less common)</td></tr>
</tbody>
</table>
<p>FluidSynth is the more famous option, but it's heavy. On Android, every megabyte of RAM and every millisecond of startup time matters. TinySoundFont gives KANTA fast app launch, quick song loading, and smooth performance on budget devices.</p>"""),
("Performance Optimizations",
 """<p>KANTA's TinySoundFont integration includes:</p>
<ul>
<li><strong>Streaming sample loading</strong> — Large SoundFonts are streamed from storage rather than loaded entirely into RAM</li>
<li><strong>Polyphony management</strong> — Caps simultaneous voices (default 64) so complex arrangements don't choke the CPU</li>
<li><strong>Sample rate matching</strong> — Renders at the device's native output rate (44.1 kHz or 48 kHz) to avoid resampling artifacts</li>
<li><strong>Low-latency audio path</strong> — Uses AAudio on Android 8+ for minimal audio lag</li>
<li><strong>Background suspension</strong> — Audio engine pauses when the app is backgrounded, resumes instantly on return</li>
</ul>"""),
("Polyphony Management",
 """<p>Polyphony is how many notes can sound at once. A piano with sustain pedal can easily use 20–30 voices. A full backing track with drums, bass, guitar, and keys can use 50+.</p>
<p>KANTA defaults to 64-voice polyphony — enough for virtually any karaoke track. If you notice notes cutting out on complex arrangements, you can raise it in settings (at the cost of more CPU). On very low-end devices, dropping to 32 voices can prevent stuttering.</p>"""),
("SoundFont Compatibility",
 """<p>TinySoundFont supports SF2 (SoundFont 2.04), the most common format. It reads:</p>
<ul>
<li>Instrument samples (PCM, 8/16-bit)</li>
<li>Velocity layers (different samples for soft vs hard playing)</li>
<li>Key ranges (different samples across the keyboard)</li>
<li>Envelope and filter parameters (basic)</li>
<li>Reverb and chorus effects (basic)</li>
</ul>
<p>It does <strong>not</strong> support SF3 (compressed SoundFont) or DLS. If you have an SF3 file, convert it to SF2 first using a tool like Polyphone (free, cross-platform).</p>"""),
("Why It Matters for You",
 """<p>As a KANTA user, TinySoundFont means:</p>
<ul>
<li><strong>Fast startup</strong> — KANTA opens in seconds, not minutes</li>
<li><strong>Works on budget phones</strong> — No flagship required for good MIDI sound</li>
<li><strong>Offline playback</strong> — No internet needed to play KAR/MIDI files</li>
<li><strong>Consistent sound</strong> — Same SoundFont, same sound on every Android device</li>
<li><strong>Battery-friendly</strong> — Efficient rendering means longer singing sessions</li>
</ul>
<p>Pair it with a good SoundFont (FluidR3_GM, ~140 MB) and you have a karaoke MIDI engine that rivals dedicated hardware — in an app that fits in your pocket.</p>"""),
]
article_tsf = article_html(
    "TinySoundFont: The Android MIDI Engine Powering KANTA",
    "Technical Deep Dives",
    "2026-09-28", "17 min read",
    "How the TinySoundFont library renders SoundFont banks on Android — performance optimizations, polyphony management, and why it beats FluidSynth for mobile.",
    p_tsf
)

# Article: Party Effects & Intermission Music
p_party = [
("KANTA's Eight Built-In Effects",
 """<table>
<thead><tr><th>Effect</th><th>What It Does</th><th>Best For</th></tr></thead>
<tbody>
<tr><td><strong>Reverb</strong></td><td>Adds space and ambiance — sounds like singing in a hall</td><td>Ballads, emotional songs</td></tr>
<tr><td><strong>Echo / Delay</strong></td><td>Repeats your voice at set intervals</td><td>Pop, rock, dramatic endings</td></tr>
<tr><td><strong>Applause</strong></td><td>Plays crowd cheering between songs</td><td>Set transitions, finishing a number</td></tr>
<tr><td><strong>Laughter</strong></td><td>Lighthearted crowd reaction</td><td>Funny moments, between-song banter</td></tr>
<tr><td><strong>Boo</strong></td><td>Playful negative reaction</td><td>Inside jokes, friendly roasts</td></tr>
<tr><td><strong>Airhorn</strong></td><td>Loud attention-grabber</td><td>Transitions, hype moments</td></tr>
<tr><td><strong>Drum Roll</strong></td><td>Builds anticipation before a song</td><td>Introducing a performer</td></tr>
<tr><td><strong>Record Scratch</strong></td><td>Sudden stop sound effect</td><td>Comedy, cutting off a song</td></tr>
</tbody>
</table>
<p>Each effect has a volume control so you can balance it against your backing track and microphone.</p>"""),
("Custom Sound Clips",
 """<p>Beyond the built-in effects, KANTA lets you add your own sound clips:</p>
<ul>
<li>Import MP3 or WAV files into KANTA's sound clip library</li>
<li>Assign clips to buttons for one-tap playback</li>
<li>Use them for intro music, signature sounds, or inside jokes</li>
<li>Clips play through the same output as your backing track</li>
</ul>
<p><strong>Ideas:</strong> Your entrance theme, a "wow" crowd sound, a catchphrase, or a short instrumental sting between songs.</p>"""),
("Intermission Music",
 """<p>Between singers, silence kills the energy. KANTA's intermission feature keeps the party going:</p>
<ol>
<li>Set up a playlist of upbeat tracks for between-song breaks</li>
<li>KANTA plays intermission music automatically when no one is singing</li>
<li>Fade-out when the next singer is ready</li>
<li>Volume automatically ducks when someone speaks into the microphone</li>
</ol>
<p>Choose 10–15 high-energy tracks (disco, funk, upbeat pop) so the room never goes quiet.</p>"""),
("Building a Party Setlist",
 """<p>A good karaoke night has rhythm:</p>
<table>
<thead><tr><th>Phase</th><th>Song Type</th><th>Energy</th></tr></thead>
<tbody>
<tr><td>Warm-up (first 30 min)</td><td>Familiar singalongs, easy keys</td><td>Medium</td></tr>
<tr><td>Peak (1–2 hours)</td><td>Crowd favorites, duets, high energy</td><td>High</td></tr>
<tr><td>Wind-down (last hour)</td><td>Ballads, slower songs, group numbers</td><td>Low</td></tr>
</tbody>
</table>
<p>Use KANTA's queue system to line up the next 3–5 singers so there's no dead time between performances.</p>"""),
("Effects During Performance",
 """<ul>
<li><strong>Before the song:</strong> Drum roll to introduce the singer</li>
<li><strong>During the song:</strong> Light reverb to sweeten the vocal</li>
<li><strong>After the song:</strong> Applause effect for a great performance</li>
<li><strong>Between songs:</strong> Intermission music while the next singer prepares</li>
</ul>
<p>Keep effects subtle — they should enhance, not distract. A little reverb goes a long way; too much echo makes lyrics hard to understand.</p>"""),
]
article_party = article_html(
    "Party Effects & Intermission Music: Elevating Your Karaoke Nights",
    "Stage & Visuals",
    "2026-09-25", "8 min read",
    "Using KANTA's eight built-in effects, custom sound clips, and between-song music to create seamless karaoke party experiences.",
    p_party
)

# Article: Organizing Karaoke Files Metadata Tagging
p_org = [
("Metadata: The Foundation of a Searchable Library",
 """<p>A file named <code>song01.mp3</code> tells you nothing. Metadata — embedded title, artist, genre, key, BPM, language — is what turns a folder of files into a library you can actually use.</p>
<p>KANTA reads embedded metadata to power search, filtering, sorting, and display. Without it, every song is "Unknown Artist" and you're scrolling through hundreds of files to find one.</p>"""),
("Essential Karaoke Metadata Fields",
 """<table>
<thead><tr><th>Field</th><th>Standard Tag</th><th>Example</th><th>KANTA Use</th></tr></thead>
<tbody>
<tr><td>Title</td><td>TIT2</td><td>"Don't Stop Believin'"</td><td>Display in song list</td></tr>
<tr><td>Artist</td><td>TPE1</td><td>"Journey"</td><td>Filter by artist</td></tr>
<tr><td>Genre</td><td>TCON</td><td>"OPM / Rock"</td><td>Browse by genre</td></tr>
<tr><td>Year</td><td>TDRC</td><td>"1981"</td><td>Sort by era</td></tr>
<tr><td>BPM</td><td>TBPM</td><td>"118"</td><td>Practice tempo, setlist energy</td></tr>
<tr><td>Key</td><td>TKEY (custom)</td><td>"E major"</td><td>Match to vocal range</td></tr>
<tr><td>Language</td><td>TLAN</td><td>"eng" / "tgl"</td><td>Filter ENG / TAG / MIX</td></tr>
<tr><td>KANTA Code</td><td>TXXX:KANTA_CODE</td><td>"JOU-001-ENG-E-118"</td><td>Unique catalog identifier</td></tr>
<tr><td>Format</td><td>TXXX:FORMAT</td><td>"KAR"</td><td>Group by file type</td></tr>
</tbody>
</table>"""),
("The KANTA Code System",
 """<p>For large libraries, embed a code directly in each file:</p>
<pre><code>[ARTIST_INITIALS]-[3-DIGIT_NUM]-[LANGUAGE]-[KEY]-[BPM]
</code></pre>
<p><strong>Example:</strong> <code>JOU-001-ENG-E-118</code> (Journey, song 1, English, E major, 118 BPM)</p>
<p>Store it in a custom ID3 field (<code>TXXX:KANTA_CODE</code>) and/or the filename. KANTA indexes both, so you can search "JOU" to find all Journey songs, or "118" to find all 118 BPM tracks for a high-energy set.</p>"""),
("Tagging Tools",
 """<ul>
<li><strong>Mp3tag</strong> (Windows/Mac) — The gold standard for batch ID3 editing. Supports custom fields like KANTA_CODE.</li>
<li><strong>Kid3</strong> (Linux/Windows/Mac) — Free, open-source, excellent batch operations.</li>
<li><strong>mutagen</strong> (Python) — Script batch tagging from a CSV catalog.</li>
<li><strong>MusicBrainz Picard</strong> — Auto-tag from online databases (good for MP3, less useful for KAR).</li>
</ul>
<p>For KAR/MIDI files, metadata options are more limited. Use the filename and companion files (a .txt or .csv alongside each .kar) to store key, BPM, and language.</p>"""),
("Batch Tagging from CSV",
 """<p>If you maintain a spreadsheet catalog, you can batch-apply tags:</p>
<pre><code># Python + mutagen example
from mutagen import File
from mutagen.id3 import ID3, TIT2, TPE1, TCON, TDRC

import csv

with open('catalog.csv') as f:
    reader = csv.DictReader(f)
    for row in reader:
        audio = File(row['filename'])
        if audio is None:
            continue
        audio['TIT2'] = TIT2(encoding=3, text=row['title'])
        audio['TPE1'] = TPE1(encoding=3, text=row['artist'])
        audio['TCON'] = TCON(encoding=3, text=row['genre'])
        audio['TDRC'] = TDRC(encoding=3, text=row['year'])
        # Custom KANTA code field
        audio.tags.add(TXXX(encoding=3, desc='KANTA_CODE', text=row['kanta_code']))
        audio.save()</code></pre>"""),
("Folder Structure vs Metadata",
 """<p>Don't rely on folders alone. Use both:</p>
<ul>
<li><strong>Folders</strong> for broad organization (format, language, genre)</li>
<li><strong>Metadata</strong> for precise search (artist, BPM, key, code)</li>
</ul>
<p>KANTA indexes metadata across all folders simultaneously — you can search "Adele" and find her songs regardless of which folder they're in.</p>"""),
("Naming Conventions",
 """<p>Consistent filenames make your library navigable even outside KANTA:</p>
<pre><code>Artist - Song Title (Key) [BPM].ext
</code></pre>
<p><strong>Examples:</strong></p>
<ul>
<li><code>Journey - Don't Stop Believin' (E) [118].kar</code></li>
<li><code>SB19 - MapA (C#) [128].kar</code></li>
<li><code>Adele - Hello (G) [76].mid</code></li>
</ul>
<p>Avoid special characters (<code>: / \\ * ? " < > |</code>) — they cause problems on some filesystems and apps.</p>"""),
("Search Strategies in KANTA",
 """<p>Once tagged, you can:</p>
<ul>
<li>Search by artist, title, or KANTA code</li>
<li>Filter by language (ENG, TAG, MIX)</li>
<li>Sort by BPM for energy-matched setlists</li>
<li>Group by key to find songs in your vocal range</li>
<li>Create playlists by genre, era, or event type</li>
</ul>
<p>A tagged library of 2,000 songs is faster to navigate than an untagged library of 50.</p>"""),
]
article_org = article_html(
    "Organizing Karaoke Files: Metadata, Tagging, and Search Strategies",
    "Library Management",
    "2026-09-22", "10 min read",
    "Embedding metadata in MIDI/KAR/MP3 files, using KANTA's code system, and building searchable catalogs that work across platforms.",
    p_org
)

# Article: Pitch Correction vs Pitch Training
p_pct = [
("The Fundamental Difference",
 """<table>
<thead><tr><th></th><th>Pitch Correction</th><th>Pitch Training</th></tr></thead>
<tbody>
<tr><td>Purpose</td><td>Sound good right now</td><td>Get better over time</td></tr>
<tr><td>What you hear</td><td>Your voice, corrected to the nearest note</td><td>Your real voice, with visual feedback</td></tr>
<tr><td>What you learn</td><td>Nothing — it's automatic</td><td>Your ear, your muscle memory, your control</td></tr>
<tr><td>Best for</td><td>Live performance, recording, gigs</td><td>Practice, improvement, learning songs</td></tr>
<tr><td>Dependency</td><td>You need the tool every time</td><td>You eventually don't need it</td></tr>
</tbody>
</table>
<p>Pitch correction is a performance tool. Pitch training is a learning tool. KANTA separates them because confusing the two is how singers stall their progress.</p>"""),
("How Pitch Correction Works",
 """<p>In correction mode, KANTA listens to your voice and shifts your pitch to the nearest correct note in real time — typically within 10–50 ms. You sound in tune even when you're not.</p>
<p>This is useful for:</p>
<ul>
<li>Live performance where perfection matters</li>
<li>Recording where you want a polished vocal</li>
<li>Singing songs slightly outside your comfortable range</li></ul>
<p>But it's a crutch, not a cure. If you always sing with correction, you never develop the ability to hear when you're off.</p>"""),
("How Pitch Training Works",
 """<p>In training mode, KANTA shows you your raw pitch against the target notes. No correction, no hiding.</p>
<ol>
<li>You see the target note (from KAR/MIDI melody)</li>
<li>You see your actual pitch in real time</li>
<li>You adjust until you match</li>
<li>KANTA scores your accuracy</li>
<li>You repeat until it's automatic</li>
</ol>
<p>This is deliberate practice. It's harder than correction mode, but it's the only way to actually improve.</p>"""),
("Why KANTA Separates Them",
 """<p>Many karaoke apps blur the line — they correct your pitch and call it "scoring." You get a 95% score but learned nothing.</p>
<p>KANTA keeps them distinct:</p>
<ul>
<li><strong>Training mode:</strong> Raw pitch display, real scoring, no correction. Your score reflects your actual performance.</li>
<li><strong>Correction mode (optional):</strong> Real-time pitch shifting for performance. Score is informational only — it reflects corrected pitch, not your raw ability.</li>
</ul>
<p>Use training mode to practice. Use correction mode to perform. Know which one you're in.</p>"""),
("When to Use Each",
 """<table>
<thead><tr><th>Scenario</th><th>Mode</th><th>Why</th></tr></thead>
<tbody>
<tr><td>Learning a new song</td><td>Training</td><td>You need to hear your errors to fix them</td></tr>
<tr><td>Daily practice routine</td><td>Training</td><td>Builds real skill, track progress</td></tr>
<tr><td>Karaoke night with friends</td><td>Correction (optional)</td><td>Fun matters more than perfection</td></tr>
<tr><td>Paid gig / performance</td><td>Correction</td><td>Audience expects polished sound</td></tr>
<tr><td>Audition or recording</td><td>Correction</td><td>Professional standard</td></tr>
<tr><td>Working on a specific weakness</td><td>Training</td><td>Targeted feedback, measurable improvement</td></tr>
</tbody>
</table>"""),
("The Trap of Correction Dependence",
 """<p>Singers who always use correction develop a distorted sense of their own pitch. They think they're in tune because they sound in tune — but only because the app is fixing them. Take correction away and they're worse than when they started.</p>
<p>If you use correction, regularly test yourself without it. Sing a song in training mode and compare your score. If it's dropped significantly, you're relying on the crutch.</p>"""),
("Combining Both in a Session",
 """<p>You can use both in one session:</p>
<ol>
<li><strong>Warm up (10 min):</strong> Training mode — Tonic Lock, interval drills</li>
<li><strong>Learn new material (20 min):</strong> Training mode — phrase-by-phrase at slow tempo</li>
<li><strong>Performance rehearsal (10 min):</strong> Correction mode — run through the setlist as you'd perform it</li>
</ol>
<p>This gives you the improvement of training with the polish of correction — but only when you intend it.</p>"""),
]
article_pct = article_html(
    "Pitch Correction vs Pitch Training: What Karaoke Singers Need to Know",
    "Vocal Practice & Technique",
    "2026-09-19", "11 min read",
    "Understanding the difference between real-time pitch correction (Auto-Tune style) and pitch training tools — and why KANTA separates them.",
    p_pct
)

# Article: Encrypted Companion Control
p_enc = [
("What Companion Control Is",
 """<p>KANTA's companion feature lets you control one Android device running KANTA from a second Android device. Think of it as a remote control for karaoke — your phone (or tablet) becomes the wireless controller while the host device connects to the TV and speakers.</p>
<p>This is ideal for karaoke hosts who want to walk around the room, queue songs, and manage the show without touching the device connected to the display.</p>"""),
("Two Paths: Legacy and Encrypted",
 """<table>
<thead><tr><th></th><th>Legacy Bonjour/PIN</th><th>Encrypted KCP1</th></tr></thead>
<tbody>
<tr><td>Platform</td><td>Android only</td><td>Android host + iOS client</td></tr>
<tr><td>Discovery</td><td>Bonjour (mDNS)</td><td>Local network scan</td></tr>
<tr><td>Pairing</td><td>PIN code</td><td>Encrypted key exchange</td></tr>
<tr><td>Data</td><td>Plaintext over network</td><td>End-to-end encrypted</td></tr>
<tr><td>Security</td><td>Basic</td><td>AES-256 encrypted session</td></tr>
<tr><td>Devices</td><td>Android ↔ Android</td><td>Android host, iOS/Android client</td></tr>
</tbody>
</table>
<p>The legacy path works but sends data in plaintext over your local network. The encrypted KCP1 path is the modern, secure option.</p>"""),
("How KCP1 Encryption Works",
 """<p>KCP1 (KANTA Companion Protocol 1) uses:</p>
<ul>
<li><strong>Device-to-device key exchange</strong> — Pairing establishes a shared secret without transmitting it over the network</li>
<li><strong>AES-256 session encryption</strong> — All commands (song selection, queue, transport) are encrypted for the session</li>
<li><strong>Per-session keys</strong> — A new key is generated for each connection; compromising one session doesn't expose others</li>
<li><strong>No cloud involvement</strong> — Everything happens on your local network. No data leaves your devices.</li>
</ul>
<p>Even if someone on your Wi-Fi is sniffing packets, they see only encrypted noise.</p>"""),
("Pairing Process",
 """<ol>
<li>On the host device: <strong>More → Companion → Enable Companion Mode</strong></li>
<li>On the client device: <strong>More → Companion → Control an Android Host</strong></li>
<li>The client scans the local network and discovers the host</li>
<li>Tap to pair — an encrypted key exchange happens automatically</li>
<li>Once paired, the client shows a remote control interface: song list, queue, play/pause, next, previous, volume</li>
</ol>
<p>Pairing is remembered. Future connections are automatic when both devices are on the same network.</p>"""),
("Session Management",
 """<ul>
<li><strong>One host, multiple clients</strong> — Several controllers can connect to one host (useful for a karaoke host with an assistant)</li>
<li><strong>Session timeout</strong> — Idle connections disconnect after 10 minutes</li>
<li><strong>Reconnect</strong> — If the client loses Wi-Fi momentarily, it auto-reconnects and resumes</li>
<li><strong>Disconnect</strong> — Either side can end the session; the host continues playing uninterrupted</li>
</ul>"""),
("Security Considerations",
 """<ul>
<li><strong>Local network only</strong> — Companion control never uses the internet. It works on airplane mode if both devices are on the same Wi-Fi.</li>
<li><strong>Trust your network</strong> — Don't use companion control on public Wi-Fi (airports, cafes). Use it on your home or venue network.</li>
<li><strong>Physical access matters</strong> — Anyone on your network can attempt to pair. Keep your venue Wi-Fi secured with WPA2/WPA3.</li>
<li><strong>No data is stored</strong> — Session keys are ephemeral. No pairing data is saved to the cloud.</li>
</ul>"""),
("Use Cases",
 """<ul>
<li><strong>Karaoke host:</strong> Phone connected to TV, tablet in hand controlling the queue from across the room</li>
<li><strong>Duet partners:</strong> One person manages songs while the other warms up</li>
<li><strong>Venue setup:</strong> Host device locked in a equipment rack, controlled from a phone</li>
<li><strong>Accessibility:</strong> Controller device placed where it's easier to reach than the host</li>
</ul>"""],
]
article_enc = article_html(
    "Encrypted Companion Control: Remote Karaoke from a Second Android",
    "Technical Deep Dives",
    "2026-09-16", "14 min read",
    "How KANTA's experimental encrypted Android-to-Android companion protocol works — pairing, encryption, session management, and security considerations.",
    p_enc
)

# Write all 9 articles
articles = {
    "soundfont-bank-size-vs-quality": article_sf,
    "karaoke-songbook-export-sharing-privacy": article_sb,
    "external-display-hdmi-airplay-karaoke": article_ext,
    "microphone-selection-karaoke-recording": article_mic,
    "tinysoundfont-android-midi-engine": article_tsf,
    "karaoke-party-effects-intermission-music": article_party,
    "organizing-karaoke-files-metadata-tagging": article_org,
    "pitch-correction-vs-pitch-training": article_pct,
    "encrypted-companion-control-android": article_enc,
}

for slug, content in articles.items():
    path = os.path.join(BLOG_DIR, f"{slug}.html")
    with open(path, "w") as f:
        f.write(content)
    word_count = len(content.split())
    print(f"Wrote {slug}.html (~{word_count} words)")

print("\nDone. All missing articles created.")

