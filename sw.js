// Commit: God Eye v1.1 PRO Voice Fixed Final - Play Store Ready
// Cache: wanalab-god-eye-v1-1-pro-voice-fixed-final
// Fixes: CDN excluded from cache (prevents hang), offline fallback to index.html, old caches auto-deleted
// Voice Fix: Works with restored v8 engine + TTS pause fix
// Author: Nasir Khan - WanaLab - Wana, Pakistan
// Date: 2026-05-14
// Repo: https://github.com/nasir684/Wanalab-Omni-Camera-

// WanaLab God Eye v1.1 PRO Voice Fixed Final - Offline universal camera
const CACHE_NAME = 'wanalab-god-eye-v1-1-pro-voice-fixed-final';
const urlsToCache = [
  '/Wanalab-Omni-Camera-/',
  '/Wanalab-Omni-Camera-/index.html',
  '/Wanalab-Omni-Camera-/manifest.json',
  '/Wanalab-Omni-Camera-/wanalab-god-eye-v1-1-icon.png',
  '/Wanalab-Omni-Camera-/wanalab-icon-v8.1.png',
  '/Wanalab-Omni-Camera-/protocols/clip-lenses.json',
  '/Wanalab-Omni-Camera-/protocols/custom.json',
  '/Wanalab-Omni-Camera-/protocols/endoscopes.json',
  '/Wanalab-Omni-Camera-/protocols/ip-cameras.json',
  '/Wanalab-Omni-Camera-/protocols/night-vision.json',
  '/Wanalab-Omni-Camera-/protocols/telephoto.json',
  '/Wanalab-Omni-Camera-/protocols/thermal.json',
  '/Wanalab-Omni-Camera-/protocols/uvc-microscopes.json',
  '/Wanalab-Omni-Camera-/protocols/wifi-microscopes.json'
];

self.addEventListener('install', event => {
  self.skipWaiting();
  event.waitUntil(
    caches.open(CACHE_NAME).then(cache => cache.addAll(urlsToCache))
  );
});

self.addEventListener('activate', event => {
  event.waitUntil(
    caches.keys().then(cacheNames => {
      return Promise.all(cacheNames.map(cacheName => { 
        if (cacheName !== CACHE_NAME) { 
          return caches.delete(cacheName); 
        } 
      }));
    }).then(() => self.clients.claim())
  );
});

self.addEventListener('fetch', event => {
  if(event.request.url.includes('cdn.jsdelivr.net') || 
     event.request.url.includes('unpkg.com') || 
     event.request.url.includes('cdnjs.cloudflare.com')) return;
  
  event.respondWith(
    fetch(event.request).catch(()=>caches.match(event.request).then(res => {
      if(event.request.mode === 'navigate'){
        return res || caches.match('/Wanalab-Omni-Camera-/index.html');
      }
      return res;
    }))
  );
});
