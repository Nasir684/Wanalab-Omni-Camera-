// WanaLab God Eye v1.4.2 - protocol-universe.js
// Master protocol + counting config - By Nasir Khan

const ProtocolUniverse = {
  version: "1.4.2",
  codename: "Anti-Mix",
  app: "WanaLab God Eye",
  lastUpdated: "2026-11-21",
  pricing: { enabled: false, note: "FREE during testing phase" },

  protocols: {
    rtsp: { enabled: true, tcpFirst: false, timeoutMs: 10000, note: "LAN camera - auto TCP/UDP, like Android" },
    hls: { enabled: true, ext: ".m3u8", delaySec: 5 },
    dash: { enabled: true, ext: ".mpd" },
    rtmp: { enabled: true, note: "Use via Progressive, no RtmpDataSource in Media3 1.4+" },
    mjpeg: { enabled: true, note: "ExoPlayer CANNOT play MJPEG - use MjpegView / <img> tag, not video player" },
    onvif: { enabled: true, port: 3702, multicast: "239.255.255.250", probes: 3, note: "Needs MulticastLock on Android" },
    http: { enabled: true, note: "Snapshot / MP4 / TS" },
    isOffline: (url) => {
      if (!url) return false;
      const u = url.toLowerCase().trim();
      if (u.includes("192.168.") || u.includes("10.0.") || u.includes("172.16.") || u.includes("localhost") || u.includes("127.0.0.1")) return true;
      return false;
    },
    isOnline: (url) => {
      if (!url) return false;
      const u = url.toLowerCase().trim();
      if (ProtocolUniverse.protocols.isOffline(u)) return false;
      return u.endsWith(".m3u8") || u.endsWith(".mpd") || u.startsWith("https://") || u.startsWith("rtmp://") || u.startsWith("rtsp://");
    }
  },

  countingPro: {
    id: "counting_pro_v142",
    enabled: true,
    defaultMode: "total",
    defaultClass: "bottle",
    modes: ["total", "entry", "zone"],
    activeMode: "total",
    classes: ["bottle", "person", "cup", "car", "chair", "all"],
    filters: {
      confThreshold: 0.50,
      minArea: 0.008,
      maxArea: 0.35,
      shapeCheck: true,
      stabilityCheck: true,
      maxDist: 90,
      timeoutMs: 1200,
      votesNeeded: 2,
      historySize: 5
    },
    presets: {
      bottle: { conf: 0.50, minArea: 0.008, maxArea: 0.35, shape: true },
      person: { conf: 0.55, minArea: 0.05, maxArea: 0.60, shape: false },
      cup: { conf: 0.50, minArea: 0.005, maxArea: 0.20, shape: true },
      car: { conf: 0.55, minArea: 0.08, maxArea: 0.80, shape: false },
      chair: { conf: 0.50, minArea: 0.05, maxArea: 0.50, shape: false },
      all: { conf: 0.45, minArea: 0.005, maxArea: 0.80, shape: false }
    },
    zone: {
      enabled: false,
      default: { x1: 0.15, y1: 0.25, x2: 0.85, y2: 0.75 }
    },
    line: { yPercent: 50, countIn: true, countOut: true, enabled: false },
    density: { low: 3, med: 8, high: 9 },
    logging: { enabled: true, exportCSV: true }
  },

  thermal: { enabled: true, file: "thermal.json" },
  nightVision: { enabled: true, file: "night-vision.json" },
  barcode: { enabled: true, file: "tap-bill.json" },
  export: { enabled: true, file: "export-hub.js" },

  getPreset: function(className) {
    return this.countingPro.presets[className] || this.countingPro.presets.bottle;
  },
  setMode: function(mode) {
    if (!this.countingPro.modes.includes(mode)) return false;
    this.countingPro.activeMode = mode;
    this.countingPro.zone.enabled = (mode === "zone");
    this.countingPro.line.enabled = (mode === "entry");
    return true;
  }
};

if (typeof window !== 'undefined') window.ProtocolUniverse = ProtocolUniverse;
if (typeof module !== 'undefined') module.exports = ProtocolUniverse;
