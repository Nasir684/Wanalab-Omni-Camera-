// WanaLab God Eye v1.6.7 FINAL - OFFLINE + NO CACHE BUG
// Fix: Deletes ALL old caches (v1.4, v1.5, v1.6, v1.6.1, v1.6.5, v1.6.6) + Caches v1.6.7 for Offline

const CACHE_NAME = 'god-eye-v167-final';

self.addEventListener('install', event => {
  self.skipWaiting();
  event.waitUntil(
    caches.open(CACHE_NAME).then(cache => {
      // Cache these for OFFLINE
      return cache.addAll([
        './',
        './index.html?v=167',
        './index.html',
        './manifest.json',
        './wanalab-god-eye-v1-1-icon.png'
      ]).catch(()=>{});
    })
  );
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
  // Only handle same-origin, skip Google / WhatsApp / external HLS / MJPEG / API
  // This is the bug fix - old file cached external IP CAM URLs and broke them
  const url = new URL(event.request.url);
  if (url.origin !== location.origin) return;

  // For index.html / navigation: NETWORK FIRST, Fallback to CACHE for Offline
  if (event.request.mode === 'navigate' || event.request.url.includes('index.html') || event.request.url.endsWith('/')) {
    event.respondWith(
      fetch(event.request).then(response => {
        // Save fresh copy for next offline use
        let copy = response.clone();
        caches.open(CACHE_NAME).then(c => c.put(event.request, copy));
        return response;
      }).catch(() => {
        // Offline: Serve cached v1.6.7
        return caches.match(event.request).then(res => res || caches.match('./index.html'));
      })
    );
    return;
  }

  // For other assets (icon, manifest, etc): CACHE FIRST, then Network
  event.respondWith(
    caches.match(event.request).then(cached => {
      if (cached) return cached;
      return fetch(event.request).then(response => {
        let copy = response.clone();
        caches.open(CACHE_NAME).then(c => c.put(event.request, copy));
        return response;
      }).catch(() => cached);
    })
  );
});

self.addEventListener('message', event => {
  if (event.data === 'skipWaiting') {
    self.skipWaiting();
  }
  // New: Allow page to force clear old cache
  if (event.data === 'clearCache') {
    caches.keys().then(names => {
      names.forEach(n => { if (n !== CACHE_NAME) caches.delete(n); });
    });
  }
});
