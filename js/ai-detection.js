// WanaLab God Eye PRO v1.9 FINAL - AI Detection + Tracking + ROI + Thermal - Founder Nasir Khan
window.aiEngine = {
  model: null,
  isLoaded: false,
  aiCanvas: null,
  ctx: null,
  video: null,
  tracking: false,
  trackedBox: null,
  trackTemplate: null,
  countROI: null,
  ocrROI: null,
  barcodeROI: null,
  thermalMode: false,

  async init(){
    this.aiCanvas = document.getElementById('aiCanvas') || document.getElementById('canvas');
    this.video = document.getElementById('godVideo') || document.getElementById('camera');
    if(this.aiCanvas){
      this.ctx = this.aiCanvas.getContext('2d');
      this.aiCanvas.width = this.video ? this.video.videoWidth || 1280 : 1280;
      this.aiCanvas.height = this.video ? this.video.videoHeight || 720 : 720;
    }
    const overlay = document.getElementById('overlay') || document.getElementById('stat') || document.getElementById('see');
    if(overlay) overlay.innerText = 'AI v1.9 READY - Manual Crop + ROI + Tracking + Thermal + Tap Bill';
    this.isLoaded = true;
    console.log('AI Engine v1.9 FINAL Loaded - Tracking + Count ROI + Thermal + OCR ROI + Barcode ROI');
    // Pre-load OpenCV check
    if(window.cv && window.cv.Mat) console.log('OpenCV Ready for v1.9 tracking/count/thermal');
  },

  // ===== DJI TRACKING - TAP TO LOCK =====
  startTracking(x, y){
    if(!window.cv || !this.video) { alert('OpenCV loading - wait 1 sec'); return; }
    try{
      const canvas = document.createElement('canvas');
      canvas.width = this.video.videoWidth; canvas.height = this.video.videoHeight;
      canvas.getContext('2d').drawImage(this.video,0,0);
      const src = cv.imread(canvas);
      const gray = new cv.Mat();
      cv.cvtColor(src, gray, cv.COLOR_RGBA2GRAY);
      const size = 140;
      const tx = Math.max(0, x - size/2), ty = Math.max(0, y - size/2);
      this.trackedBox = {x: tx, y: ty, w: size, h: size};
      const roi = gray.roi(new cv.Rect(tx, ty, size, size));
      this.trackTemplate = new cv.Mat();
      roi.copyTo(this.trackTemplate);
      src.delete(); gray.delete(); roi.delete();
      this.tracking = true;
      document.getElementById('trackInfo').innerText = `TRACK LOCKED @ ${Math.round(x)},${Math.round(y)}`;
      this.followLoop();
    }catch(e){ console.log('Tracking init error', e); }
  },

  followLoop(){
    if(!this.tracking || !this.trackedBox || !this.trackTemplate) return;
    try{
      const canvas = document.createElement('canvas');
      canvas.width = this.video.videoWidth; canvas.height = this.video.videoHeight;
      canvas.getContext('2d').drawImage(this.video,0,0);
      const src = cv.imread(canvas);
      const gray = new cv.Mat();
      cv.cvtColor(src, gray, cv.COLOR_RGBA2GRAY);
      const sx = Math.max(0, this.trackedBox.x - 80), sy = Math.max(0, this.trackedBox.y - 80);
      const searchW = this.trackedBox.w + 160, searchH = this.trackedBox.h + 160;
      const searchROI = gray.roi(new cv.Rect(sx, sy, searchW, searchH));
      const result = new cv.Mat();
      cv.matchTemplate(searchROI, this.trackTemplate, result, cv.TM_CCOEFF_NORMED);
      const mm = cv.minMaxLoc(result);
      this.trackedBox.x = sx + mm.maxLoc.x;
      this.trackedBox.y = sy + mm.maxLoc.y;
      // Draw DJI style corners
      this.ctx.clearRect(0,0,this.aiCanvas.width, this.aiCanvas.height);
      const bx = this.trackedBox.x, by = this.trackedBox.y, bw = this.trackedBox.w, bh = this.trackedBox.h;
      this.ctx.strokeStyle = '#00ff88'; this.ctx.lineWidth = 3;
      // Corners
      const cl = 20;
      this.ctx.beginPath();
      this.ctx.moveTo(bx, by+cl); this.ctx.lineTo(bx, by); this.ctx.lineTo(bx+cl, by);
      this.ctx.moveTo(bx+bw-cl, by); this.ctx.lineTo(bx+bw, by); this.ctx.lineTo(bx+bw, by+cl);
      this.ctx.moveTo(bx+bw, by+bh-cl); this.ctx.lineTo(bx+bw, by+bh); this.ctx.lineTo(bx+bw-cl, by+bh);
      this.ctx.moveTo(bx+cl, by+bh); this.ctx.lineTo(bx, by+bh); this.ctx.lineTo(bx, by+bh-cl);
      this.ctx.stroke();
      // Center dot + confidence
      this.ctx.fillStyle = '#00ff88';
      this.ctx.beginPath(); this.ctx.arc(bx+bw/2, by+bh/2, 4, 0, Math.PI*2); this.ctx.fill();
      this.ctx.font = '12px monospace'; this.ctx.fillText(`${Math.round(mm.maxVal*100)}% LOCKED`, bx, by-8);
      src.delete(); gray.delete(); searchROI.delete(); result.delete();
    }catch(e){}
    if(this.tracking) requestAnimationFrame(()=>this.followLoop());
  },

  stopTracking(){
    this.tracking = false; this.trackedBox = null;
    if(this.trackTemplate) { try{this.trackTemplate.delete();}catch{} this.trackTemplate=null; }
    if(this.ctx) this.ctx.clearRect(0,0,this.aiCanvas.width, this.aiCanvas.height);
    const el = document.getElementById('trackInfo'); if(el) el.innerText = '';
  },

  // ===== COUNT ROI - COUNT OBJECTS INSIDE BOX =====
  countInROI(roi){
    if(!window.cv || !roi) return 0;
    try{
      const canvas = document.createElement('canvas');
      canvas.width = roi.w; canvas.height = roi.h;
      canvas.getContext('2d').drawImage(this.video, roi.x, roi.y, roi.w, roi.h, 0,0,roi.w, roi.h);
      const src = cv.imread(canvas);
      const gray = new cv.Mat(), thresh = new cv.Mat();
      cv.cvtColor(src, gray, cv.COLOR_RGBA2GRAY);
      cv.threshold(gray, thresh, 0,255, cv.THRESH_BINARY_INV + cv.THRESH_OTSU);
      const contours = new cv.MatVector(), hier = new cv.Mat();
      cv.findContours(thresh, contours, hier, cv.RETR_EXTERNAL, cv.CHAIN_APPROX_SIMPLE);
      let cnt=0;
      for(let i=0;i<contours.size();i++){ if(cv.contourArea(contours.get(i))>80) cnt++; }
      src.delete(); gray.delete(); thresh.delete(); contours.delete(); hier.delete();
      const el = document.getElementById('countDisplay'); if(el) el.innerText = `COUNT ROI: ${cnt} objects`;
      return cnt;
    }catch(e){ return 0; }
  },

  // ===== OCR ROI - TEXT ONLY INSIDE BOX =====
  async ocrInROI(roi){
    if(!roi) return '';
    const canvas = document.createElement('canvas');
    canvas.width = roi.w; canvas.height = roi.h;
    canvas.getContext('2d').drawImage(this.video, roi.x, roi.y, roi.w, roi.h, 0,0,roi.w, roi.h);
    try{
      const worker = await Tesseract.createWorker('eng');
      const ret = await worker.recognize(canvas);
      await worker.terminate();
      const text = ret.data.text;
      const box = document.getElementById('ocrBox'); if(box) box.innerText = `OCR ROI: ${text.slice(0,200)}`;
      localStorage.setItem('lastOCR', text);
      return text;
    }catch(e){ return ''; }
  },

  // ===== BARCODE ROI - FAST SCAN INSIDE BOX =====
  async barcodeInROI(roi){
    if(!roi) return null;
    const canvas = document.createElement('canvas');
    canvas.width = roi.w; canvas.height = roi.h;
    canvas.getContext('2d').drawImage(this.video, roi.x, roi.y, roi.w, roi.h, 0,0,roi.w, roi.h);
    try{
      if('BarcodeDetector' in window){
        const detector = new BarcodeDetector({formats:['ean_13','ean_8','code_128','qr_code','upc_a']});
        const bitmap = await createImageBitmap(canvas);
        const codes = await detector.detect(bitmap);
        if(codes[0]){
          const val = codes[0].rawValue;
          const el = document.getElementById('barcodeRes'); if(el) el.innerText = `BARCODE ROI: ${val}`;
          return val;
        }
      }
    }catch(e){}
    return null;
  },

  // ===== THERMAL FILTER - JET COLORMAP =====
  applyThermal(){
    if(!window.cv || !this.video) return;
    try{
      const canvas = document.getElementById('thermalCanvas');
      if(!canvas) return;
      canvas.width = this.video.videoWidth; canvas.height = this.video.videoHeight;
      const src = cv.imread(this.video);
      const gray = new cv.Mat(), colored = new cv.Mat();
      cv.cvtColor(src, gray, cv.COLOR_RGBA2GRAY);
      cv.applyColorMap(gray, colored, cv.COLORMAP_JET);
      cv.imshow(canvas, colored);
      src.delete(); gray.delete(); colored.delete();
      canvas.style.display = 'block';
    }catch(e){}
  },

  // Legacy mock for compatibility
  detect(){
    if(!this.aiCanvas) return;
    const w = this.aiCanvas.width, h = this.aiCanvas.height;
    this.ctx.strokeStyle = '#00ff00'; this.ctx.lineWidth = 2; this.ctx.font = '12px monospace';
    const mock = [{x:w*0.2,y:h*0.3,w:w*0.2,h:h*0.2,label:'Particle 87%'},{x:w*0.6,y:h*0.5,w:w*0.15,h:h*0.15,label:'Fiber 92%'}];
    mock.forEach(d=>{ this.ctx.strokeRect(d.x,d.y,d.w,d.h); this.ctx.fillStyle='#00ff00'; this.ctx.fillText(d.label,d.x,d.y-5); });
    const el = document.getElementById('overlay')||document.getElementById('see'); if(el) el.innerText=`AI v1.9 - ${mock.length} mock | Use Tracking/Count ROI buttons for real AI`;
  },

  countParticles(){
    const roi = window.countROI || {x:0,y:0,w:this.video.videoWidth, h:this.video.videoHeight};
    const c = this.countInROI(roi);
    alert(`WanaLab AI Counter v1.9 FINAL:\nCount: ${c} objects\nROI: ${roi.w}x${roi.h}\nMethod: OpenCV contours area>80\n\nTip: Drag COUNT ROI box for accurate count`);
    return c;
  }
};

document.addEventListener('DOMContentLoaded', ()=>window.aiEngine.init());
