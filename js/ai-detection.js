// WanaLab God Eye v1.4.1 - ai-detection.js
// Anti-Mix: ID Tracking + Size + Shape + Stability
// By Nasir Khan

const CountingPro = {
  version: "1.4.1",
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

  // Anti-Mix Config - editable from FILTERS page
  config: {
    confThreshold: 0.62,
    minArea: 0.008,  // 0.8% - hand is 30% = ignored
    maxArea: 0.18,   // 18%
    shapeCheck: true,
    stabilityCheck: true,
    maxDist: 60, // was 80 - tighter tracking
    timeoutMs: 800, // was 1200
    stabilityDelay: 1000,
    historySize: 5,
    votesNeeded: 3
  },

  presets: {
    bottle: { conf: 0.62, minArea: 0.008, maxArea: 0.18, shape: true },
    person: { conf: 0.55, minArea: 0.05, maxArea: 0.60, shape: false },
    cup: { conf: 0.60, minArea: 0.005, maxArea: 0.12, shape: false },
    car: { conf: 0.60, minArea: 0.10, maxArea: 0.80, shape: false }
  },

  applyPreset(type) {
    const p = this.presets[type];
    if (!p) return;
    this.config.confThreshold = p.conf;
    this.config.minArea = p.minArea;
    this.config.maxArea = p.maxArea;
    this.config.shapeCheck = p.shape;
    this.targetClass = type;
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

      current.push({cx:x+w/2, cy:y+h/2, x,y,w,h, cls:p.class, score:p.score, area});
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

        // Anti-Mix: If majority is NOT target, drop it (was flicker)
        if(this.config.stabilityCheck && maj && this.targetClass!=='all' && maj!==this.targetClass){
          delete this.trackObjects[bestId];
          delete this.classHistory[bestId];
          return;
        }

        let o=this.trackObjects[bestId];
        if(isCounting && !o.counted && this.countMode!=='zone'){
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
        let inZone = this.roiZones.length===0 ? true : this.roiZones.some(z=>det.cx>=z.x1 && det.cx<=z.x2 && det.cy>=z.y1 && det.cy<=z.y2);
        if(inZone || this.countMode!=='zone'){
          let id='ID_'+this.nextId++;
          this.trackObjects[id]={cx:det.cx,cy:det.cy,lastSeen:now,counted:false,cls:det.cls,firstSeen:now};
          this.classHistory[id]=[det.cls];
          det.id=id;
          if(isCounting && this.countMode==='total' && inZone){
            setTimeout(()=>{
              let maj=this.getClassMajority(id);
              if(this.trackObjects[id] && !this.trackObjects[id].counted && (!this.config.stabilityCheck || maj===this.targetClass || this.targetClass==='all')){
                this.particleCount++; this.trackObjects[id].counted=true; this.entryCount++;
              }
            }, this.config.stabilityDelay);
          }
          used.add(id);
        }
      }
    });

    // Cleanup old IDs
    for(let id in this.trackObjects){
      if(now-this.trackObjects[id].lastSeen>1200){ delete this.trackObjects[id]; delete this.classHistory[id]; }
    }

    return current; // draw these in index.html
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

// Export for index.html
if(typeof window!=='undefined') window.CountingPro = CountingPro;
if(typeof module!=='undefined') module.exports = CountingPro;
