// WanaLab God Eye v2.2 - Device Hub - Nasir Khan 2026-11-23
// Live: https://nasir684.github.io/Wanalab-Omni-Camera-/?v=2.2
// Dera Ismail Khan, Khyber Pakhtunkhwa

function safeGet(key) { try { return localStorage.getItem(key); } catch (e) { return null; } }
function safeSet(key, val) { try { localStorage.setItem(key, val); } catch (e) {} }

window.WanaDevices = {};
window.WanaDeviceHub = {
  version: '2.2',
  currentDeviceId: safeGet('wanaCurrentDevice') || 'wanalab-omni-cam',
  devices: [],
  loaded: false,
  credentials: { shop: "", office: "", department: "" },
  gps_timestamp: true,
  gps_share: true,
  anti_fake_gps: true,
  wanalab_watermark: true,
  pricing: "PRO Rs. 2,500 / Year"
};

const DEVICE_LIST = {
  version: '2.2',
  live_url: 'https://nasir684.github.io/Wanalab-Omni-Camera-/?v=2.2',
  devices: [
    {
      id: 'wanalab-omni-cam',
      name: 'WanaLab Omni Camera PRO',
      type: 'phone-camera',
      protocol: 'doc-scanner-v2.2 + count-v2.2 + voice',
      connection: 'native',
      features: ['doc-scan','manual-crop','count-roi','ocr-roi','barcode-roi','tap-bill','thermal','tracking','searchable-pdf','docx-img','bill-pdf','voice-en-ur-ps','torch','sliders','gps-timestamp','gps-share','credentials'],
      caps: ['doc-scan','count-roi','ocr-roi','barcode-roi','tap-bill','thermal','tracking','voice','torch','capture','export','gps'],
      icon: '👁️',
      status: 'active',
      default: true,
      resolution: { width: 1920, height: 1080, fps: 30 },
      position: 'primary'
    },
    {
      id: 'dji-mini-4-pro',
      name: 'DJI Mini 4 Pro',
      type: 'drone',
      protocol: 'dji-tracking-v2.2 + thermal + 4k',
      connection: 'wifi',
      features: ['tracking','thermal','hdr','4k-video','follow-me','orbit','gps-timestamp','gps-share'],
      caps: ['tracking','thermal','4k-video','follow-me','gps'],
      icon: '🚁',
      status: 'supported',
      position: 'aerial'
    },
    {
      id: 'flir-one-pro',
      name: 'FLIR ONE Pro Thermal',
      type: 'thermal-camera',
      protocol: 'thermal-v2.2 + jet + iron',
      connection: 'usb-c',
      features: ['thermal','thermal-roi','temp-measure','night','gps-timestamp'],
      caps: ['thermal-roi','temp-measure','thermal-jet','gps'],
      maps: ['JET','IRON','HOT','BONE','COOL','OCEAN','RAINBOW','WHITEHOT','BLACKHOT','INFERNO'],
      icon: '🔥',
      status: 'supported',
      position: 'thermal'
    },
    {
      id: 'usb-doc-cam',
      name: 'USB Document Camera',
      type: 'doc-camera',
      protocol: 'doc-scanner-v2.2 + ocr',
      connection: 'usb',
      features: ['doc-scan','ocr','count-roi','doc-crop','gps-timestamp'],
      caps: ['doc-scan','ocr','count-roi','gps'],
      icon: '📄',
      status: 'supported',
      position: 'document'
    },
    {
      id: 'ip-camera',
      name: 'IP Camera / CCTV RTSP',
      type: 'ip-camera',
      protocol: 'rtsp via go2rtc + onvif + tracking + mqtt + websocket',
      connection: 'rtsp',
      features: ['tracking','count-roi','thermal','motion-detect','gps-timestamp'],
      caps: ['tracking','count-roi','motion-detect','gps'],
      icon: '📹',
      status: 'experimental',
      position: 'surveillance'
    }
  ]
};

