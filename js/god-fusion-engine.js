// WanaLab God Eye - CamScan Engine v2.2
// Phone Camera + Doc Scan + Camera Recovery
// Date: 2026-11-23 - Author: Nasir Khan - Dera Ismail Khan, Khyber Pakhtunkhwa
// NOTE: Renamed from Emergency Fix v1.2 - this is NOT fusion, it's CamScan. Fusion is in god-fusion-engine.js
console.log("God Eye CamScan v2.2 Loading...");

window.GodEye = {
  version: "2.2",
  video: null,
  docMode: false,
  opencvReady: false,
  stream: null,
  recoverTimer: null,
  isInitializing: false,

  // v2.2 - Universal
  credentials: { shop: "", office: "", department: "" },
  gps_timestamp: true,
  gps_share: true,
  anti_fake_gps: true,
  wanalab_watermark: true,
  pricing: "PRO Rs. 2,500 / Year",

  init: async function() {
    if (this.isInitializing) return;
    this.isInitializing = true;
    try {
      const v = document.querySelector('video') || document.getElementById('godVideo') || document.getElementById('camera');
      if (!v) { console.log("No video tag"); this.isInitializing = false; return; }
      this.video = v;
      v.setAttribute('autoplay', '');
      v.setAttribute('playsinline', '');
      v.muted = true;
      v.setAttribute('muted', '');

      this.stop();

      const stream = await navigator.mediaDevices.getUserMedia({
        video: { facingMode: "environment", width: { ideal: 1920 }, height: { ideal: 1080 } },
        audio: false
      });
      this.stream = stream;
      v.srcObject = stream;
      await v.play();
      document.body.style.background = "#000";
      console.log("✅ Camera LIVE - God Eye v2.2 Ready");

      if (window.cv && window.cv.Mat) {
        this.opencvReady = true;
        console.log("✅ OpenCV Ready - Doc Scan Available");
      }

      if (this.recoverTimer) clearInterval(this.recoverTimer);
      this.recoverTimer = setInterval(() => {
        if (v && v.paused && v.srcObject && document.visibilityState === 'visible') {
          v.play().catch(() => {});
        }
      }, 3000);

    } catch (e) {
      const msg = "Camera Error: " + e.message + " - Tap 🔒 in address bar → Allow Camera";
      console.error(msg);
      const stat = document.getElementById('stat');
      if (stat) stat.innerText = "Allow Camera: Tap 🔒 → Allow";
    } finally {
      this.isInitializing = false;
    }
  },

  stop: function() {
    if (this.recoverTimer) { clearInterval(this.recoverTimer); this.recoverTimer = null; }
    if (this.stream) {
      this.stream.getTracks().forEach(t => t.stop());
      this.stream = null;
    }
    if (this.video) this.video.srcObject = null;
  },

  isDocScanReady: function() {
    return this.opencvReady && window.cv && window.cv.Mat;
  },

  applyQuickFilter: function(canvas, mode) {
    if (!window.cv || !window.cv.Mat) return;
    let src = null, gray = null, dst = null;
    try {
      src = cv.imread(canvas);
      dst = new cv.Mat();
      gray = new cv.Mat();
      switch (mode) {
        case 'bw':
          cv.cvtColor(src, gray, cv.COLOR_RGBA2GRAY);
          cv.threshold(gray, gray, 0, 255, cv.THRESH_BINARY + cv.THRESH_OTSU);
          cv.cvtColor(gray, dst, cv.COLOR_GRAY2RGBA);
          break;
        case 'magic':
          cv.cvtColor(src, gray, cv.COLOR_RGBA2GRAY);
          cv.adaptiveThreshold(gray, dst, 255, cv.ADAPTIVE_THRESH_GAUSSIAN_C, cv.THRESH_BINARY, 15, 12);
          let rgba = new cv.Mat();
          cv.cvtColor(dst, rgba, cv.COLOR_GRAY2RGBA);
          dst.delete();
          dst = rgba;
          break;
        case 'enhance':
          cv.convertScaleAbs(src, dst, 1.3, 15);
          break;
        default:
          src.copyTo(dst);
          break;
      }
      cv.imshow(canvas, dst);
    } catch (e) {
      console.log("Filter error:", e);
    } finally {
      try { if (src) src.delete(); } catch (e) {}
      try { if (gray) gray.delete(); } catch (e) {}
      try { if (dst) dst.delete(); } catch (e) {}
    }
  },

  getFrameForFusion: function(canvas) {
    if (!this.video || this.video.readyState < 2) return null;
    const c = canvas || document.createElement('canvas');
    c.width = this.video.videoWidth;
    c.height = this.video.videoHeight;
    const ctx = c.getContext('2d');
    ctx.drawImage(this.video, 0, 0, c.width, c.height);
    return c;
  }
};

window.addEventListener('load', () => {
  setTimeout(() => window.GodEye.init(), 600);
});

document.addEventListener('click', () => {
  if (window.GodEye.video && !window.GodEye.video.srcObject) {
    window.GodEye.init();
  }
});

document.addEventListener('visibilitychange', () => {
  if (document.hidden) {
    if (window.GodEye.recoverTimer) { clearInterval(window.GodEye.recoverTimer); window.GodEye.recoverTimer = null; }
  } else {
    if (window.GodEye.video && window.GodEye.video.srcObject && !window.GodEye.recoverTimer) {
      window.GodEye.init();
    }
  }
});

if (window.cv) {
  if (window.cv.Mat) {
    window.GodEye.opencvReady = true;
    console.log("✅ OpenCV Runtime Ready - Doc Scan ON (already loaded)");
  } else {
    cv['onRuntimeInitialized'] = () => {
      window.GodEye.opencvReady = true;
      console.log("✅ OpenCV Runtime Ready - Doc Scan ON");
    };
  }
}
