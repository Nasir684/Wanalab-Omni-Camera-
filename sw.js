// Fix: Cache.addAll fail-safe - Play Store Ready + CamScan v1.2
// Live: https://nasir684.github.io/Wanalab-Omni-Camera-/
// Date: 2026-05-14 - v5 CamScan Ready

const CACHE_NAME = 'wanalab-god-eye-v5-camscan';

self.addEventListener('install', event => {
  self.skipWaiting();
  event.waitUntil(
    caches.open(CACHE_NAME).then(async cache => {
      console.log('[GodEye] Installing v5 - CamScan Ready');
      const urls = [
        './',
        './index.html',
        './manifest.json',
        './wanalab-god-eye-v1-1-icon.png'
      ];
      // Your fail-safe method - prevents REDUNDANT
      for (let url of urls) {
        try {
          await cache.add(url);
          console.log('[GodEye] Cached:', url);
        } catch (e) {
          console.log('[GodEye] Skipped (fail-safe):', url, e.message);
        }
      }
    })
  );
});

self.addEventListener('activate', event => {
  event.waitUntil(
    caches.keys().then(names => {
      return Promise.all(
        names.map(n => {
          if (n !== CACHE_NAME) {
            console.log('[GodEye] Deleting old cache:', n);
            return caches.delete(n);
          }
        })
      );
    }).then(() => self.clients.claim())
  );
});

self.addEventListener('fetch', event => {
  const url = event.request.url;

  // === DO NOT CACHE HEAVY CDNs - They break offline ===
  // These will be ONLINE ONLY features (inactive when offline)
  if (
    url.includes('docs.opencv.org') ||
    url.includes('cdn.jsdelivr.net') ||
    url.includes('unpkg.com') ||
    url.includes('cdnjs.cloudflare.com') ||
    url.includes('github.com')
  ) {
    return; // Let browser fetch directly, don't cache
  }

  event.respondWith(
    caches.match(event.request).then(cached => {
      // OFFLINE LOGIC: Return cached if exists
      if (cached) {
        return cached;
      }

      return fetch(event.request).then(resp => {
        // Cache successful local requests for offline
        if (resp && resp.status === 200 && resp.type === 'basic') {
          const clone = resp.clone();
          caches.open(CACHE_NAME).then(c => c.put(event.request, clone));
        }
        return resp;
      }).catch(() => {
        // OFFLINE FALLBACK
        if (event.request.mode === 'navigate') {
          return caches.match('./index.html') || caches.match('./');
        }
        // For images, return icon if offline
        if (event.request.destination === 'image') {
          return caches.match('./wanalab-god-eye-v1-1-icon.png');
        }
      });
    })
  );
});