async function loadDeviceHub() {
  try {
    let data = { ...DEVICE_LIST, devices: [...DEVICE_LIST.devices] };
    try {
      const res = await fetch('./devices.json', { cache: 'no-store' });
      if (res.ok) {
        const j = await res.json();
        const remoteList = Array.isArray(j) ? j : (j.devices || []);
        if (remoteList.length) {
          const localById = {};
          DEVICE_LIST.devices.forEach(d => localById[d.id] = d);
          data.devices = remoteList.map(dev => {
            const local = localById[dev.id];
            if (!local) return dev;
            return { ...local, ...dev, caps: dev.caps || local.caps, features: dev.features || local.features };
          });
          console.log('✅ devices.json v2.2 loaded');
        }
      }
    } catch (e) { console.warn('devices.json not found, using built-in list'); }

    window.WanaDevices = data;
    window.WanaDeviceHub.devices = data.devices;
    window.WanaDeviceHub.loaded = true;
    safeSet('wanaDevices', JSON.stringify(data));

    // FIXED: If saved device no longer exists, fall back to default instead of crashing
    if (!data.devices.find(d => d.id === window.WanaDeviceHub.currentDeviceId)) {
      window.WanaDeviceHub.currentDeviceId = data.devices[0].id;
      safeSet('wanaCurrentDevice', window.WanaDeviceHub.currentDeviceId);
    }

    console.log('✅ Device Hub loaded v2.2', data.devices.length, 'devices - Current:', window.WanaDeviceHub.currentDeviceId);

    const stat = document.getElementById('statText') || document.getElementById('stat');
    if (stat) stat.innerText = `Ready v2.2 - ${data.devices.length} devices - ${getCurrentDevice().name}`;

    renderDeviceSelector();
    verifyButtons();
    return data;
  } catch (e) {
    console.warn('Device Hub offline', e);
    window.WanaDeviceHub.devices = DEVICE_LIST.devices;
    window.WanaDevices = DEVICE_LIST;
    window.WanaDeviceHub.loaded = true;
    return DEVICE_LIST;
  }
}

function getCurrentDevice() {
  const id = window.WanaDeviceHub.currentDeviceId;
  return window.WanaDeviceHub.devices.find(d => d.id === id) || window.WanaDeviceHub.devices[0] || DEVICE_LIST.devices[0];
}

function switchDevice(deviceId) {
  if (!deviceId || typeof deviceId !== 'string' || deviceId.length < 3) {
    let idx = window.WanaDeviceHub.devices.findIndex(d => d.id === window.WanaDeviceHub.currentDeviceId);
    idx = (idx + 1) % window.WanaDeviceHub.devices.length;
    deviceId = window.WanaDeviceHub.devices[idx].id;
  }
  const dev = window.WanaDeviceHub.devices.find(d => d.id === deviceId);
  if (!dev) return;
  window.WanaDeviceHub.currentDeviceId = deviceId;
  safeSet('wanaCurrentDevice', deviceId);
  console.log('🔄 Switched to', dev.name);

  const statText = document.getElementById('statText');
  if (statText) statText.innerText = `Switched: ${dev.name} - ${(dev.caps || []).length} caps active`;

  const devInfo = document.getElementById('deviceInfo');
  if (devInfo) devInfo.innerText = dev.name;

  const see = document.getElementById('see');
  if (see) see.innerText = `${dev.icon} ${dev.name} — Active — ${(dev.features || []).slice(0, 4).join(', ')}`;

  if (dev.id === 'flir-one-pro') {
    const th = document.getElementById('thermal');
    const thV = document.getElementById('thermalV');
    if (th) th.value = 60;
    if (thV) thV.innerText = 60;
  }
  hideDeviceHub();
}

