// Protocol Universe Loader v1.9 FINAL v99 - Nasir Khan
window.WanaProtocols = {};
async function loadProtocols(){
  try{
    const res = await fetch('./protocols/index.json?v=19&force=99', {cache:'no-store'});
    const data = await res.json();
    window.WanaProtocols.index = data;
    console.log('✅ Protocols loaded v99', data.version, data.protocols.length);
    // preload all protocol json
    for(let p of data.protocols){
      try{
        let r = await fetch(p.file, {cache:'no-store'});
        window.WanaProtocols[p.id] = await r.json();
      }catch{}
    }
    document.getElementById('stat').innerText = `Ready v99 - ${data.protocols.length} protocols`;
    return data;
  }catch(e){
    console.error('Protocol load failed', e);
    document.getElementById('stat').innerText = 'Ready v99 - protocols offline';
  }
}
window.loadProtocols = loadProtocols;
document.addEventListener('DOMContentLoaded', loadProtocols);
