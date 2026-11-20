// WanaLab God Eye v1.4.1 - protocol-universe.js
// Master protocol config - By Nasir Khan

const ProtocolUniverse = {
  version: "1.4.1",
  codename: "Anti-Mix",
  app: "WanaLab God Eye",
  lastUpdated: "2026-11-20",
  pricing: { enabled: false, note: "FREE during testing phase" },

  countingPro: {
    id: "counting_pro_v141",
    enabled: true,
    defaultMode: "total",
    defaultClass: "bottle",
    modes: ["total", "entry", "zone"],
    classes: ["bottle", "person", "cup", "car", "chair", "all"],
    // Anti-Mix Filters - synced with ai-detection.js
    filters: {
      confThreshold: 0.62,
      minArea: 0.008,
      maxArea: 0.18,
      shapeCheck: true,
      stabilityCheck: true,
      maxDist: 60,
      timeoutMs: 800,
      votesNeeded: 3,
      historySize: 5
    },
    presets: {
      bottle: { conf: 0.62, minArea: 0.008, maxArea: 0.18, shape: true },
      person: { conf: 0.55, minArea: 0.05, maxArea: 0.60, shape: false }
    },
    zone: {
      enabled: true,
      default: { x1: 0.15, y1: 0.25, x2: 0.85, y2: 0.75 }
    },
    line: { yPercent: 50, countIn: true, countOut: true },
    density: { low: 3, med: 8, high: 9 },
    logging: { enabled: true, exportCSV: true }
  },

  // Other protocols untouched
  thermal: { enabled: true, file: "thermal.json" },
  nightVision: { enabled: true, file: "night-vision.json" },
  barcode: { enabled: true, file: "tap-bill.json" },
  export: { enabled: true, file: "export-hub.js" }
};

if(typeof window!=='undefined') window.ProtocolUniverse = ProtocolUniverse;
if(typeof module!=='undefined') module.exports = ProtocolUniverse;
