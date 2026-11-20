// WanaLab God Eye v1.6.5 FINAL - OFFLINE + NO CACHE BUG
// Fix: Deletes ALL old caches (v1.4, v1.5, v1.6, v1.6.1) + Caches v1.6.5 for Offline

const CACHE_NAME = 'god-eye-v165-final';

self.addEventListener('install', event => {
  self.skipWaiting();
  event.waitUntil(
    caches.open(CACHE_NAME).then(cache => {
      // Cache these for OFFLINE - This is what old file was missing
      return cache.addAll([
        './',
        './index.html?v=165',
        './index.html',
        './manifest.json'
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
  // For index.html / navigation: NETWORK FIRST, Fallback to CACHE for Offline
  if (event.request.mode === 'navigate' || event.request.url.includes('index.html') || event.request.url.endsWith('/')) {
    event.respondWith(
      fetch(event.request).then(response => {
        // Save fresh copy for next offline use
        let copy = response.clone();
        caches.open(CACHE_NAME).then(c => c.put(event.request, copy));
        return response;
      }).catch(() => {
        // Offline: Serve cached v1.6.5
        return caches.match(event.request).then(res => res || caches.match('./index.html'));
      })
    );
    return;
  }
  // For other assets (icon, etc): CACHE FIRST, then Network
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
});
