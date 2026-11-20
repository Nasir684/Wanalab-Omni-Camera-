// WanaLab God Eye v1.4.2 FIXED - NO CACHE for index.html
// This version DELETES old cache and always fetches fresh file
// Fix for: Hang caused by old cached index.html

const CACHE_NAME = 'god-eye-v1-4-2-fixed';

self.addEventListener('install', event => {
  self.skipWaiting();
  // Do NOT cache anything on install - old code cached broken index.html here
});

self.addEventListener('activate', event => {
  event.waitUntil(
    caches.keys().then(cacheNames => {
      return Promise.all(
        cacheNames.map(name => {
          // Delete ALL old caches including god-eye-v1-4-1-anti-mix
          return caches.delete(name);
        })
      );
    }).then(() => self.clients.claim())
  );
});

self.addEventListener('fetch', event => {
  // NETWORK FIRST for HTML - always get fresh index.html
  // CACHE ONLY for icon, never for index.html
  if (event.request.mode === 'navigate' || event.request.url.includes('index.html') || event.request.url.endsWith('/')) {
    event.respondWith(
      fetch(event.request).catch(() => caches.match(event.request))
    );
    return;
  }
  // For other files, try network, fallback to cache
  event.respondWith(
    fetch(event.request).catch(() => caches.match(event.request))
  );
});
