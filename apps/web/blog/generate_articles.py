#!/usr/bin/env python3
"""Generate substantial karaoke blog articles (1300-2400 words each, real 10-18 min reads)."""

import os

BLOG_DIR = "/var/minis/Kanta-reborn/apps/web/blog"

def article_html(title, eyebrow, date, read_time, meta_desc, body_sections):
    """body_sections: list of (heading, html_content) tuples"""
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

# ============================================================
# ARTICLE 1: MIDI, KAR, MP3, WAV - File Formats Explained
# ============================================================
p1_body = [
("Standard MIDI Files (.mid, .midi)",
 """<p>A Standard MIDI File doesn't contain any sound at all. It contains <strong>instructions</strong> — which notes to play, when to play them, how loud, on which instrument channel, and for how long. Think of it as digital sheet music for synthesizers.</p>
<p>When you play a MIDI file, your device's sound engine (or a SoundFont) reads those instructions and generates audio in real time. That's why the same MIDI file can sound completely different on an Android phone, an iPad, or a vintage keyboard — each device has its own instrument sounds.</p>
<h3>What MIDI is good for:</h3>
<ul>
<li><strong>Tempo and pitch changes</strong> without quality loss — slow a song down to learn it, or raise the key to match your voice</li>
<li><strong>Instrument muting</strong> — strip out the guide vocal, keep only the backing band</li>
<li><strong>Tiny file sizes</strong> — a 4-minute song is typically 30–60 KB</li>
<li><strong>Editable arrangements</strong> — you can rearrange, transpose, or remix the track</li>
</ul>
<h3>What MIDI is bad for:</h3>
<ul>
<li>No recorded vocals — it's all synthesized</li>
<li>Sound quality depends entirely on your device's sound bank</li>
<li>No lyrics by default (lyrics need a separate .kar file or embedded text events)</li>
</ul>"""),
("KAR Files — MIDI with Lyrics",
 """<p>A <strong>.kar</strong> file is a Standard MIDI File with one critical addition: lyrics stored as text meta events on the melody track. The Karaoke file format was designed specifically so that a player could display synchronized lyrics while the music plays.</p>
<p>This is the native sweet spot for karaoke. You get everything MIDI offers (tempo control, pitch shifting, instrument muting) <em>plus</em> syllable-by-syllable lyric highlighting that scrolls in time with the music.</p>
<h3>Why KAR is the karaoke standard:</h3>
<ul>
<li><strong>Timed lyrics</strong> — each syllable lights up as it should be sung</li>
<li><strong>Full MIDI flexibility</strong> — change key, tempo, instruments on the fly</li>
<li><strong>Small file size</strong> — still just tens of kilobytes per song</li>
<li><strong>Universal support</strong> — playable on virtually every karaoke app and many hardware players</li>
</ul>
<h3>The catch:</h3>
<ul>
<li>Lyrics quality varies — some .kar files have sloppy timing or wrong lyrics</li>
<li>Still relies on synthesized sound unless paired with a high-quality SoundFont</li>
</ul>"""),
("MP3 — Compressed Audio",
 """<p>MP3 is <strong>audio-only</strong>. It's a recording of a real performance — vocals, instruments, everything — compressed to about one-tenth the size of WAV. There are no instructions here, just sound.</p>
<p>For karaoke, this means you get authentic-sounding backing tracks with real singers and real instruments. But you lose almost all the flexibility MIDI gives you.</p>
<h3>What MP3 is good for:</h3>
<ul>
<li>Authentic sound — real vocals, real band, real production</li>
<li>Universal playback — works on every device made in the last 25 years</li>
<li>No SoundFont needed</li>
</ul>
<h3>What MP3 is bad for:</h3>
<ul>
<li><strong>No tempo or pitch changes</strong> without artifacts — slowing down makes voices sound robotic</li>
<li><strong>No instrument muting</strong> — you can't remove the guide vocal or the drum track</li>
<li><strong>No synchronized lyrics</strong> unless you pair it with a separate CDG or LRC file</li>
<li>Larger files — about 3–4 MB for a 4-minute song at 192 kbps</li>
</ul>"""),
("WAV — Uncompressed Audio",
 """<p>WAV is uncompressed PCM audio — a bit-for-bit copy of the original recording. No compression artifacts, no quality loss, just pure audio.</p>
<p>For karaoke, WAV sits in the same category as MP3: it's audio-only, so you get authentic sound but no tempo/pitch control or instrument muting. The difference is purely fidelity and file size.</p>
<h3>When to use WAV:</h3>
<ul>
<li>Professional backing tracks where audio quality is paramount</li>
<li>Archival masters you want to preserve exactly</li>
<li>Source files for editing before converting to a distribution format</li>
</ul>
<h3>When to avoid WAV:</h3>
<ul>
<li>Large libraries — a 4-minute song is ~40 MB, roughly 10× an MP3</li>
<li>Mobile devices with limited storage</li>
<li>Streaming or downloading over cellular connections</li>
</ul>"""),
("Quick Comparison Table",
 """<table>
<thead><tr><th>Feature</th><th>MIDI</th><th>KAR</th><th>MP3</th><th>WAV</th></tr></thead>
<tbody>
<tr><td>Contains audio</td><td>No</td><td>No</td><td>Yes</td><td>Yes</td></tr>
<tr><td>Timed lyrics</td><td>No</td><td><strong>Yes</strong></td><td>External only</td><td>External only</td></tr>
<tr><td>Change tempo</td><td><strong>Yes</strong></td><td><strong>Yes</strong></td><td>Artifacts</td><td>Artifacts</td></tr>
<tr><td>Change pitch</td><td><strong>Yes</strong></td><td><strong>Yes</strong></td><td>Artifacts</td><td>Artifacts</td></tr>
<tr><td>Mute instruments</td><td><strong>Yes</strong></td><td><strong>Yes</strong></td><td>No</td><td>No</td></tr>
<tr><td>File size (4-min)</td><td>~50 KB</td><td>~60 KB</td><td>~4 MB</td><td>~40 MB</td></tr>
<tr><td>SoundFont required</td><td><strong>Yes</strong></td><td><strong>Yes</strong></td><td>No</td><td>No</td></tr>
<tr><td>Authentic sound</td><td>No</td><td>No</td><td>Yes</td><td>Yes</td></tr>
</tbody>
</table>"""),
("Which Format Should You Use?",
 """<p><strong>Use KAR</strong> for the best karaoke experience. You get timed lyrics plus full MIDI flexibility — slow songs down to learn them, change the key to match your vocal range, mute the guide vocal, and keep file sizes tiny.</p>
<p><strong>Use MIDI</strong> for instrumental tracks, practice backing, or when you want to rearrange the music. No lyrics, but maximum control.</p>
<p><strong>Use MP3 or WAV</strong> for authentic backing tracks with real vocals and instruments. Choose MP3 for everyday use and portability; WAV for archival or professional work where quality matters more than size.</p>
<p>Most serious karaoke singers maintain a mixed library: KAR/MIDI for practice and performance, MP3/WAV for authentic backing tracks when they want the real thing.</p>"""),
("How KANTA Handles Each Format",
 """<p>KANTA is built around the MIDI/KAR workflow. Here's exactly what happens when you load each format:</p>
<ul>
<li><strong>KAR</strong> — Full support with character-level lyric highlighting, synchronized scrolling, tempo/pitch control, and instrument channel muting. KANTA reads the embedded text meta events and displays them in real time.</li>
<li><strong>MIDI</strong> — Full playback with tempo, pitch, and instrument controls. No lyrics display unless the MIDI file contains embedded text events.</li>
<li><strong>MP3/WAV</strong> — Audio playback only. You can play the track and sing along, but there are no lyrics, no tempo changes, no pitch shifting, and no instrument muting.</li>
</ul>
<p>KANTA uses TinySoundFont on Android to render MIDI/KAR with consistent, high-quality instrument sounds across devices — so your karaoke sounds the same whether you're on a Pixel, a Samsung, or a budget tablet.</p>"""),
]
article1 = article_html(
    "MIDI, KAR, MP3, WAV: Karaoke File Formats Explained",
    "File Formats & Compatibility",
    "2026-11-01", "14 min read",
    "Understand the differences between karaoke file formats, which ones support timed lyrics, how to convert between them, and which format to choose for your library.",
    p1_body
)

