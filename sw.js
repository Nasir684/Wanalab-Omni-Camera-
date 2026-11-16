// WanaLab God Eye - SW v9 FINAL ALL-IN-ONE
// v9-final-camscan - Pre-cache + Thermal/Tracking + DOCX+IMG + Lazy OCR
// Author: Nasir Khan - Date: 2026-05-14
const CACHE_NAME = 'god-eye-v9-final-all-in-one';
const CORE_ASSETS = [
  '/Wanalab-Omni-Camera-/',
  '/Wanalab-Omni-Camera-/index.html',
  '/Wanalab-Omni-Camera-/manifest.json',
  '/Wanalab-Omni-Camera-/wanalab-god-eye-v1-1-icon.png'
];

// CDN libs to pre-cache for speed (added DOCX lib for v1.9)
const CDN_CACHE = [
  'https://cdnjs.cloudflare.com/ajax/libs/jspdf/2.5.1/jspdf.umd.min.js',
  'https://unpkg.com/@zxing/library@0.20.0/umd/index.min.js',
  'https://docs.opencv.org/4.8.0/opencv.js',
  'https://unpkg.com/docx@7.8.2/build/index.js'
];

// Install - Pre-cache everything instantly
self.addEventListener('install', event => {
  console.log('[SW v9 FINAL] Installing - Pre-caching core + OpenCV + DOCX');
  self.skipWaiting();
  event.waitUntil(
    caches.open(CACHE_NAME).then(async cache => {
      await cache.addAll(CORE_ASSETS);
      try {
        await Promise.allSettled(CDN_CACHE.map(url => 
          cache.add(url).catch(() => console.log('CDN cache skip:', url))
        ));
      } catch(e){}
      console.log('[SW v9] All pre-cached - v1.9 FAST READY');
    })
  );
});

// Activate - Clean old caches
self.addEventListener('activate', event => {
  console.log('[SW v9 FINAL] Activating - Cleaning old caches');
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

// Fetch - TURBO STRATEGY v9
self.addEventListener('fetch', event => {
  const req = event.request;
  const url = req.url;
  if(req.method !== 'GET') return;

  // STRATEGY 1: CDN libs - Cache First, super fast (includes DOCX now)
  if(CDN_CACHE.some(cdnUrl => url.includes(cdnUrl.split('/')[2]))) {
    event.respondWith(
      caches.match(req).then(cached => {
        if(cached) {
          fetch(req).then(res => {
            if(res.ok) caches.open(CACHE_NAME).then(c => c.put(req, res));
          }).catch(()=>{});
          return cached;
        }
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

  // STRATEGY 2: Tesseract + DOCX heavy - Network First, Lazy
  if(url.includes('tesseract') || url.includes('docx')) {
    event.respondWith(
      fetch(req).then(res => {
        if(res.ok) {
          const clone = res.clone();
          caches.open(CACHE_NAME).then(c => c.put(req, clone));
        }
        return res;
      }).catch(() => caches.match(req))
    );
    return;
  }

  // STRATEGY 3: Core files - Cache First
  event.respondWith(
    caches.match(req).then(cached => {
      if(cached) return cached;
      return fetch(req).then(res => {
        if(!res.ok || res.type === 'opaque') return res;
        const clone = res.clone();
        caches.open(CACHE_NAME).then(c => c.put(req, clone));
        return res;
      }).catch(() => {
        if(req.mode === 'navigate') {
          return caches.match('/Wanalab-Omni-Camera-/index.html');
        }
      });
    })
  );
});

self.addEventListener('message', event => {
  if(event.data && event.data.type === 'SKIP_WAITING') {
    self.skipWaiting();
  }
});

console.log('God Eye SW v9 FINAL Loaded - Thermal + Tracking + DOCX+IMG ON');
