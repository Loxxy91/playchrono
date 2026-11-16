/* Applies the saved / requested theme before first paint (separate file so the page can run under a strict CSP). */
try {
  var t = new URLSearchParams(location.search).get('theme') || localStorage.getItem('pp.theme');
  if (t === 'light' || t === 'dark') document.documentElement.dataset.theme = t;
  if (new URLSearchParams(location.search).get('anim') === '0') document.documentElement.classList.add('no-anim');
} catch (e) { /* storage blocked */ }