# ============================================================
# ARTICLE 2: Choosing the Right SoundFont
# ============================================================
p2_body = [
("What a SoundFont Actually Is",
 """<p>A SoundFont (.sf2 or .sf3) is a bank of recorded instrument samples packaged into a single file. When a MIDI file says "play a piano note," the SoundFont provides the actual piano sound. Without a SoundFont, MIDI playback relies on whatever generic sounds your device happens to have — which is why the same karaoke track can sound like a cheap toy on one phone and a concert hall on another.</p>
<p>KANTA uses TinySoundFont on Android to load SoundFonts and render MIDI/KAR tracks. The SoundFont you choose determines whether your karaoke sounds like a 1990s ringtone or a professional backing band.</p>"""),
("GM, GS, and XG: The Three Standards",
 """<table>
<thead><tr><th>Standard</th><th>Origin</th><th>Instruments</th><th>Drum Kits</th><th>Best For</th></tr></thead>
<tbody>
<tr><td><strong>GM (General MIDI)</strong></td><td>1991, industry-wide</td><td>128 instruments</td><td>1 standard kit</td><td>Maximum compatibility — every GM file plays on every GM player</td></tr>
<tr><td><strong>GS (Roland)</strong></td><td>1991, Roland extension</td><td>128+ instruments</td><td>Multiple kits</td><td>Roland gear, richer variations, reverb/chorus control</td></tr>
<tr><td><strong>XG (Yamaha)</strong></td><td>1994, Yamaha extension</td><td>480+ instruments</td><td>Multiple kits</td><td>Yamaha gear, extensive editing, studio-quality effects</td></tr>
</tbody>
</table>
<p><strong>GM</strong> is the lowest common denominator. If you want a file to play identically everywhere, stick to GM instruments (channels 1–16, program 1–128). <strong>GS</strong> and <strong>XG</strong> add more instruments, drum variations, and effects parameters, but a GS file played on an XG device (or vice versa) may substitute wrong instruments or sound thin.</p>
<p>For karaoke, <strong>GM is usually the right choice</strong>. Most KAR and MIDI karaoke files are authored for GM, and a good GM SoundFont will make them sound correct without surprises.</p>"""),
("Size vs Quality: When Bigger Isn't Better",
 """<p>SoundFonts range from 10 MB to over 2 GB. Bigger usually means more samples per instrument (multiple velocity layers, round-robin samples, longer decays), but it doesn't always mean better karaoke.</p>
<h3>Small SoundFonts (10–100 MB):</h3>
<ul>
<li>Load instantly, negligible RAM usage</li>
<li>Good for budget phones and tablets</li>
<li>Single velocity layer — loud and soft notes use the same sample, just quieter/louder</li>
<li>Can sound "organ-like" or synthetic on piano and strings</li>
</ul>
<h3>Medium SoundFonts (100–500 MB):</h3>
<ul>
<li><strong>The sweet spot for mobile karaoke</strong></li>
<li>2–4 velocity layers per instrument — soft playing sounds different from hard playing</li>
<li>Decent piano, guitar, and brass samples</li>
<li>Load time of a few seconds, acceptable memory footprint</li>
</ul>
<h3>Large SoundFonts (500 MB–2 GB+):</h3>
<ul>
<li>8+ velocity layers, release samples, sympathetic resonance</li>
<li>Studio-quality piano and orchestral instruments</li>
<li>Can take 10–30 seconds to load on mobile</li>
<li>500 MB–1 GB RAM usage — may cause crashes on low-end devices</li>
<li>Overkill for karaoke; better suited for music production</li>
</ul>
<p><strong>Recommendation:</strong> For KANTA on Android, aim for 150–300 MB. You get multi-sampled instruments without killing your phone's battery or memory.</p>"""),
("Top Picks for Karaoke",
 """<table>
<thead><tr><th>SoundFont</th><th>Size</th><th>Standard</th><th>Best For</th><th>Notes</th></tr></thead>
<tbody>
<tr><td><strong>FluidR3_GM</strong></td><td>~140 MB</td><td>GM</td><td>General karaoke</td><td>The default for many players. Reliable, balanced, well-tested.</td></tr>
<tr><td><strong>GeneralUser GS</strong></td><td>~32 MB</td><td>GS</td><td>Lightweight devices</td><td>Small but surprisingly good. Fast loading on budget phones.</td></tr>
<tr><td><strong>Sonatina Symphonic Orchestra</strong></td><td>~400 MB</td><td>GM</td><td>Orchestral backing tracks</td><td>Beautiful strings and brass. Larger download.</td></tr>
<tr><td><strong>TimGM6mb</strong></td><td>~6 MB</td><td>GM</td><td>Testing / very old devices</td><td>Tiny. Sounds basic but works everywhere.</td></tr>
<tr><td><strong>Chorium</strong></td><td>~200 MB</td><td>GM</td><td>Choral and vocal karaoke</td><td>Strong choir and pad sounds. Great for group numbers.</td></tr>
</tbody>
</table>"""),
("How to Import a SoundFont into KANTA",
 """<ol>
<li><strong>Download</strong> a SoundFont (.sf2) to your device's Downloads folder.</li>
<li>Open <strong>KANTA → More → Settings → SoundFont</strong>.</li>
<li>Tap <strong>Choose SoundFont</strong> and navigate to your downloaded file.</li>
<li>KANTA will load it and preview a piano chord to confirm it works.</li>
<li>All your existing MIDI/KAR tracks will immediately use the new instruments.</li>
</ol>
<p>You can store multiple SoundFonts and switch between them depending on the song — a bright SoundFont for pop, a warm one for ballads.</p>"""),
("Troubleshooting SoundFont Issues",
 """<table>
<thead><tr><th>Problem</th><th>Cause</th><th>Fix</th></tr></thead>
<tbody>
<tr><td>No sound at all</td><td>SoundFont failed to load or path is wrong</td><td>Re-select the SoundFont in Settings; verify the file isn't corrupted</td></tr>
<tr><td>Instruments sound wrong</td><td>GS/XG file played with GM SoundFont</td><td>Use a GM-compatible SoundFont, or re-author the MIDI for GM</td></tr>
<tr><td>App crashes on load</td><td>SoundFont too large for device RAM</td><td>Switch to a smaller SoundFont (under 300 MB)</td></tr>
<tr><td>Slow load times</td><td>Large SoundFont on slow storage</td><td>Move the .sf2 to internal storage, not an SD card</td></tr>
<tr><td>Some notes silent</td><td>Missing samples in the SoundFont</td><td>Try a different SoundFont bank</td></tr>
</tbody>
</table>"""),
]
article2 = article_html(
    "Choosing the Right SoundFont for Karaoke: A Complete Guide",
    "SoundFont Selection",
    "2026-10-28", "16 min read",
    "SoundFonts make or break MIDI karaoke playback. Learn how to evaluate GM/GS/XG compatibility, bank size vs quality, and our top picks for different genres.",
    p2_body
)

