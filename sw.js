// Commit: OFFLINE FIX FINAL - Play Store Ready
// Live: https://nasir684.github.io/Wanalab-Omni-Camera-/
// Repo: https://github.com/nasir684/Wanalab-Omni-Camera-
// Fix: Cache-First for offline - solves "You're offline" screen
// Date: 2026-05-14

const CACHE_NAME = 'wanalab-god-eye-v1-1-pro-offline-fix-v2';
const urlsToCache = [
  '/Wanalab-Omni-Camera-/',
  '/Wanalab-Omni-Camera-/index.html',
  '/Wanalab-Omni-Camera-/manifest.json',
  '/Wanalab-Omni-Camera-/wanalab-god-eye-v1-1-icon.png',
  '/Wanalab-Omni-Camera-/wanalab-icon-v8.1.png'
];

self.addEventListener('install', event => {
  self.skipWaiting();
  event.waitUntil(
    caches.open(CACHE_NAME).then(cache => {
      console.log('Caching God Eye offline');
      return cache.addAll(urlsToCache);
    })
  );
});

self.addEventListener('activate', event => {
  event.waitUntil(
    caches.keys().then(cacheNames => {
      return Promise.all(
        cacheNames.map(name => {
          if (name !== CACHE_NAME) return caches.delete(name);
        })
      );
    }).then(() => self.clients.claim())
  );
});

self.addEventListener('fetch', event => {
  // Don't intercept CDN - let browser handle
  if (event.request.url.includes('cdn.jsdelivr.net') || 
      event.request.url.includes('unpkg.com') || 
      event.request.url.includes('cdnjs.cloudflare.com') ||
      event.request.url.includes('github.com')) {
    return;
  }

  // OFFLINE-FIRST: Try cache first, then network
  event.respondWith(
    caches.match(event.request).then(cachedResponse => {
      if (cachedResponse) {
        // Return cached and update cache in background
        event.waitUntil(
          fetch(event.request).then(networkResponse => {
            return caches.open(CACHE_NAME).then(cache => {
              cache.put(event.request, networkResponse.clone());
              return networkResponse;
            });
          }).catch(() => {})
        );
        return cachedResponse;
      }
      
      // Not in cache, try network
      return fetch(event.request)
        .then(response => {
          // Cache new requests
          if (response && response.status === 200 && response.type === 'basic') {
            let responseClone = response.clone();
            caches.open(CACHE_NAME).then(cache => {
              cache.put(event.request, responseClone);
            });
          }
          return response;
        })
        .catch(() => {
          // Offline fallback for navigation
          if (event.request.mode === 'navigate') {
            return caches.match('/Wanalab-Omni-Camera-/index.html') || 
                   caches.match('/Wanalab-Omni-Camera-/');
          }
          return new Response('Offline - God Eye cached', {status: 503});
        });
    })
  );
});
