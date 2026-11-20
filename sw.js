// WanaLab God Eye v1.4.3 FIXED - NO CACHE for index.html
// Fix for: Unclickable START button - forces fresh v1.4.3 file

const CACHE_NAME = 'god-eye-v1-4-3-fixed';

self.addEventListener('install', event => {
  self.skipWaiting();
  // Do NOT cache anything - old code cached broken index.html here
});

self.addEventListener('activate', event => {
  event.waitUntil(
    caches.keys().then(cacheNames => {
      return Promise.all(
        cacheNames.map(name => {
          // Delete ALL old caches including god-eye-v1-4-1 and god-eye-v1-4-2
          return caches.delete(name);
        })
      );
    }).then(() => self.clients.claim())
  );
});

self.addEventListener('fetch', event => {
  // NETWORK FIRST - always get fresh index.html, never cached old
  if (event.request.mode === 'navigate' || event.request.url.includes('index.html') || event.request.url.endsWith('/')) {
    event.respondWith(
      fetch(event.request).catch(() => caches.match(event.request))
    );
    return;
  }
  event.respondWith(
    fetch(event.request).catch(() => caches.match(event.request))
  );
});