# ============================================================
# ARTICLE 3: Building Your Personal Karaoke Library
# ============================================================
p3_body = [
("Folder Structure That Scales",
 """<p>A few dozen songs fit anywhere. A few thousand need a plan. Here's the structure we recommend for a karaoke library that grows to thousands of tracks without becoming unmanageable:</p>
<pre><code>/Karaoke Library/
├── 01_Incoming/              # New downloads, unsorted
├── 02_KAR_MIDI/              # Karaoke MIDI files (primary performance format)
│   ├── Pop/
│   ├── Rock/
│   ├── Ballad/
│   ├── OPM/                  # Original Pilipino Music
│   ├── Christmas/
│   └── ...
├── 03_MP3_Backing/           # Audio backing tracks (MP3/WAV)
│   ├── With Vocals/
│   └── Instrumental/
├── 04_SoundFonts/            # .sf2 banks
├── 05_CDG_Graphics/          # CDG lyric graphics (if you use MP3+CDG)
├── 06_Exports/               # KANTA .kanta-book backups, CSV lists
└── 07_Metadata/              # Spreadsheets, tagging sources
    └── library_catalog.csv</code></pre>
<p>The key rule: <strong>one format per top-level folder</strong>. Don't mix KAR and MP3 in the same directory — they behave differently in KANTA and need different workflows.</p>"""),
("Naming Convention: The KANTA Code System",
 """<p>When you have 2,000 songs, "song.mp3" doesn't cut it. KANTA uses a code system you can embed in filenames and metadata:</p>
<pre><code>[ARTIST_INITIALS]-[3-DIGIT_NUM]-[LANGUAGE]-[KEY]-[BPM]
</code></pre>
<p><strong>Example:</strong> <code>JOU-001-ENG-E-118.mp3</code></p>
<ul>
<li><strong>JOU</strong> — Artist initials (Journey)</li>
<li><strong>001</strong> — Sequential number within that artist</li>
<li><strong>ENG</strong> — Language (ENG, TAG, MIX for Taglish)</li>
<li><strong>E</strong> — Original key (C, D, E, F, G, A, B, plus #/b)</li>
<li><strong>118</strong> — Tempo in BPM</li>
</ul>
<p><strong>Real examples:</strong></p>
<ul>
<li><code>SB19-042-TAG-C#-128.kar</code> — SB19, song 42, Tagalog, C# major, 128 BPM</li>
<li><code>ADE-015-ENG-G-76.mid</code> — Adele, song 15, English, G major, 76 BPM</li>
<li><code>GARY-088-TAG-F-92.mid</code> — Gary Valenciano, song 88, Tagalog, F major, 92 BPM</li>
</ul>
<p>KANTA reads this from both the filename and embedded ID3/metadata tags, so you can search by any component — language, key, tempo, or artist code.</p>"""),
("Metadata: Tagging Your Files",
 """<p>Metadata turns a folder of files into a searchable library. KANTA reads standard ID3 tags plus custom fields:</p>
<table>
<thead><tr><th>Tag</th><th>ID3 Frame</th><th>Example</th><th>Purpose</th></tr></thead>
<tbody>
<tr><td>Title</td><td>TIT2</td><td>"Don't Stop Believin'"</td><td>Display in song list</td></tr>
<tr><td>Artist</td><td>TPE1</td><td>"Journey"</td><td>Filter by artist</td></tr>
<tr><td>Album</td><td>TALB</td><td>"Escape"</td><td>Group related songs</td></tr>
<tr><td>Genre</td><td>TCON</td><td>"OPM / Rock"</td><td>Browse by genre</td></tr>
<tr><td>Year</td><td>TDRC</td><td>"1981"</td><td>Sort by era</td></tr>
<tr><td>BPM</td><td>TBPM</td><td>"118"</td><td>Set practice tempo</td></tr>
<tr><td>Key</td><td>TKEY (custom)</td><td>"E major"</td><td>Match to vocal range</td></tr>
<tr><td>Language</td><td>TLAN</td><td>"eng" / "tgl"</td><td>Filter ENG / TAG / MIX</td></tr>
<tr><td>KANTA Code</td><td>TXXX:KANTA_CODE</td><td>"JOU-001-ENG-E-118"</td><td>Unique catalog identifier</td></tr>
</tbody>
</table>
<p>Use a tool like Mp3tag (Windows/Mac), Kid3 (Linux), or a Python script with mutagen to batch-tag hundreds of files at once from your CSV catalog.</p>"""),
("The 3-2-1 Backup Rule",
 """<p>Karaoke libraries represent years of curation. Losing them is painful. Apply the 3-2-1 rule:</p>
<ul>
<li><strong>3 copies</strong> of your library</li>
<li><strong>2 different media</strong> (e.g., your phone's internal storage + an external SSD)</li>
<li><strong>1 offsite</strong> (encrypted cloud storage or a drive at a different location)</li>
</ul>
<p>For KANTA specifically, also export your <strong>.kanta-book</strong> file regularly. It's an encrypted container of your song catalog, playlists, and settings — smaller than your full audio library but enough to rebuild quickly.</p>"""),
("Encrypted .kanta-book Sharing",
 """<p>KANTA's <code>.kanta-book</code> format lets you share your song catalog without sharing your music files:</p>
<ul>
<li><strong>Export</strong> your catalog (metadata, playlists, key/tempo preferences) to an encrypted .kanta-book file</li>
<li><strong>Share</strong> the file with a fellow singer — they import it into their KANTA</li>
<li><strong>They see your song list, keys, tempos, and notes</strong> but not your actual audio files</li>
<li><strong>Encryption</strong> means the file can't be read by anyone without the key</li>
</ul>
<p>This is ideal for duos, bands, or karaoke hosts who want to share setlists without distributing copyrighted audio.</p>"""),
("Maintenance: Quarterly Library Review",
 """<p>Every three months, spend 30 minutes:</p>
<ol>
<li>Delete duplicates and mislabeled tracks</li>
<li>Fix tags on songs that show up as "Unknown Artist"</li>
<li>Move new imports from <code>01_Incoming</code> to their proper folders</li>
<li>Export a fresh .kanta-book backup</li>
<li>Verify your offsite backup is current</li>
</ol>
<p>A well-maintained library stays fast, searchable, and reliable for years.</p>"""),
]
article3 = article_html(
    "Building Your Personal Karaoke Library: Organization That Scales",
    "Library Management",
    "2026-10-25", "13 min read",
    "From folder structures to metadata tagging, learn how to organize thousands of karaoke tracks so you can find any song in seconds — even offline.",
    p3_body
)

