// WanaLab God Eye v1.9 FINAL v99 - nasir684.github.io/Wanalab-Omni-Camera-/
const CACHE_NAME = 'wana-god-eye-v19-final-99-nasir684';
const VERSION = '1.9.99';
const FILES = [
  './',
  './index.html?v=19&force=99',
  './manifest.json?v=19',
  './export.html?v=19'
];

self.addEventListener('install', e=>{
  self.skipWaiting();
  e.waitUntil(
    caches.open(CACHE_NAME).then(c=>c.addAll(FILES).catch(()=>{})).then(()=>self.skipWaiting())
  );
});

self.addEventListener('activate', e=>{
  e.waitUntil(
    caches.keys().then(keys=>Promise.all(keys.map(k=>{
      if(k!==CACHE_NAME && k.toLowerCase().includes('wana')) return caches.delete(k);
    }))).then(()=>self.clients.claim())
  );
});

self.addEventListener('fetch', e=>{
  if(e.request.url.includes('chrome://') || e.request.url.includes('chrome-extension://')) return;
  // Always fresh for HTML
  if(e.request.destination === 'document' || e.request.url.includes('.html')){
    e.respondWith(fetch(e.request, {cache:'no-store'}).catch(()=>caches.match(e.request)));
    return;
  }
  e.respondWith(
    fetch(e.request, {cache:'no-store'}).then(r=>{
      if(r.ok && e.request.method==='GET'){
        const clone = r.clone();
        caches.open(CACHE_NAME).then(c=>c.put(e.request, clone));
      }
      return r;
    }).catch(()=>caches.match(e.request))
  );
});
