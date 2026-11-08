// Blog data and rendering
const blogPosts = [
  {
    slug: "midi-kar-mp3-wav-karaoke-file-formats-explained",
    title: "MIDI, KAR, MP3, WAV: Karaoke File Formats Explained",
    excerpt: "Understand the differences between karaoke file formats, which ones support timed lyrics, how to convert between them, and which format to choose for your library.",
    category: "File Formats & Compatibility",
    categorySlug: "file-formats",
    date: "2026-11-01",
    readTime: "5 min read",
    author: "KANTA Team",
    featured: true
  },
  {
    slug: "choosing-the-right-soundfont-for-karaoke",
    title: "Choosing the Right SoundFont for Karaoke: A Complete Guide",
    excerpt: "SoundFonts make or break MIDI karaoke playback. Learn how to evaluate GM/GS/XG compatibility, bank size vs quality, and our top picks for different genres.",
    category: "SoundFont Selection",
    categorySlug: "soundfonts",
    date: "2026-10-28",
    readTime: "4 min read",
    author: "KANTA Team",
    featured: true
  },
  {
    slug: "building-your-personal-karaoke-library",
    title: "Building Your Personal Karaoke Library: Organization That Scales",
    excerpt: "From folder structures to metadata tagging, learn how to organize thousands of karaoke tracks so you can find any song in seconds — even offline.",
    category: "Library Management",
    categorySlug: "library-management",
    date: "2026-10-25",
    readTime: "4 min read",
    author: "KANTA Team",
    featured: true
  },
  {
    slug: "spice-pitch-estimation-how-it-works",
    title: "SPICE Pitch Estimation: How On-Device Vocal Analysis Works",
    excerpt: "Deep dive into Google's SPICE model running on TensorFlow Lite — how it estimates pitch from microphone input without sending audio to the cloud.",
    category: "Technical Deep Dives",
    categorySlug: "technical-deep-dives",
    date: "2026-10-22",
    readTime: "5 min read",
    author: "KANTA Team",
    featured: true
  },
  {
    slug: "vocal-practice-techniques-pitch-training",
    title: "Vocal Practice Techniques: Using Pitch Training to Improve Your Singing",
    excerpt: "Structured practice routines using real-time pitch feedback, song-key targeting, and scoring — designed for karaoke singers who want measurable progress.",
    category: "Vocal Practice & Technique",
    categorySlug: "vocal-practice",
    date: "2026-10-16",
    readTime: "5 min read",
    author: "KANTA Team",
    featured: true
  },
  {
    slug: "converting-karaoke-files-ffmpeg-tools",
    title: "Converting Karaoke Files: FFmpeg, Tools, and Batch Processing",
    excerpt: "Practical guide to converting between karaoke formats, extracting lyrics from KAR files, normalizing audio levels, and automating batch conversions.",
    category: "File Formats & Compatibility",
    categorySlug: "file-formats",
    date: "2026-10-13",
    readTime: "7 min read",
    author: "KANTA Team",
    featured: false
  },
  {
    slug: "soundfont-bank-size-vs-quality",
    title: "SoundFont Bank Size vs Quality: When Bigger Isn't Better",
    excerpt: "Analyzing the trade-offs between SoundFont file size, polyphony, sample quality, and loading time — with benchmarks for mobile devices.",
    category: "SoundFont Selection",
    categorySlug: "soundfonts",
    date: "2026-10-10",
    readTime: "3 min read",
    author: "KANTA Team",
    featured: false
  },
  {
    slug: "karaoke-songbook-export-sharing-privacy",
    title: "Private Songbook Export: Sharing Your Catalog Without Sharing Your Music",
    excerpt: "How KANTA's encrypted .kanta-book format works, generating CSV exports for spreadsheets, and best practices for sharing song lists privately.",
    category: "Library Management",
    categorySlug: "library-management",
    date: "2026-10-07",
    readTime: "3 min read",
    author: "KANTA Team",
    featured: false
  },
  {
    slug: "external-display-hdmi-airplay-karaoke",
    title: "External Displays for Karaoke: HDMI, AirPlay, and Wireless Options",
    excerpt: "Setting up a dedicated karaoke stage on TVs and projectors — cable choices, latency considerations, and optimizing KANTA's dual-screen mode.",
    category: "Stage & Visuals",
    categorySlug: "stage-setup",
    date: "2026-10-04",
    readTime: "3 min read",
    author: "KANTA Team",
    featured: false
  },
  {
    slug: "microphone-selection-karaoke-recording",
    title: "Microphone Selection for Karaoke: Wired, USB, and Wireless Options",
    excerpt: "Comparing microphone types for karaoke monitoring and scoring — dynamic vs condenser, USB audio interfaces, Bluetooth limitations, and budget picks.",
    category: "Vocal Practice & Technique",
    categorySlug: "vocal-practice",
    date: "2026-10-01",
    readTime: "3 min read",
    author: "KANTA Team",
    featured: false
  },
  {
    slug: "tinysoundfont-android-midi-engine",
    title: "TinySoundFont: The Android MIDI Engine Powering KANTA",
    excerpt: "How the TinySoundFont library renders SoundFont banks on Android — performance optimizations, polyphony management, and why it beats FluidSynth for mobile.",
    category: "Technical Deep Dives",
    categorySlug: "technical-deep-dives",
    date: "2026-09-28",
    readTime: "3 min read",
    author: "KANTA Team",
    featured: false
  },
  {
    slug: "karaoke-party-effects-intermission-music",
    title: "Party Effects & Intermission Music: Elevating Your Karaoke Nights",
    excerpt: "Using KANTA's eight built-in effects, custom sound clips, and between-song music to create seamless karaoke party experiences.",
    category: "Stage & Visuals",
    categorySlug: "stage-setup",
    date: "2026-09-25",
    readTime: "2 min read",
    author: "KANTA Team",
    featured: false
  },
  {
    slug: "organizing-karaoke-files-metadata-tagging",
    title: "Organizing Karaoke Files: Metadata, Tagging, and Search Strategies",
    excerpt: "Embedding metadata in MIDI/KAR/MP3 files, using KANTA's code system, and building searchable catalogs that work across platforms.",
    category: "Library Management",
    categorySlug: "library-management",
    date: "2026-09-22",
    readTime: "3 min read",
    author: "KANTA Team",
    featured: false
  },
  {
    slug: "pitch-correction-vs-pitch-training",
    title: "Pitch Correction vs Pitch Training: What Karaoke Singers Need to Know",
    excerpt: "Understanding the difference between real-time pitch correction (Auto-Tune style) and pitch training tools — and why KANTA separates them.",
    category: "Vocal Practice & Technique",
    categorySlug: "vocal-practice",
    date: "2026-09-19",
    readTime: "3 min read",
    author: "KANTA Team",
    featured: false
  },
  {
    slug: "encrypted-companion-control-android",
    title: "Encrypted Companion Control: Remote Karaoke from a Second Android",
    excerpt: "How KANTA's experimental encrypted Android-to-Android companion protocol works — pairing, encryption, session management, and security considerations.",
    category: "Technical Deep Dives",
    categorySlug: "technical-deep-dives",
    date: "2026-09-16",
    readTime: "3 min read",
    author: "KANTA Team",
    featured: false
  }
];

