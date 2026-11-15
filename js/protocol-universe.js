// WanaLab God Eye v1.0 - Protocol Universe - 1 Repo Working Version - Global
// Founder Nasir Khan | Nasir@wanalab.org
window.protocolUniverse = {
  protocols: [],
  loaded: false,
  baseUrl: null,

  async init(){
    const universeEl = document.getElementById('universe');
    this.baseUrl = window.location.origin + window.location.pathname.replace(/\/[^\/]*$/, '/');
    // Normalize for GitHub pages / Vercel / Local
    let root = this.baseUrl;
    if(root.endsWith('js/')) root = root.replace('js/','');
    if(!root.endsWith('/')) root += '/';
    
    const localIndex = root + 'protocols/index.json';
    const rawFallback = 'https://raw.githubusercontent.com/wanalab/wana-omni-camera/main/protocols/index.json';

    try{
      let indexData = null;
      try{
        const r = await fetch(localIndex, {cache:'no-store'});
        if(!r.ok) throw 0;
        indexData = await r.json();
      }catch{
        // Fallback to raw github (if local fails)
        const r2 = await fetch(rawFallback, {cache:'no-store'});
        indexData = await r2.json();
        root = 'https://raw.githubusercontent.com/wanalab/wana-omni-camera/main/';
      }

      let all = [];
      for(const file of indexData.files){
        try{
          let finalUrl;
          if(root.includes('raw.githubusercontent')){
            finalUrl = `https://raw.githubusercontent.com/wanalab/wana-omni-camera/main/protocols/${file}`;
          } else {
            finalUrl = root + 'protocols/' + file;
          }
          const r = await fetch(finalUrl, {cache:'no-store'});
          if(r.ok){
            const data = await r.json();
            if(Array.isArray(data)){
              const clean = data.filter(d=>d.id && !d._comment);
              all = all.concat(clean);
            }
          }
        }catch(e){ console.log('protocol file fail', file, e); }
      }

      this.protocols = all;
      this.loaded = true;
      localStorage.setItem('wanalab_protocols_cache', JSON.stringify({time: Date.now(), data: all}));

      if(universeEl){
        universeEl.innerText = `UNIVERSE ACTIVE: ${all.length} PROTOCOLS LOADED | OFFLINE+ONLINE | GLOBAL`;
        universeEl.style.background = '#22d3ee';
        universeEl.style.color = '#000';
        universeEl.style.fontWeight = 'bold';
      }
      console.log(`[WanaLab God Eye] Universe loaded: ${all.length} protocols`, all);

    }catch(e){
      // Offline fallback
      const cached = localStorage.getItem('wanalab_protocols_cache');
      if(cached){
        try{
          const parsed = JSON.parse(cached);
          this.protocols = parsed.data;
          this.loaded = true;
          if(universeEl) universeEl.innerText = `OFFLINE CACHE: ${this.protocols.length} PROTOCOLS | Connect for update`;
        }catch{}
      } else {
        if(universeEl) universeEl.innerText = `OFFLINE CORE: 10 MODES + USB UVC READY | Connect for 24+ Protocols`;
      }
      console.log('[WanaLab] Universe offline, core active', e);
    }
  },

  findForDevice(vendorId, type){
    return this.protocols.filter(p=> (p.vendorIds && p.vendorIds.includes(vendorId)) || p.type===type );
  },

  getAll(){ return this.protocols; },
  getByMode(mode){ return this.protocols.filter(p=>p.mode===mode); }
};

// Auto init
document.addEventListener('DOMContentLoaded', ()=>{ if(window.protocolUniverse) window.protocolUniverse.init(); });
