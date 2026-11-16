// WanaLab God Eye v99.3 PRO - FINAL FIX - nasir684 - All Buttons Working
const CACHE_NAME = 'wana-god-eye-v19-pro-993-nasir684-final';
const VERSION = '1.9.99.3 PRO';

const CORE_FILES = [
  './',
  './index.html?v=19&force=99&v=993',
  './manifest.json?v=993',
  './export.html?v=993',
  './js/device-hub.js?v=993',
  './devices.json?v=993',
  './protocols/index.json?v=993'
];

self.addEventListener('install', e => {
  self.skipWaiting();
  console.log('SW v99.3 PRO installing - KILLING OLD CACHE');
  e.waitUntil(
    caches.keys().then(keys => Promise.all(
      keys.map(k => {
        if (k.includes('wana')) return caches.delete(k);
      })
    )).then(() => {
      return caches.open(CACHE_NAME).then(cache => {
        return cache.addAll(CORE_FILES).catch(err => console.log('Cache add fail', err));
      });
    }).then(() => self.skipWaiting())
  );
});

self.addEventListener('activate', e => {
  console.log('SW v99.3 PRO activating - All protocols active');
  e.waitUntil(
    caches.keys().then(keys => Promise.all(
      keys.map(k => {
        if (k.includes('wana') && k !== CACHE_NAME) return caches.delete(k);
      })
    )).then(() => self.clients.claim())
  );
});

self.addEventListener('fetch', e => {
  // Network-first for HTML - FIXES CAM ISSUES
  if (e.request.destination === 'document' || e.request.url.includes('index.html') || e.request.url.includes('?v=')) {
    e.respondWith(
      fetch(e.request, { cache: 'no-store' })
        .then(r => {
          // Clone and cache fresh
          let clone = r.clone();
          caches.open(CACHE_NAME).then(c => c.put(e.request, clone));
          return r;
        })
        .catch(() => caches.match(e.request))
    );
    return;
  }
  // Cache-first for others
  e.respondWith(
    caches.match(e.request).then(cached => {
      return cached || fetch(e.request).then(r => {
        let clone = r.clone();
        caches.open(CACHE_NAME).then(c => c.put(e.request, clone));
        return r;
      });
    }).catch(() => caches.match('./index.html?v=19&force=99&v=993'))
  );
});
