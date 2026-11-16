// Fix: Cache.addAll fail-safe - Play Store Ready
// Live: https://nasir684.github.io/Wanalab-Omni-Camera-/
// Date: 2026-05-14

const CACHE_NAME = 'wanalab-god-eye-v4-fix-redundant';

self.addEventListener('install', event => {
  self.skipWaiting();
  event.waitUntil(
    caches.open(CACHE_NAME).then(async cache => {
      console.log('Caching God Eye offline');
      const urls = [
        './',
        './index.html',
        './manifest.json'
      ];
      // Add one by one, skip if fails - prevents REDUNDANT bug
      for (let url of urls) {
        try {
          await cache.add(url);
        } catch (e) {
          console.log('Skipped cache:', url, e);
        }
      }
    })
  );
});

self.addEventListener('activate', event => {
  event.waitUntil(
    caches.keys().then(names => {
      return Promise.all(names.map(n => { if (n !== CACHE_NAME) return caches.delete(n); }));
    }).then(() => self.clients.claim())
  );
});

self.addEventListener('fetch', event => {
  if (event.request.url.includes('cdn.jsdelivr.net') || 
      event.request.url.includes('github.com')) return;

  event.respondWith(
    caches.match(event.request).then(cached => {
      return cached || fetch(event.request).then(resp => {
        if (resp && resp.status === 200) {
          const clone = resp.clone();
          caches.open(CACHE_NAME).then(c => c.put(event.request, clone));
        }
        return resp;
      }).catch(() => {
        if (event.request.mode === 'navigate') {
          return caches.match('./index.html') || caches.match('./');
        }
      });
    })
  );
});
