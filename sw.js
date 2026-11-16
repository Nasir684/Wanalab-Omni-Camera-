// WanaLab God Eye v99.2 EMERGENCY - Cam+Voice+Count Fixed - nasir684
const CACHE_NAME = 'wana-god-eye-v19-final-992-emergency';
const VERSION = '1.9.99.2';

self.addEventListener('install', e=>{
  self.skipWaiting();
  console.log('SW v99.2 installing - KILLING OLD CACHE');
  e.waitUntil(
    caches.keys().then(keys=>Promise.all(keys.map(k=>{
      if(k.includes('wana')) return caches.delete(k);
    }))).then(()=>self.skipWaiting())
  );
});

self.addEventListener('activate', e=>{
  console.log('SW v99.2 activating');
  e.waitUntil(
    caches.keys().then(keys=>Promise.all(keys.map(k=>{
      if(k!==CACHE_NAME && k.includes('wana')) return caches.delete(k);
    }))).then(()=>self.clients.claim())
  );
});

self.addEventListener('fetch', e=>{
  // Always network-first for HTML to fix cam issues
  if(e.request.destination === 'document' || e.request.url.includes('index.html')){
    e.respondWith(
      fetch(e.request, {cache:'no-store'})
        .then(r=>r)
        .catch(()=>caches.match(e.request))
    );
    return;
  }
  e.respondWith(fetch(e.request).catch(()=>caches.match(e.request)));
});
