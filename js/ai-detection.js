// WanaLab God Eye v1.4.2 - ai-detection.js
// Anti-Mix: ID Tracking + Size + Shape + Stability
// By Nasir Khan

const CountingPro = {
  version: "1.4.2",
  trackObjects: {},
  classHistory: {},
  nextId: 1,
  particleCount: 0,
  entryCount: 0,
  exitCount: 0,
  countLog: [],
  countMode: 'total', // total | entry | zone
  targetClass: 'bottle',
  countLineY: 0,
  roiZones: [],

  // FIX: Config now synced from ProtocolUniverse, not hardcoded
  // If ProtocolUniverse not loaded, fallback to these (shop-tuned)
  config: {
    confThreshold: 0.50, // FIX: Was 0.62 - missed low-light bottles
    minArea: 0.008,
    maxArea: 0.35, // FIX: Was 0.18 - rejected close bottles on counter
    shapeCheck: true,
    stabilityCheck: true,
    maxDist: 90, // FIX: Was 60 - fast hand moved >60px = double count
    timeoutMs: 1200, // FIX: Was 800 - too short, lost tracker
    stabilityDelay: 0, // FIX: Removed setTimeout counting, kept for compat, not used
    historySize: 5,
    votesNeeded: 2 // FIX: Was 3 - too strict for 15fps shaky cam
  },

  presets: {
    bottle: { conf: 0.50, minArea: 0.008, maxArea: 0.35, shape: true },
    person: { conf: 0.55, minArea: 0.05, maxArea: 0.60, shape: false },
    cup: { conf: 0.50, minArea: 0.005, maxArea: 0.20, shape: true },
    car: { conf: 0.55, minArea: 0.08, maxArea: 0.80, shape: false },
    chair: { conf: 0.50, minArea: 0.05, maxArea: 0.50, shape: false },
    all: { conf: 0.45, minArea: 0.005, maxArea: 0.80, shape: false }
  },

  // FIX: Sync from ProtocolUniverse on load - single source of truth
  syncFromUniverse() {
    try {
      if (typeof ProtocolUniverse !== 'undefined' && ProtocolUniverse.getPreset) {
        const p = ProtocolUniverse.getPreset(this.targetClass);
        this.config.confThreshold = p.conf;
        this.config.minArea = p.minArea;
        this.config.maxArea = p.maxArea;
        this.config.shapeCheck = p.shape;
        // Also sync filters
        if (ProtocolUniverse.countingPro && ProtocolUniverse.countingPro.filters) {
          const f = ProtocolUniverse.countingPro.filters;
          this.config.maxDist = f.maxDist;
          this.config.timeoutMs = f.timeoutMs;
          this.config.votesNeeded = f.votesNeeded;
          this.config.historySize = f.historySize;
        }
        // Sync zone
        if (ProtocolUniverse.countingPro && ProtocolUniverse.countingPro.zone) {
          const z = ProtocolUniverse.countingPro.zone;
          if (z.enabled) this.roiZones = [z.default];
        }
      }
    } catch(e) { console.warn("[CountingPro] sync failed, using fallback", e); }
  },

  applyPreset(type) {
    const p = this.presets[type];
    if (!p) return;
    this.config.confThreshold = p.conf;
    this.config.minArea = p.minArea;
    this.config.maxArea = p.maxArea;
    this.config.shapeCheck = p.shape;
    this.targetClass = type;
    // FIX: Also push to ProtocolUniverse so both stay in sync
    try { if (typeof ProtocolUniverse !== 'undefined') ProtocolUniverse.countingPro.defaultClass = type; } catch(e) {}
    console.log(`[CountingPro] Preset ${type} applied`);
  },

  getDistance(x1,y1,x2,y2){ return Math.hypot(x1-x2, y1-y2); },

  getClassMajority(id){
    let hist = this.classHistory[id] || [];
    if(hist.length===0) return null;
    let counts={}; hist.forEach(c=>counts[c]=(counts[c]||0)+1);
    let best = Object.keys(counts).sort((a,b)=>counts[b]-counts[a])[0];
    return counts[best] >= this.config.votesNeeded ? best : null;
  },

  // FIX: ROI check with normalized coords - was comparing pixels vs 0-1
  isInZone(det, videoW, videoH) {
    if (this.roiZones.length === 0) return true;
    const nx = det.cx / videoW;
    const ny = det.cy / videoH;
    return this.roiZones.some(z => {
      // Support both normalized (0-1) and pixel zones
      if (z.x2 <= 1.01 && z.y2 <= 1.01) return nx >= z.x1 && nx <= z.x2 && ny >= z.y1 && ny <= z.y2;
      return det.cx >= z.x1 && det.cx <= z.x2 && det.cy >= z.y1 && det.cy <= z.y2;
    });
  },

  // Call this from your loop() with coco predictions
  process(preds, videoW, videoH, canvasH, isCounting){
    this.countLineY = canvasH * 0.5;
    let now = Date.now();
    let current = [];

    // === FILTER 1: Class + Confidence + Size + Shape ===
    preds.forEach(p=>{
      if(this.targetClass!=='all' && p.class!==this.targetClass) return;
      if(p.score < this.config.confThreshold) return;

      let [x,y,w,h] = p.bbox;
      let area = (w*h) / (videoW*videoH);
      if(area < this.config.minArea || area > this.config.maxArea) return;

      if(this.config.shapeCheck && this.targetClass==='bottle'){
        if(h < w*1.2) return; // Bottle must be taller than wide
      }

      current.push({cx:x+w/2, cy:y+h/2, x,y,w,h, cls:p.class, score:p.score, area, stableFrames: 0});
    });

    // === FILTER 2: ID Tracking + Majority Vote ===
    let used = new Set();
    current.forEach(det=>{
      let bestId=null, bestDist=this.config.maxDist;
      for(let id in this.trackObjects){
        if(used.has(id)) continue;
        let o=this.trackObjects[id];
        let d=this.getDistance(det.cx,det.cy,o.cx,o.cy);
        if(d<bestDist && (now-o.lastSeen)<this.config.timeoutMs){bestDist=d; bestId=id;}
      }

      if(bestId){
        this.classHistory[bestId]=this.classHistory[bestId]||[];
        this.classHistory[bestId].push(det.cls);
        if(this.classHistory[bestId].length>this.config.historySize) this.classHistory[bestId].shift();
        let maj = this.config.stabilityCheck ? this.getClassMajority(bestId) : det.cls;

        if(this.config.stabilityCheck && maj && this.targetClass!=='all' && maj!==this.targetClass){
          delete this.trackObjects[bestId];
          delete this.classHistory[bestId];
          return;
        }

        let o=this.trackObjects[bestId];
        o.stableFrames = (o.stableFrames || 0) + 1;

        // FIX: TOTAL mode - count synchronously when stable, NO setTimeout
        // Was: setTimeout() per ID = double count + ghost after reset
        if(isCounting && this.countMode==='total' && !o.counted){
          const inZone = this.isInZone(det, videoW, videoH);
          if(inZone && (!this.config.stabilityCheck || o.stableFrames >= this.config.votesNeeded)){
            // Majority must match, or if not yet voted, use current class
            if(!this.config.stabilityCheck || maj===this.targetClass || this.targetClass==='all' || maj===null){
              this.particleCount++; this.entryCount++; o.counted=true;
              this.countLog.push({id:bestId,type:'ENTRY',class:maj||det.cls,time:new Date().toLocaleTimeString()});
            }
          }
        }

        if(isCounting && !o.counted && this.countMode!=='zone' && this.countMode!=='total'){
          if(o.cy < this.countLineY && det.cy >= this.countLineY){
            if(!this.config.stabilityCheck || maj===this.targetClass || this.targetClass==='all'){
              this.entryCount++; this.particleCount++; o.counted=true;
              this.countLog.push({id:bestId,type:'ENTRY',class:maj||det.cls,time:new Date().toLocaleTimeString()});
            }
          } else if(o.cy > this.countLineY && det.cy <= this.countLineY){
            this.exitCount++;
            this.countLog.push({id:bestId,type:'EXIT',class:maj||det.cls,time:new Date().toLocaleTimeString()});
          }
        }
        o.cx=det.cx; o.cy=det.cy; o.lastSeen=now; used.add(bestId); det.id=bestId;
      } else {
        let inZone = this.isInZone(det, videoW, videoH);
        if(inZone || this.countMode!=='zone'){
          let id='ID_'+this.nextId++;
          this.trackObjects[id]={cx:det.cx,cy:det.cy,lastSeen:now,counted:false,cls:det.cls,firstSeen:now,stableFrames:1};
          this.classHistory[id]=[det.cls];
          det.id=id;
          // FIX: No setTimeout here - counting happens on next frames when stableFrames >= votesNeeded
          // This prevents double count from re-detection + ghost after reset
          used.add(id);
        }
      }
    });

    // FIX: Cleanup uses timeoutMs, not hardcoded 1200
    const cleanupMs = this.config.timeoutMs + 200;
    for(let id in this.trackObjects){
      if(now-this.trackObjects[id].lastSeen>cleanupMs){ delete this.trackObjects[id]; delete this.classHistory[id]; }
    }

    return current;
  },

  reset(){
    this.trackObjects={}; this.classHistory={}; this.nextId=1;
    this.particleCount=0; this.entryCount=0; this.exitCount=0; this.countLog=[];
  },
  exportLog(){
    let csv="Time,ID,Type,Class\n";
    this.countLog.forEach(l=>csv+=`${l.time},${l.id},${l.type},${l.class}\n`);
    csv+=`\nTOTAL,${this.particleCount},IN ${this.entryCount} OUT ${this.exitCount}\n`;
    let blob=new Blob([csv],{type:'text/csv'});
    let a=document.createElement('a'); a.href=URL.createObjectURL(blob); a.download=`CountLog-${Date.now()}.csv`; a.click();
  }
};

// FIX: Auto-sync on load if ProtocolUniverse already loaded
try { CountingPro.syncFromUniverse(); } catch(e) {}

// Export for index.html
if(typeof window!=='undefined') window.CountingPro = CountingPro;
if(typeof module!=='undefined') module.exports = CountingPro;