const categories = [
  { slug: "file-formats", name: "File Formats & Compatibility", icon: "📁", count: 3 },
  { slug: "soundfonts", name: "SoundFont Selection", icon: "🎹", count: 2 },
  { slug: "stage-setup", name: "Stage & Visuals", icon: "🎭", count: 2 },
  { slug: "vocal-practice", name: "Vocal Practice & Technique", icon: "🎤", count: 3 },
  { slug: "library-management", name: "Library Management", icon: "📚", count: 3 },
  { slug: "technical-deep-dives", name: "Technical Deep Dives", icon: "⚙️", count: 3 }
];

function formatDate(dateStr) {
  const date = new Date(dateStr);
  return date.toLocaleDateString('en-US', { year: 'numeric', month: 'long', day: 'numeric' });
}

function createPostCard(post) {
  const card = document.createElement('article');
  card.className = 'post-card';
  
  card.innerHTML = `
    <div class="post-thumbnail">
      <span class="post-category">${post.category}</span>
    </div>
    <div class="post-content">
      <div class="post-meta">
        <time datetime="${post.date}">${formatDate(post.date)}</time>
        <span class="read-time">${post.readTime}</span>
      </div>
      <h3>${post.title}</h3>
      <p class="post-excerpt">${post.excerpt}</p>
      <div class="post-read-time">
        <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><circle cx="12" cy="12" r="10"/><polyline points="12 6 12 12 16 14"/></svg>
        ${post.readTime}
      </div>
    </div>
  `;
  
  card.addEventListener('click', (e) => {
    if (!e.target.closest('a')) {
      window.location.href = `/blog/${post.slug}`;
    }
  });
  card.style.cursor = 'pointer';
  
  return card;
}

