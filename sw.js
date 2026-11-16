// WanaLab God Eye v1.1 PRO - Universal Camera OS - World's first offline universal camera
const CACHE_NAME = 'wanalab-god-eye-v1-1-pro-final';
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
  // Don't cache external CDN - prevents hang (your original fix)
  if(event.request.url.includes('cdn.jsdelivr.net') || 
     event.request.url.includes('unpkg.com') || 
     event.request.url.includes('cdnjs.cloudflare.com')) return;
  
  event.respondWith(
    fetch(event.request).catch(()=>caches.match(event.request).then(res => {
      // Fallback to index.html for navigation requests offline
      if(event.request.mode === 'navigate'){
        return res || caches.match('/Wanalab-Omni-Camera-/index.html');
      }
      return res;
    }))
  );
});