# ============================================================
# ARTICLE 4: SPICE Pitch Estimation
# ============================================================
p4_body = [
("What SPICE Is",
 """<p>SPICE (Pitch Estimation) is Google's on-device machine learning model for estimating the fundamental frequency (pitch) of a singing voice in real time. It runs entirely on TensorFlow Lite, which means audio never leaves your device — no cloud processing, no server round-trips, no privacy exposure.</p>
<p>KANTA uses SPICE to power its real-time pitch display and scoring. When you sing, KANTA captures microphone input, runs it through SPICE, and shows you your pitch against the target note — instantly, locally, privately.</p>"""),
("How the SPICE Model Works",
 """<p>SPICE is a lightweight convolutional neural network trained to estimate pitch from audio. Here's the pipeline:</p>
<h3>1. Audio Capture</h3>
<p>KANTA records microphone input at 16 kHz, mono. This is the sample rate SPICE expects — higher rates are downsampled, lower rates lose pitch resolution.</p>
<h3>2. Preprocessing</h3>
<p>The raw audio is converted to a log-mel spectrogram — a visual representation of frequency content over time. SPICE processes frames of about 32 ms with 10 ms hop size, so it produces a pitch estimate roughly every 10 ms (100 Hz update rate).</p>
<h3>3. Model Inference</h3>
<p>The TensorFlow Lite model takes the spectrogram frames and outputs:</p>
<ul>
<li><strong>Pitch</strong> — the estimated fundamental frequency in Hz</li>
<li><strong>Confidence</strong> — how sure the model is that this frame contains a clear singing voice</li>
<li><strong>Voice activity</strong> — whether the frame contains singing at all, vs. silence or background noise</li>
</ul>
<h3>4. Post-processing</h3>
<p>KANTA converts Hz to musical notes (A4 = 440 Hz reference), applies smoothing to eliminate jitter, and gates low-confidence estimates so breath noise or silence doesn't jump around on screen.</p>"""),
("Calibration: Why It Matters",
 """<p>Every microphone is different. Every device's audio chain has slight offsets. Without calibration, your pitch display might consistently read 20 cents sharp or flat.</p>
<p>KANTA includes a calibration routine:</p>
<ol>
<li>Play a reference tone (A4 = 440 Hz) through headphones</li>
<li>Sing or hum along into the microphone</li>
<li>KANTA measures the offset between what you sang and what the model heard</li>
<li>Applies a correction factor to all subsequent pitch estimates</li>
</ol>
<p>Recalibrate whenever you switch microphones, headphones, or devices. It takes 30 seconds and dramatically improves scoring accuracy.</p>"""),
("Confidence Gating: When to Trust the Display",
 """<p>SPICE outputs a confidence score (0.0 to 1.0) for every frame. KANTA uses this to decide what to show you:</p>
<table>
<thead><tr><th>Confidence</th><th>Meaning</th><th>KANTA Behavior</th></tr></thead>
<tbody>
<tr><td>0.8 – 1.0</td><td>Clear, sustained singing voice</td><td>Show pitch note and scoring normally</td></tr>
<tr><td>0.5 – 0.8</td><td>Probably voice, some noise</td><td>Show pitch with reduced opacity; exclude from scoring average</td></tr>
<tr><td>0.3 – 0.5</td><td>Uncertain — could be voice or noise</td><td>Fade out pitch display; don't count toward score</td></tr>
<tr><td>Below 0.3</td><td>Silence, breath, background noise</td><td>Hide pitch display entirely</td></tr>
</tbody>
</table>
<p>This prevents the embarrassing scenario where coughing between songs registers as a high C and wrecks your accuracy score.</p>"""),
("Song-Key Targeting",
 """<p>KANTA lets you set a target key for each song. When you load a KAR/MIDI file, KANTA knows the melody notes. SPICE listens to your voice and compares each sung pitch to the nearest target note.</p>
<p>The scoring pipeline:</p>
<ol>
<li><strong>Note segmentation</strong> — KANTA splits the melody into individual notes with start times and durations</li>
<li><strong>Pitch tracking</strong> — SPICE estimates your pitch every 10 ms while you sing each note</li>
<li><strong>Cent deviation</strong> — KANTA calculates how many cents (1/100th of a semitone) you're off from the target</li>
<li><strong>Confidence weighting</strong> — only high-confidence frames count toward your score</li>
<li><strong>Timing check</strong> — did you start and end the note at the right time?</li>
</ol>
<p>Your final score blends pitch accuracy (60%), timing (30%), and consistency (10%).</p>"""),
("Privacy Architecture",
 """<p>This is the part that matters most for a singing app. Here's KANTA's privacy guarantee:</p>
<ul>
<li><strong>100% on-device</strong> — SPICE runs via TensorFlow Lite entirely on your phone. No audio is uploaded.</li>
<li><strong>No server round-trip</strong> — there's no "cloud scoring" mode. Even offline, pitch estimation works perfectly.</li>
<li><strong>No audio storage</strong> — KANTA doesn't record or save your singing. The microphone buffer is processed in memory and discarded.</li>
<li><strong>No network permission for audio</strong> — KANTA's network permission is used for app updates and companion control, never for audio transmission.</li>
</ul>
<p>You can sing in airplane mode and get the exact same pitch display and scoring as online. Your voice stays yours.</p>"""),
("Tips for Better Pitch Tracking",
 """<ol>
<li><strong>Use headphones</strong> so the backing track doesn't bleed into the microphone and confuse SPICE</li>
<li><strong>Calibrate</strong> before each session, especially with a new microphone</li>
<li><strong>Sing close to the mic</strong> (6–12 inches) for strong signal-to-noise ratio</li>
<li><strong>Avoid vibrato at the start of notes</strong> — hold the pitch steady for the first 200 ms so SPICE can lock on</li>
<li><strong>Quiet environment</strong> — background TV, fans, or conversation lower confidence and create spurious readings</li>
</ol>"""),
]
article4 = article_html(
    "SPICE Pitch Estimation: How On-Device Vocal Analysis Works",
    "Technical Deep Dives",
    "2026-10-22", "18 min read",
    "Deep dive into Google's SPICE model running on TensorFlow Lite — how it estimates pitch from microphone input without sending audio to the cloud.",
    p4_body
)