function getCategoryFromURL() {
  const path = window.location.pathname;
  const match = path.match(/^\/blog\/category\/([^/]+)/);
  return match ? match[1] : null;
}

function renderPostsGrid(postsToRender = blogPosts) {
  const grid = document.getElementById('posts-grid');
  if (!grid) return;
  
  grid.innerHTML = '';
  postsToRender.forEach((post, index) => {
    const card = createPostCard(post);
    card.style.transitionDelay = `${index * 80}ms`;
    grid.appendChild(card);
  });
  
  requestAnimationFrame(() => {
    grid.classList.add('visible');
  });
}

function filterPostsByCategory(categorySlug) {
  if (!categorySlug || categorySlug === 'all') {
    return blogPosts;
  }
  return blogPosts.filter(post => post.categorySlug === categorySlug);
}

function updateActiveCategory(categorySlug) {
  document.querySelectorAll('.category-card').forEach(card => {
    const href = card.getAttribute('href');
    const cardSlug = href ? href.split('/blog/category/')[1] : null;
    if (cardSlug === categorySlug) {
      card.style.borderColor = 'var(--accent-cyan, #5fd1d9)';
      card.style.boxShadow = '0 12px 32px rgba(95, 209, 217, 0.15)';
    } else {
      card.style.borderColor = '';
      card.style.boxShadow = '';
    }
  });
}

document.addEventListener('DOMContentLoaded', () => {
  // Check URL for category on initial load
  const urlCategory = getCategoryFromURL();
  const initialPosts = urlCategory ? filterPostsByCategory(urlCategory) : blogPosts;
  renderPostsGrid(initialPosts);
  if (urlCategory) {
    updateActiveCategory(urlCategory);
  }
  
  // Handle category filter clicks
  document.querySelectorAll('a[href^="/blog/category/"]').forEach(link => {
    link.addEventListener('click', (e) => {
      e.preventDefault();
      const categorySlug = link.getAttribute('href').split('/blog/category/')[1];
      const filtered = filterPostsByCategory(categorySlug);
      renderPostsGrid(filtered);
      updateActiveCategory(categorySlug);
      
      history.pushState({ category: categorySlug }, '', `/blog/category/${categorySlug}`);
      document.getElementById('posts-grid').scrollIntoView({ behavior: 'smooth' });
    });
  });
  
  // Handle "View all" link
  document.querySelectorAll('a[href="/blog/all"]').forEach(link => {
    link.addEventListener('click', (e) => {
      e.preventDefault();
      renderPostsGrid(blogPosts);
      updateActiveCategory(null);
      history.pushState({ category: 'all' }, '', '/blog');
    });
  });
  
  // Handle browser back/forward
  window.addEventListener('popstate', (e) => {
    const category = getCategoryFromURL();
    const posts = category ? filterPostsByCategory(category) : blogPosts;
    renderPostsGrid(posts);
    updateActiveCategory(category);
  });
  
  // Newsletter form
  const newsletterForm = document.getElementById('newsletter-form');
  if (newsletterForm) {
    newsletterForm.addEventListener('submit', (e) => {
      e.preventDefault();
      const email = document.getElementById('email').value;
      console.log('Newsletter signup:', email);
      alert('Thanks for subscribing! (Demo — no actual submission)');
      newsletterForm.reset();
    });
  }
  
  // Intersection Observer for reveal animations
  const observer = new IntersectionObserver((entries) => {
    entries.forEach(entry => {
      if (entry.isIntersecting) {
        entry.target.classList.add('visible');
      }
    });
  }, { threshold: 0.1, rootMargin: '0px 0px -50px 0px' });
  
  document.querySelectorAll('.reveal, .reveal-stagger').forEach(el => {
    observer.observe(el);
  });
});

window.blogData = { blogPosts, categories, formatDate };
