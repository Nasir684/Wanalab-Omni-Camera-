// WanaLab God Eye - SW v6 TURBO FASTEST
// v6-turbo-camscan - Pre-cache + Lazy OCR + Instant Load
// Author: Nasir Khan - Date: 2026-05-14
const CACHE_NAME = 'god-eye-v6-turbo-camscan-fixed-final';
const CORE_ASSETS = [
  '/Wanalab-Omni-Camera-/',
  '/Wanalab-Omni-Camera-/index.html',
  '/Wanalab-Omni-Camera-/manifest.json',
  '/Wanalab-Omni-Camera-/wanalab-god-eye-v1-1-icon.png'
];

// CDN libs to pre-cache for speed (except Tesseract - lazy)
const CDN_CACHE = [
  'https://cdnjs.cloudflare.com/ajax/libs/jspdf/2.5.1/jspdf.umd.min.js',
  'https://unpkg.com/@zxing/library@0.20.0/umd/index.min.js',
  'https://docs.opencv.org/4.8.0/opencv.js'
];

// Install - Pre-cache everything instantly
self.addEventListener('install', event => {
  console.log('[SW v6 TURBO] Installing - Pre-caching core + OpenCV');
  self.skipWaiting();
  event.waitUntil(
    caches.open(CACHE_NAME).then(async cache => {
      // Cache core first
      await cache.addAll(CORE_ASSETS);
      // Then try CDN cache (don't fail if one fails)
      try {
        await Promise.allSettled(CDN_CACHE.map(url => 
          cache.add(url).catch(() => console.log('CDN cache skip:', url))
        ));
      } catch(e){}
      console.log('[SW v6] All pre-cached - App will be FAST');
    })
  );
});

// Activate - Clean old caches
self.addEventListener('activate', event => {
  console.log('[SW v6 TURBO] Activating - Cleaning old caches');
  event.waitUntil(
    caches.keys().then(keys => 
      Promise.all(keys.map(k => {
        if(k !== CACHE_NAME) {
          console.log('Deleting old cache:', k);
          return caches.delete(k);
        }
      }))
    ).then(() => self.clients.claim())
  );
});

// Fetch - TURBO STRATEGY
self.addEventListener('fetch', event => {
  const req = event.request;
  const url = req.url;

  // Skip non-GET
  if(req.method !== 'GET') return;

  // === STRATEGY 1: CDN libs - Cache First, super fast ===
  if(CDN_CACHE.some(cdnUrl => url.includes(cdnUrl.split('/')[2]))) {
    event.respondWith(
      caches.match(req).then(cached => {
        if(cached) {
          // Update in background for next time
          fetch(req).then(res => {
            if(res.ok) caches.open(CACHE_NAME).then(c => c.put(req, res));
          }).catch(()=>{});
          return cached;
        }
        // Not cached, fetch and cache
        return fetch(req).then(res => {
          if(res.ok) {
            const clone = res.clone();
            caches.open(CACHE_NAME).then(c => c.put(req, clone));
          }
          return res;
        }).catch(() => caches.match(req));
      })
    );
    return;
  }

  // === STRATEGY 2: Tesseract - Network First, Lazy ===
  // Don't cache Tesseract until user uses OCR - saves startup time
  if(url.includes('tesseract')) {
    event.respondWith(
      fetch(req).then(res => {
        // Cache only after first successful OCR use
        if(res.ok) {
          const clone = res.clone();
          caches.open(CACHE_NAME).then(c => c.put(req, clone));
        }
        return res;
      }).catch(() => caches.match(req))
    );
    return;
  }

  // === STRATEGY 3: Core files - Cache First, always fast ===
  event.respondWith(
    caches.match(req).then(cached => {
      if(cached) return cached;
      return fetch(req).then(res => {
        // Don't cache chrome-extension or other schemes
        if(!res.ok || res.type === 'opaque') return res;
        const clone = res.clone();
        caches.open(CACHE_NAME).then(c => c.put(req, clone));
        return res;
      }).catch(() => {
        // Offline fallback - return index.html for navigation
        if(req.mode === 'navigate') {
          return caches.match('/Wanalab-Omni-Camera-/index.html');
        }
      });
    })
  );
});

// Message handler for skipWaiting
self.addEventListener('message', event => {
  if(event.data && event.data.type === 'SKIP_WAITING') {
    self.skipWaiting();
  }
});

console.log('God Eye SW v6 TURBO Loaded - Fastest Mode ON');
