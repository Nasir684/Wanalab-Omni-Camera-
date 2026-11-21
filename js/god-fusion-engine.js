// WanaLab God Eye - CamScan Engine v1.4.2
// Phone Camera + Doc Scan + Camera Recovery
// Date: 2026-11-21 - Author: Nasir Khan
// NOTE: Renamed from Emergency Fix v1.2 - this is NOT fusion, it's CamScan. Fusion is in god-fusion-engine.js
console.log("God Eye CamScan v1.4.2 Loading...");

window.GodEye = {
  video: null,
  docMode: false,
  opencvReady: false,
  stream: null,
  recoverTimer: null,
  isInitializing: false,

  init: async function() {
    if (this.isInitializing) return;
    this.isInitializing = true;
    try {
      const v = document.querySelector('video') || document.getElementById('godVideo') || document.getElementById('camera');
      if(!v){ console.log("No video tag"); this.isInitializing = false; return; }
      this.video = v;
      v.setAttribute('autoplay','');
      v.setAttribute('playsinline','');
      v.muted = true;
      v.setAttribute('muted','');

      // FIX: Stop old stream before starting new - was leaking 2 streams
      this.stop();

      const stream = await navigator.mediaDevices.getUserMedia({
        video:{facingMode:"environment", width:{ideal:1920}, height:{ideal:1080}},
        audio:false
      });
      this.stream = stream;
      v.srcObject = stream;
      await v.play();
      document.body.style.background="#000";
      console.log("✅ Camera LIVE - God Eye v1.4.2 Ready");

      // FIX: Check OpenCV correctly - if already loaded, mark ready immediately
      if(window.cv && window.cv.Mat) {
        this.opencvReady = true;
        console.log("✅ OpenCV Ready - Doc Scan Available");
      }

      // FIX: Clear old timer before starting new - was draining battery
      if (this.recoverTimer) clearInterval(this.recoverTimer);
      this.recoverTimer = setInterval(() => {
        if(v && v.paused && v.srcObject && document.visibilityState === 'visible') {
          v.play().catch(()=>{});
        }
      }, 3000);

    } catch(e){
      const msg = "Camera Error: "+e.message+" - Tap 🔒 in address bar → Allow Camera";
      console.error(msg);
      const stat = document.getElementById('stat');
      if(stat) stat.innerText = "Allow Camera: Tap 🔒 → Allow";
    } finally {
      this.isInitializing = false;
    }
  },

  // FIX: New - stop camera properly, clear timer
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

  // FIX: No memory leak - all Mats deleted in finally, separate src/dst
  applyQuickFilter: function(canvas, mode) {
    if(!window.cv || !window.cv.Mat) return;
    let src = null, gray = null, dst = null;
    try {
      src = cv.imread(canvas);
      dst = new cv.Mat();
      gray = new cv.Mat();
      switch(mode) {
        case 'bw':
          cv.cvtColor(src, gray, cv.COLOR_RGBA2GRAY);
          cv.threshold(gray, gray, 0, 255, cv.THRESH_BINARY + cv.THRESH_OTSU);
          cv.cvtColor(gray, dst, cv.COLOR_GRAY2RGBA);
          break;
        case 'magic':
          cv.cvtColor(src, gray, cv.COLOR_RGBA2GRAY);
          cv.adaptiveThreshold(gray, dst, 255, cv.ADAPTIVE_THRESH_GAUSSIAN_C, cv.THRESH_BINARY, 15, 12);
          // adaptiveThreshold outputs GRAY, need to convert to RGBA for imshow
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
    } catch(e) {
      console.log("Filter error:", e);
    } finally {
      // FIX: Delete ALL mats - was leaking after 20 scans
      try { if (src) src.delete(); } catch(e) {}
      try { if (gray) gray.delete(); } catch(e) {}
      try { if (dst) dst.delete(); } catch(e) {}
    }
  },

  // FIX: Hook for real fusion - call this from god-fusion-engine.js
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

// Auto-init on load
window.addEventListener('load', ()=>{
  setTimeout(()=>window.GodEye.init(), 600);
});

// FIX: Click to recover - was {once:true}, now retries every tap until success
document.addEventListener('click', ()=>{
  if(window.GodEye.video && !window.GodEye.video.srcObject) {
    window.GodEye.init();
  }
});

// FIX: Stop camera when tab hidden - saves battery on shop tablet
document.addEventListener('visibilitychange', () => {
  if (document.hidden) {
    // Don't stop fully, just pause recovery timer
    if (window.GodEye.recoverTimer) { clearInterval(window.GodEye.recoverTimer); window.GodEye.recoverTimer = null; }
  } else {
    if (window.GodEye.video && window.GodEye.video.srcObject && !window.GodEye.recoverTimer) {
      window.GodEye.init();
    }
  }
});

// FIX: OpenCV loaded callback - handle both cases: already loaded vs loading
if(window.cv) {
  if (window.cv.Mat) {
    // Already loaded
    window.GodEye.opencvReady = true;
    console.log("✅ OpenCV Runtime Ready - Doc Scan ON (already loaded)");
  } else {
    cv['onRuntimeInitialized'] = () => {
      window.GodEye.opencvReady = true;
      console.log("✅ OpenCV Runtime Ready - Doc Scan ON");
    };
  }
}
