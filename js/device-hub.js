// WanaLab God Eye v1.9 FINAL v99 - Device Hub - Nasir Khan 2026-05-14
// Live: https://nasir684.github.io/Wanalab-Omni-Camera-/?v=19&force=99
window.WanaDevices = {};
window.WanaDeviceHub = {
  version: '1.9.99-nasir684',
  currentDeviceId: localStorage.getItem('wanaCurrentDevice') || 'wanalab-omni-cam',
  devices: [],
  loaded: false
};

const DEVICE_LIST = {
  version: '1.9.99-nasir684',
  live_url: 'https://nasir684.github.io/Wanalab-Omni-Camera-/?v=19&force=99',
  devices: [
    {
      id: 'wanalab-omni-cam',
      name: 'WanaLab Omni Camera (Phone)',
      type: 'phone-camera',
      protocol: 'doc-scanner-v1',
      connection: 'native',
      features: ['doc-scan','manual-crop','count-roi','ocr-roi','barcode-roi','tap-bill','thermal','tracking','searchable-pdf','docx-img','bill-pdf'],
      icon: '👁️',
      status: 'active',
      default: true,
      resolution: { width: 1920, height: 1080, fps: 30 }
    },
    {
      id: 'dji-mini-4-pro',
      name: 'DJI Mini 4 Pro',
      type: 'drone',
      protocol: 'dji-tracking-v1',
      connection: 'wifi',
      features: ['tracking','thermal','hdr','4k-video','follow-me'],
      icon: '🚁',
      status: 'supported'
    },
    {
      id: 'flir-one-pro',
      name: 'FLIR ONE Pro Thermal',
      type: 'thermal-camera',
      protocol: 'thermal-v1',
      connection: 'usb-c',
      features: ['thermal','thermal-roi','temp-measure','night'],
      maps: ['JET','IRON','HOT','BONE','COOL','OCEAN'],
      icon: '🔥',
      status: 'supported'
    },
    {
      id: 'usb-doc-cam',
      name: 'USB Document Camera',
      type: 'doc-camera',
      protocol: 'doc-scanner-v1',
      connection: 'usb',
      features: ['doc-scan','ocr','count-roi','doc-crop'],
      icon: '📄',
      status: 'supported'
    },
    {
      id: 'ip-camera',
      name: 'IP Camera / CCTV RTSP',
      type: 'ip-camera',
      protocol: 'dji-tracking-v1',
      connection: 'rtsp',
      features: ['tracking','count-roi','thermal','motion-detect'],
      icon: '📹',
      status: 'experimental'
    }
  ]
};

async function loadDeviceHub(){
  try{
    // Try fetch devices.json if exists, else use built-in list
    let data = DEVICE_LIST;
    try{
      const res = await fetch('./devices.json?v=19&force=99', {cache:'no-store'});
      if(res.ok) data = await res.json();
    }catch{}

    window.WanaDevices = data;
    window.WanaDeviceHub.devices = data.devices;
    window.WanaDeviceHub.loaded = true;

    // Save cache
    localStorage.setItem('wanaDevices', JSON.stringify(data));
    
    console.log('✅ Device Hub loaded v99', data.devices.length, 'devices - Current:', window.WanaDeviceHub.currentDeviceId);
    
    const stat = document.getElementById('stat');
    if(stat) stat.innerText = `Ready v99 - ${data.devices.length} devices - ${getCurrentDevice().name}`;

    renderDeviceSelector();
    return data;
  }catch(e){
    console.warn('Device Hub offline', e);
    window.WanaDeviceHub.devices = DEVICE_LIST.devices;
    window.WanaDevices = DEVICE_LIST;
    window.WanaDeviceHub.loaded = true;
    return DEVICE_LIST;
  }
}

function getCurrentDevice(){
  const id = window.WanaDeviceHub.currentDeviceId;
  return window.WanaDeviceHub.devices.find(d=>d.id===id) || window.WanaDeviceHub.devices[0];
}

function switchDevice(deviceId){
  const dev = window.WanaDeviceHub.devices.find(d=>d.id===deviceId);
  if(!dev) return;
  window.WanaDeviceHub.currentDeviceId = deviceId;
  localStorage.setItem('wanaCurrentDevice', deviceId);
  console.log('🔄 Switched to', dev.name);
  document.getElementById('stat').innerText = `Switched: ${dev.name} - ${dev.features.length} features`;
  // Apply device-specific settings
  if(dev.id==='flir-one-pro'){
    if(window.toggleThermal) window.toggleThermal(60);
  }
  if(dev.id==='dji-mini-4-pro'){
    if(window.toggleTracking) window.toggleTracking();
  }
  hideDeviceHub();
}

function renderDeviceSelector(){
  let existing = document.getElementById('deviceHubPage');
  if(existing) existing.remove();
  
  let html = `<div id="deviceHubPage" style="display:none;position:fixed;inset:0;background:rgba(0,0,0,0.96);z-index:9997;padding:14px;overflow-y:auto"><div style="max-width:440px;margin:20px auto;background:linear-gradient(180deg,#001a0a,#000);border:2px solid #00ff88;border-radius:20px;padding:16px"><h2 style="color:#00ff88;text-align:center">📱 Device Hub v99</h2><p style="color:#aaa;font-size:10px;text-align:center;margin:6px 0">https://nasir684.github.io/Wanalab-Omni-Camera-/?v=19&force=99</p><div style="margin:12px 0">`;
  
  window.WanaDeviceHub.devices.forEach(d=>{
    const active = d.id===window.WanaDeviceHub.currentDeviceId;
    html+=`<div onclick="switchDevice('${d.id}')" style="background:${active?'#00ff88':'#111'};color:${active?'#000':'#fff'};border:1px solid ${active?'#00ff88':'#333'};border-radius:12px;padding:10px;margin:6px 0;cursor:pointer;display:flex;justify-content:space-between;align-items:center"><div><div style="font-weight:800">${d.icon} ${d.name}</div><div style="font-size:8px;opacity:0.8">${d.type} • ${d.features.slice(0,3).join(', ')} • ${d.status}</div></div><div style="font-size:10px;font-weight:800">${active?'✅ ACTIVE':'➡️'}</div></div>`;
  });
  
  html+=`</div><button onclick="hideDeviceHub()" style="width:100%;padding:12px;background:#333;color:#fff;border:none;border-radius:10px;font-weight:800">❌ CLOSE</button><a href="https://nasir684.github.io/Wanalab-Omni-Camera-/?v=19&force=99" style="display:block;text-align:center;margin-top:10px;color:#00ff88;font-size:10px">🧪 TEST LINK v99</a></div></div>`;
  
  document.body.insertAdjacentHTML('beforeend', html);
}

function showDeviceHub(){ 
  if(!window.WanaDeviceHub.loaded) loadDeviceHub().then(()=>{document.getElementById('deviceHubPage').style.display='block';});
  else document.getElementById('deviceHubPage').style.display='block';
}
function hideDeviceHub(){ document.getElementById('deviceHubPage').style.display='none'; }

// Expose globally
window.loadDeviceHub = loadDeviceHub;
window.getCurrentDevice = getCurrentDevice;
window.switchDevice = switchDevice;
window.showDeviceHub = showDeviceHub;
window.hideDeviceHub = hideDeviceHub;

// Auto load
document.addEventListener('DOMContentLoaded', loadDeviceHub);
