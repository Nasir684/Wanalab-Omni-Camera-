// WanaLab v9.2.11.1 - No Cache Hang + Protocols Offline
const CACHE_NAME = 'wanalab-v9-2-11-1-protocols';
const urlsToCache = [
  '/Wanalab-Omni-Camera-/',
  '/Wanalab-Omni-Camera-/index.html',
  '/Wanalab-Omni-Camera-/manifest.json',
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
    fetch(event.request).catch(()=>caches.match(event.request))
  );
});
