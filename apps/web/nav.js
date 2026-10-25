/* KANTA mobile navigation toggle — same-origin, deferred, no dependencies. */
(function () {
  var toggle = document.querySelector('.nav-toggle');
  var drawer = document.getElementById('site-drawer');

  if (!toggle || !drawer) {
    return;
  }

  function closeMenu() {
    drawer.classList.remove('open');
    toggle.setAttribute('aria-expanded', 'false');
  }

  toggle.addEventListener('click', function () {
    var open = drawer.classList.toggle('open');
    toggle.setAttribute('aria-expanded', String(open));
  });

  drawer.querySelectorAll('a').forEach(function (link) {
    link.addEventListener('click', closeMenu);
  });

  document.addEventListener('keydown', function (event) {
    if (event.key === 'Escape' && drawer.classList.contains('open')) {
      closeMenu();
      toggle.focus();
    }
  });
})();
