// WanaLab God Eye v2.2 - FLIR ONE SDK + RGB-Thermal Fusion
// Founder Nasir Khan - Dera Ismail Khan, Khyber Pakhtunkhwa - 2026-11-23
// Works with FLIR ONE Gen3/Pro/Edge
// WARNING: Browser cannot access real FLIR ONE via WebRTC. Real temp needs native Android FLIR SDK.
// This file provides SIMULATED thermal for demo + hook for native bridge.
window.flirEngine = {
  version: "2.2",
  thermalCanvas: null,
  rgbVideo: null,
  fusionMode: 'fusion', // thermal, rgb, fusion, msx
  isConnected: false,
  isSimulated: true, // FIX: Always true in browser - never claim real temp
  palette: 'iron',

  // v2.2 - Universal
  credentials: { shop: "", office: "", department: "" },
  gps_timestamp: true,
  gps_share: true,
  anti_fake_gps: true,
  wanalab_watermark: true,
  pricing: "PRO Rs. 2,500 / Year",

  init(rgbVideoElement) {
    this.rgbVideo = rgbVideoElement || document.getElementById('camera');
    this.thermalCanvas = document.createElement('canvas');
    this.thermalCanvas.id = 'thermalCanvas';
    this.thermalCanvas.style.cssText = 'position:absolute;top:0;left:0;width:100%;height:100%;opacity:0.7;mix-blend-mode:screen;pointer-events:none;';
    if (this.rgbVideo && this.rgbVideo.parentElement) {
      this.rgbVideo.parentElement.style.position = 'relative';
      this.rgbVideo.parentElement.appendChild(this.thermalCanvas);
    }
    this.checkFLIR();
    this.startSimulatedLoop();
  },

  checkFLIR() {
    if (window.FLIRNative && window.FLIRNative.isConnected) {
      this.isConnected = true;
      this.isSimulated = false;
      console.log('FLIR Native Bridge Connected - REAL temp available');
      return;
    }
    if (navigator.mediaDevices && navigator.mediaDevices.enumerateDevices) {
      navigator.mediaDevices.enumerateDevices().then(devices => {
        const flir = devices.find(d => (d.label || '').toLowerCase().includes('flir') || (d.label || '').toLowerCase().includes('thermal'));
        if (flir) {
          this.isConnected = true;
          console.log('FLIR Detected:', flir.label);
          const ov = document.getElementById('overlay');
          if (ov) ov.innerText += ' | FLIR CONNECTED (SIMULATED)';
        } else {
          console.log('FLIR not found via UVC - using SIMULATED thermal for demo');
        }
      }).catch(() => {});
    }
  },

  startSimulatedLoop() {
    if (this._loop) return;
    this._loop = setInterval(() => {
      if (!this.thermalCanvas || !this.rgbVideo) return;
      if (this.fusionMode === 'rgb') return;
      if (this.rgbVideo.readyState < 2) return;
      try {
        const ctx = this.thermalCanvas.getContext('2d');
        if (this.thermalCanvas.width !== this.rgbVideo.videoWidth) {
          this.thermalCanvas.width = this.rgbVideo.videoWidth || 640;
          this.thermalCanvas.height = this.rgbVideo.videoHeight || 480;
        }
        ctx.drawImage(this.rgbVideo, 0, 0, this.thermalCanvas.width, this.thermalCanvas.height);
      } catch (e) {}
    }, 100); // 10fps simulated - enough for demo, saves battery
  },

  setFusionMode(mode) {
    this.fusionMode = mode;
    if (!this.thermalCanvas) return;
    if (this.rgbVideo) this.rgbVideo.style.filter = '';
    switch (mode) {
      case 'thermal': this.thermalCanvas.style.opacity = '1'; if (this.rgbVideo) this.rgbVideo.style.opacity = '0.2'; break;
      case 'rgb': this.thermalCanvas.style.opacity = '0'; if (this.rgbVideo) this.rgbVideo.style.opacity = '1'; break;
      case 'fusion': this.thermalCanvas.style.opacity = '0.7'; if (this.rgbVideo) this.rgbVideo.style.opacity = '1'; break;
      case 'msx': this.thermalCanvas.style.opacity = '0.6'; if (this.rgbVideo) this.rgbVideo.style.filter = 'contrast(1.5) grayscale(0.5)'; break;
    }
    this.applyThermalPalette(this.palette);
  },

  applyThermalPalette(palette = 'iron') {
    this.palette = palette;
    if (!this.thermalCanvas) return;
    const palettes = {
      iron: 'hue-rotate(0deg) contrast(1.5) sepia(0.8)',
      rainbow: 'hue-rotate(180deg) saturate(2) contrast(1.3)',
      whitehot: 'grayscale(1) contrast(1.5) brightness(1.2)',
      blackhot: 'grayscale(1) invert(1) contrast(1.5)',
      jet: 'hue-rotate(200deg) saturate(2.5) contrast(1.4)',
      hot: 'hue-rotate(340deg) saturate(1.8) contrast(1.5)',
      bone: 'grayscale(1) sepia(0.3) contrast(1.3) brightness(1.1)',
      cool: 'hue-rotate(120deg) saturate(1.5) contrast(1.2)',
      ocean: 'hue-rotate(160deg) saturate(1.8) contrast(1.3)',
      inferno: 'hue-rotate(320deg) saturate(2.2) contrast(1.6)',
      JET: 'hue-rotate(200deg) saturate(2.5) contrast(1.4)',
      IRON: 'hue-rotate(0deg) contrast(1.5) sepia(0.8)',
      HOT: 'hue-rotate(340deg) saturate(1.8) contrast(1.5)',
      BONE: 'grayscale(1) sepia(0.3) contrast(1.3) brightness(1.1)',
      COOL: 'hue-rotate(120deg) saturate(1.5) contrast(1.2)',
      OCEAN: 'hue-rotate(160deg) saturate(1.8) contrast(1.3)',
      RAINBOW: 'hue-rotate(180deg) saturate(2) contrast(1.3)',
      WHITEHOT: 'grayscale(1) contrast(1.5) brightness(1.2)',
      BLACKHOT: 'grayscale(1) invert(1) contrast(1.5)',
      INFERNO: 'hue-rotate(320deg) saturate(2.2) contrast(1.6)'
    };
    this.thermalCanvas.style.filter = palettes[palette] || palettes[String(palette).toLowerCase()] || palettes.iron;
  },

  getTemperature(x, y) {
    if (window.FLIRNative && typeof window.FLIRNative.getTemperature === 'function') {
      try { return window.FLIRNative.getTemperature(x, y); } catch (e) { return null; }
    }
    console.warn("⚠️ getTemperature: SIMULATED mode - no real FLIR hardware, returning null. Do not use for fever / safety.");
    return null;
  },

  getTempLabel(x, y) {
    const t = this.getTemperature(x, y);
    if (t === null) return "SIMULATED - NOT REAL TEMP";
    return t + "°C";
  },

  stop() {
    if (this._loop) { clearInterval(this._loop); this._loop = null; }
  }
};

document.addEventListener('DOMContentLoaded', () => { window.flirEngine.init(); });
