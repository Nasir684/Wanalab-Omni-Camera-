// WanaLab God Eye v1.6 FINAL - NO CACHE BUG
// Fix: Deletes ALL old caches (v1.4.1, v1.4.2, v1.4.3, v1.4.4, v1.5) and always fetches fresh v1.6

const CACHE_NAME = 'god-eye-v1-6-final';

self.addEventListener('install', event => {
  self.skipWaiting();
  // Do NOT pre-cache index.html - old code cached broken file here
});

self.addEventListener('activate', event => {
  event.waitUntil(
    caches.keys().then(cacheNames => {
      return Promise.all(
        cacheNames.map(name => {
          // Delete EVERY old cache
          if (name !== CACHE_NAME) {
            return caches.delete(name);
          }
        })
      );
    }).then(() => self.clients.claim())
  );
});

self.addEventListener('fetch', event => {
  // NETWORK FIRST for everything - always fresh v1.6
  // Only use cache if offline AND network fails
  event.respondWith(
    fetch(event.request)
      .then(response => {
        // Don't cache index.html or / - always fresh
        if (event.request.mode === 'navigate' || event.request.url.includes('index.html') || event.request.url.endsWith('/')) {
          return response;
        }
        // Cache other assets (icons, libs) for offline - optional
        return response;
      })
      .catch(() => {
        return caches.match(event.request);
      })
  );
});

// Force update when new version arrives
self.addEventListener('message', event => {
  if (event.data === 'skipWaiting') {
    self.skipWaiting();
  }
});
