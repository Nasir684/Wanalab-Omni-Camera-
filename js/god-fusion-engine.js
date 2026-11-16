// WanaLab God Eye - Emergency Fix + CamScan Engine v1.2
// Play Store Ready - Doc Scan + Camera Recovery
// Date: 2026-05-14 - Author: Nasir Khan
console.log("God Eye Engine v1.2 Loading... CamScan Ready");

window.GodEye = {
  video: null,
  docMode: false,
  opencvReady: false,
  
  init: async function() {
    try {
      const v = document.querySelector('video') || document.getElementById('godVideo') || document.getElementById('camera');
      if(!v){ console.log("No video tag"); return; }
      this.video = v;
      v.setAttribute('autoplay',''); 
      v.setAttribute('playsinline',''); 
      v.muted=true;
      v.setAttribute('muted','');
      
      const stream = await navigator.mediaDevices.getUserMedia({
        video:{facingMode:"environment", width:{ideal:1920}, height:{ideal:1080}}, 
        audio:false
      });
      v.srcObject = stream;
      await v.play();
      document.body.style.background="#000";
      console.log("✅ Camera LIVE - God Eye v1.2 Ready");
      
      // Check OpenCV for Doc Scan
      if(window.cv) {
        this.opencvReady = true;
        console.log("✅ OpenCV Ready - Doc Scan Available");
      }
      
      // Auto-recover if video freezes
      setInterval(() => {
        if(v && v.paused && v.srcObject) {
          v.play().catch(()=>{});
        }
      }, 3000);
      
    } catch(e){
      const msg = "Camera Error: "+e.message+" - Tap 🔒 in address bar → Allow Camera";
      console.error(msg);
      // Don't alert if user denied - show in UI instead
      const stat = document.getElementById('stat');
      if(stat) stat.innerText = "Allow Camera: Tap 🔒 → Allow";
    }
  },
  
  // === NEW: DOC SCAN QUICK CHECK ===
  isDocScanReady: function() {
    return this.opencvReady && window.cv;
  },
  
  // === NEW: Apply CamScan Filter via Emergency Engine ===
  applyQuickFilter: function(canvas, mode) {
    if(!window.cv) return;
    try {
      let src = cv.imread(canvas);
      let dst = new cv.Mat();
      switch(mode) {
        case 'bw':
          cv.cvtColor(src, dst, cv.COLOR_RGBA2GRAY);
          cv.threshold(dst, dst, 0, 255, cv.THRESH_BINARY + cv.THRESH_OTSU);
          cv.cvtColor(dst, src, cv.COLOR_GRAY2RGBA);
          break;
        case 'magic':
          cv.cvtColor(src, dst, cv.COLOR_RGBA2GRAY);
          cv.adaptiveThreshold(dst, dst, 255, cv.ADAPTIVE_THRESH_GAUSSIAN_C, cv.THRESH_BINARY, 15, 12);
          cv.cvtColor(dst, src, cv.COLOR_GRAY2RGBA);
          break;
        case 'enhance':
          cv.convertScaleAbs(src, src, 1.3, 15);
          break;
      }
      cv.imshow(canvas, src);
      src.delete(); dst.delete();
    } catch(e) {
      console.log("Filter error:", e);
    }
  }
};

// Auto-init on load
window.addEventListener('load', ()=>{ 
  setTimeout(()=>window.GodEye.init(), 600); 
});

// Click to recover camera (your fix - kept)
document.addEventListener('click', ()=>{ 
  if(window.GodEye.video && !window.GodEye.video.srcObject) {
    window.GodEye.init(); 
  }
}, {once:true});

// OpenCV loaded callback
if(window.cv) {
  cv['onRuntimeInitialized'] = () => {
    window.GodEye.opencvReady = true;
    console.log("✅ OpenCV Runtime Ready - Doc Scan ON");
  };
}