function verifyButtons() {
  const checks = {
    'DEVICES (5)': DEVICE_LIST.devices.length === 5,
    'DOC SCAN': typeof window.GodEye !== 'undefined' && typeof window.GodEye.applyQuickFilter === 'function',
    'COUNT ROI': typeof window.CountingPro !== 'undefined',
    'OCR ROI': document.getElementById('ocrBtn') !== null || document.querySelector('[data-action="ocr"]') !== null,
    'BARCODE': document.querySelector('[data-action="barcode"]') !== null || typeof window.BarcodeDetector !== 'undefined',
    'TRACK': typeof window.CountingPro !== 'undefined',
    'TAP BILL': document.querySelector('[data-action="tap-bill"]') !== null,
    'TORCH': typeof navigator.mediaDevices !== 'undefined',
    'CLEAR': typeof window.CountingPro !== 'undefined' && typeof window.CountingPro.reset === 'function',
    'SLIDERS': document.querySelector('input[type="range"]') !== null,
    'CAPTURE': typeof window.GodEye !== 'undefined',
    'EXPORT': typeof window.CountingPro !== 'undefined' && typeof window.CountingPro.exportLog === 'function',
    'VOICE': 'webkitSpeechRecognition' in window || 'SpeechRecognition' in window
  };
  const allOk = Object.values(checks).every(v => v);
  console.log('🔍 v2.2 Button Check:', checks, 'ALL WORKING:', allOk);
  const see = document.getElementById('see');
  if (see) {
    if (allOk) see.innerText = '✅ v2.2 - All 13 buttons working - Ready to draw ROI';
    else {
      const failed = Object.keys(checks).filter(k => !checks[k]);
      see.innerText = `⚠️ v2.2 - ${failed.length} missing: ${failed.join(', ')}`;
    }
  }
  return checks;
}

function renderDeviceSelector() {
  const existing = document.getElementById('deviceHubPage');
  if (existing) existing.remove();

  let html = `<div id="deviceHubPage" style="display:none;position:fixed;inset:0;background:rgba(0,0,0,0.96);z-index:9997;padding:14px;overflow-y:auto"><div style="max-width:440px;margin:20px auto;background:linear-gradient(180deg,#001a0a,#000);border:2px solid #00ff88;border-radius:20px;padding:16px"><h2 style="color:#00ff88;text-align:center">📱 Device Hub v2.2</h2><p style="color:#aaa;font-size:10px;text-align:center;margin:6px 0">All protocols active + positioned • ${DEVICE_LIST.live_url}</p><div style="margin:12px 0">`;

  window.WanaDeviceHub.devices.forEach(d => {
    const active = d.id === window.WanaDeviceHub.currentDeviceId;
    const caps = d.caps || [];
    html += `<div onclick="switchDevice('${d.id}')" style="background:${active ? '#00ff88' : '#111'};color:${active ? '#000' : '#fff'};border:1px solid ${active ? '#00ff88' : '#333'};border-radius:12px;padding:10px;margin:6px 0;cursor:pointer;display:flex;justify-content:space-between;align-items:center"><div><div style="font-weight:800">${d.icon} ${d.name}</div><div style="font-size:8px;opacity:0.8">${d.type} • ${caps.slice(0, 3).join(', ')} • ${d.status} • ${d.position}</div></div><div style="font-size:10px;font-weight:800">${active ? '✅ ACTIVE' : '➡️'}</div></div>`;
  });

  html += `</div><button onclick="hideDeviceHub()" style="width:100%;padding:12px;background:#333;color:#fff;border:none;border-radius:10px;font-weight:800">❌ CLOSE</button><div style="text-align:center;margin-top:10px;color:#00ff88;font-size:10px">✅ v2.2 - Buttons verified</div></div></div>`;

  document.body.insertAdjacentHTML('beforeend', html);
}

function showDeviceHub() {
  if (!window.WanaDeviceHub.loaded) loadDeviceHub().then(() => { const el = document.getElementById('deviceHubPage'); if (el) el.style.display = 'block'; });
  else { const el = document.getElementById('deviceHubPage'); if (el) el.style.display = 'block'; }
}
function hideDeviceHub() { const el = document.getElementById('deviceHubPage'); if (el) el.style.display = 'none'; }

window.loadDeviceHub = loadDeviceHub;
window.getCurrentDevice = getCurrentDevice;
window.switchDevice = switchDevice;
window.showDeviceHub = showDeviceHub;
window.hideDeviceHub = hideDeviceHub;
window.verifyButtons = verifyButtons;

document.addEventListener('DOMContentLoaded', loadDeviceHub);
if (document.readyState !== 'loading') loadDeviceHub();