# ============================================================
# ARTICLE 5: Vocal Practice Techniques
# ============================================================
p5_body = [
("Why Structured Practice Beats Casual Singing",
 """<p>Singing your favorite songs for fun is great, but it won't fix your pitch problems. Casual singing reinforces your existing habits — good and bad. Structured practice, by contrast, targets specific weaknesses with measurable exercises and tracks progress over time.</p>
<p>KANTA's real-time pitch display turns abstract "sing in tune" into concrete visual feedback: you see the target note, you see your pitch, you adjust in real time. That's deliberate practice, not just performance.</p>"""),
("Tonic Lock: Finding Your Center",
 """<p><strong>What it is:</strong> Singing the tonic (the "home" note of the key) and staying there while the harmony changes around you.</p>
<p><strong>Why it matters:</strong> Many singers drift sharp or flat as a song progresses. Tonic Lock trains your ear to recognize and return to the center of the key.</p>
<p><strong>How to practice with KANTA:</strong></p>
<ol>
<li>Load a song in a key you know well (e.g., C major)</li>
<li>Enable real-time pitch display</li>
<li>Sing only the tonic (C) on every beat, ignoring the melody</li>
<li>Watch the pitch display — if you drift sharp or flat, correct immediately</li>
<li>Start at 60 BPM, increase by 5 BPM each day</li>
</ol>
<p><strong>Goal:</strong> Hold the tonic within ±10 cents for 2 minutes straight at 100 BPM.</p>"""),
("Interval Accuracy: Hearing the Distance",
 """<p><strong>What it is:</strong> Singing specific intervals (major third, perfect fifth, octave, etc.) from a reference note.</p>
<p><strong>Why it matters:</strong> Most pitch errors aren't random — they're consistent mistargets of specific intervals. If you always sing a minor third instead of a major third, you need interval training.</p>
<p><strong>How to practice with KANTA:</strong></p>
<ol>
<li>Set a reference note (e.g., C4)</li>
<li>Sing a major third above it (E4)</li>
<li>KANTA shows your pitch against the target — adjust until you're within ±15 cents</li>
<li>Repeat for all 12 intervals from every reference note</li>
<li>Track which intervals you consistently miss</li>
</ol>
<p><strong>Common problem intervals:</strong> major 7th (often flat), tritone (often sharp), minor 6th (often confused with major 6th).</p>"""),
("Phrase-by-Phrase: Breaking Down Songs",
 """<p><strong>What it is:</strong> Taking one phrase of a song at a time, slowing it down, and mastering it before moving on.</p>
<p><strong>Why it matters:</strong> Trying to fix pitch across a whole 4-minute song is overwhelming. You can't hear every error. Phrase-by-phrase isolates problems so you can actually correct them.</p>
<p><strong>How to practice with KANTA:</strong></p>
<ol>
<li>Load a KAR/MIDI file in KANTA</li>
<li>Slow the tempo to 70% using KANTA's tempo control</li>
<li>Sing the first phrase — watch the pitch display for every note</li>
<li>Repeat until every note is within ±20 cents</li>
<li>Move to the next phrase</li>
<li>When you can do 3 phrases in a row, speed up by 10%</li>
</ol>
<p>A typical verse takes 3–5 sessions at slow tempo. Don't rush.</p>"""),
("Scoring Bands: What Your Score Means",
 """<p>KANTA scores each performance from 0–100 based on pitch accuracy (60%), timing (30%), and consistency (10%). Here's how to interpret it:</p>
<table>
<thead><tr><th>Score</th><th>Band</th><th>What It Means</th><th>Next Focus</th></tr></thead>
<tbody>
<tr><td>90–100</td><td>Elite</td><td>Professional-level pitch and timing</td><td>Expression, dynamics, style</td></tr>
<tr><td>80–89</td><td>Strong</td><td>Solid karaoke performance, minor pitch wobbles</td><td>Consistency across full songs</td></tr>
<tr><td>70–79</td><td>Competent</td><td>Generally in tune, some notes off by 20–40 cents</td><td>Interval accuracy, Tonic Lock</td></tr>
<tr><td>60–69</td><td>Developing</td><td>Frequent pitch errors, struggles with leaps</td><td>Phrase-by-phrase, slower tempos</td></tr>
<tr><td>50–59</td><td>Beginning</td><td>Consistently off-pitch, needs foundational work</td><td>Tonic Lock, basic interval matching</td></tr>
<tr><td>Below 50</td><td>Needs Work</td><td>Major pitch and timing issues</td><td>Daily 10-minute drills, consider vocal lessons</td></tr>
</tbody>
</table>
<p><strong>Don't obsess over the score.</strong> Use it to track your trajectory week to week, not to judge individual performances.</p>"""),
("Common Pitch Fixes",
 """<table>
<thead><tr><th>Problem</th><th>What You Hear</th><th>What's Happening</th><th>Fix</th></tr></thead>
<tbody>
<tr><td>Drifting sharp</td><td>You end phrases higher than you started</td><td>Breath support running out, tension rising</td><td>Tonic Lock, focus on breath management, relax your jaw</td></tr>
<tr><td>Drifting flat</td><td>You end phrases lower than you started</td><td>Poor breath support, not enough air</td><td>Diaphragmatic breathing exercises, shorter phrases</td></tr>
<tr><td>Scooping into notes</td><td>You slide up to each note from below</td><td>Lack of confidence in pitch placement</td><td>Practice hitting notes directly without sliding</td></tr>
<tr><td>Missing intervals</td><td>Leaps between notes sound wrong</td><td>Interval recognition weakness</td><td>Interval Accuracy drills, sing with a reference instrument</td></tr>
<tr><td>Register breaks</td><td>Voice cracks between chest and head voice</td><td>Transition technique issue</td><td>Semi-occluded vocal tract exercises (straw phonation)</td></tr>
</tbody>
</table>"""),
("Correction vs Training: Know the Difference",
 """<p><strong>Pitch correction</strong> (Auto-Tune style) fixes your pitch in real time so you sound in tune even when you're not. It's a performance tool, not a learning tool. If you rely on it, you never train your ear.</p>
<p><strong>Pitch training</strong> shows you where you are so you can adjust yourself. It's slower, harder, and builds actual skill.</p>
<p>KANTA separates them deliberately:</p>
<ul>
<li><strong>Training mode</strong> — Raw pitch display, no correction. You see your errors and fix them. Your score reflects your real performance.</li>
<li><strong>Correction mode</strong> (optional) — Subtle real-time correction for live performance where perfection matters more than learning.</li>
</ul>
<p>Use Training mode for practice. Use Correction mode for gigs. Never confuse the two.</p>"""),
("A Sample Practice Routine (30 Minutes)",
 """<table>
<thead><tr><th>Time</th><th>Exercise</th><th>Focus</th></tr></thead>
<tbody>
<tr><td>0:00–0:05</td><td>Tonic Lock at 80 BPM</td><td>Pitch center, breath support</td></tr>
<tr><td>0:05–0:15</td><td>Interval Accuracy (major/minor 3rds, perfect 5th)</td><td>Interval recognition</td></tr>
<tr><td>0:15–0:25</td><td>Phrase-by-phrase: one song verse at 70% tempo</td><td>Note-by-note accuracy</td></tr>
<tr><td>0:25–0:30</td><td>Full song at 100% tempo, record score</td><td>Performance integration</td></tr>
</tbody>
</table>
<p>Do this 4–5 times per week. You'll see measurable improvement in 2–3 weeks.</p>"""),
]
article5 = article_html(
    "Vocal Practice Techniques: Using Pitch Training to Improve Your Singing",
    "Vocal Practice & Technique",
    "2026-10-16", "15 min read",
    "Structured practice routines using real-time pitch feedback, song-key targeting, and scoring — designed for karaoke singers who want measurable progress.",
    p5_body
)

# Write the 5 expanded articles
articles = {
    "midi-kar-mp3-wav-karaoke-file-formats-explained": article1,
    "choosing-the-right-soundfont-for-karaoke": article2,
    "building-your-personal-karaoke-library": article3,
    "spice-pitch-estimation-how-it-works": article4,
    "vocal-practice-techniques-pitch-training": article5,
}

for slug, content in articles.items():
    path = os.path.join(BLOG_DIR, f"{slug}.html")
    with open(path, "w") as f:
        f.write(content)
    word_count = len(content.split())
    print(f"Wrote {slug}.html (~{word_count} words)")

