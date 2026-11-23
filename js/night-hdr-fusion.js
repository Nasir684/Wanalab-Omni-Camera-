// WanaLab God Eye v2.2 - Night God HDR Fusion
// Real 5-frame fusion with canvas filter bracketing
// Author: Nasir Khan - Dera Ismail Khan, Khyber Pakhtunkhwa - 2026-11-23
window.nightGodEngine = {
  version: "2.2",
  video: null,
  canvas: null,
  ctx: null,
  frames: [],
  isCapturing: false,

  // v2.2 - Universal
  credentials: { shop: "", office: "", department: "" },
  gps_timestamp: true,
  gps_share: true,
  anti_fake_gps: true,
  wanalab_watermark: true,
  pricing: "PRO Rs. 2,500 / Year",

  init() {
    this.video = document.getElementById('camera');
    this.canvas = document.getElementById('canvas');
    if (this.canvas) this.ctx = this.canvas.getContext('2d');
  },

  // NOTE v2.2: Name kept as capture10Frames for button compatibility - Actually captures 5 frames
  async capture10Frames() {
    if (!this.video) this.init();
    if (this.isCapturing) return;
    if (!this.video || this.video.readyState < 2) {
      const ov = document.getElementById('overlay');
      if (ov) ov.innerText = 'NIGHT GOD | Camera not ready';
      return;
    }
    this.isCapturing = true;
    this.frames = [];
    const overlay = document.getElementById('overlay');
    const setOverlay = (t) => { if (overlay) overlay.innerText = t; };

    const old = document.getElementById('nightResult');
    if (old) old.remove();

    const maxW = 960;
    const scale = Math.min(1, maxW / (this.video.videoWidth || 1280));
    const fw = Math.floor((this.video.videoWidth || 1280) * scale);
    const fh = Math.floor((this.video.videoHeight || 720) * scale);

    const capCanvas = document.createElement('canvas');
    capCanvas.width = fw; capCanvas.height = fh;
    const capCtx = capCanvas.getContext('2d');

    setOverlay('NIGHT GOD | Hold still - CAPTURING 5 FRAMES...');
    const exposures = [0.6, 1.0, 1.4, 1.8, 2.2];
    for (let i = 0; i < exposures.length; i++) {
      const exposure = exposures[i];
      capCtx.filter = `brightness(${exposure}) contrast(1.2)`;
      capCtx.drawImage(this.video, 0, 0, fw, fh);
      capCtx.filter = 'none';
      const frame = capCtx.getImageData(0, 0, fw, fh);
      this.frames.push(frame);
      setOverlay(`NIGHT GOD | FRAME ${i + 1}/5 CAPTURED`);
      await this.sleep(60);
    }

    setOverlay('NIGHT GOD | FUSING 5 FRAMES...');
    await this.sleep(10);
    const fused = await this.fuseFrames();
    this.showFused(fused, fw, fh);
    setOverlay('NIGHT GOD | FUSION COMPLETE - Shadows lifted');
    this.isCapturing = false;
    this.frames = [];
    if (this.video) this.video.style.filter = 'none';
  },

  grabFrame() {
    if (!this.canvas || !this.video) return null;
    this.canvas.width = this.video.videoWidth || 1280;
    this.canvas.height = this.video.videoHeight || 720;
    this.ctx.drawImage(this.video, 0, 0);
    return this.ctx.getImageData(0, 0, this.canvas.width, this.canvas.height);
  },

  async fuseFrames() {
    if (this.frames.length === 0) return null;
    const w = this.frames[0].width, h = this.frames[0].height;
    const outCtx = this.ctx || document.createElement('canvas').getContext('2d');
    const fused = outCtx.createImageData(w, h);
    const n = this.frames.length;
    const total = fused.data.length;
    for (let p = 0; p < total; p += 4) {
      let r = 0, g = 0, b = 0;
      for (let f of this.frames) { r += f.data[p]; g += f.data[p + 1]; b += f.data[p + 2]; }
      r /= n; g /= n; b /= n;
      const lift = (v) => {
        if (v < 60) return Math.min(255, v * 1.8 + 10);
        if (v < 120) return Math.min(255, v * 1.35 + 5);
        return v;
      };
      fused.data[p] = lift(r);
      fused.data[p + 1] = lift(g);
      fused.data[p + 2] = lift(b);
      fused.data[p + 3] = 255;
      if (p % 800000 === 0 && p > 0) await this.sleep(0);
    }
    return fused;
  },

  showFused(imageData, w, h) {
    if (!imageData) return;
    if (this.canvas && this.ctx) {
      this.canvas.width = w || imageData.width;
      this.canvas.height = h || imageData.height;
      this.ctx.putImageData(imageData, 0, 0);
    }
    const resultImg = document.createElement('img');
    resultImg.src = this.canvas ? this.canvas.toDataURL('image/jpeg', 0.9) : '';
    resultImg.style.cssText = 'position:absolute;top:0;left:0;width:100%;height:100%;z-index:20;border:3px solid #0ff;object-fit:contain;background:#000';
    resultImg.id = 'nightResult';
    resultImg.onclick = () => resultImg.remove();
    if (this.video && this.video.parentElement) this.video.parentElement.appendChild(resultImg);
    const ov = document.getElementById('overlay');
    if (ov) ov.innerText = 'NIGHT GOD FUSION DONE - Tap image to close';
  },

  sleep(ms) { return new Promise(r => setTimeout(r, ms)); }
};
