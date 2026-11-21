// WanaLab God Eye v1.4.2 - RTSP / ONVIF / IP Camera Player
// Works: Hikvision, Dahua, ESP32-CAM, Any RTSP via go2rtc, WiFi Microscopes
// Browser CANNOT play RTSP directly - needs go2rtc gateway -> HLS / WebRTC / MJPEG
window.rtspEngine = {
  video: null,
  hls: null,
  mjpegImg: null,
  currentUrl: null,
  currentType: null,

  init(){
    this.video = document.getElementById('camera');
  },

  setOverlay(t) {
    const ov = document.getElementById('overlay');
    if (ov) ov.innerText = t;
  },

  // FIX: Remove old player before adding new - was leaking 3 stacked imgs with same ID
  cleanup() {
    if (this.hls) { try { this.hls.destroy(); } catch(e) {} this.hls = null; }
    if (this.mjpegImg) { try { this.mjpegImg.src = ''; this.mjpegImg.remove(); } catch(e) {} this.mjpegImg = null; }
    const old = document.getElementById('mjpegPlayer');
    if (old) old.remove();
    if (this.video) {
      this.video.srcObject = null;
      this.video.src = '';
      this.video.style.display = 'block';
    }
  },

  // Play IP Camera / WiFi Microscope via HTTP MJPEG - ESP32-CAM, microscopes
  playMJPEG(url){
    if(!this.video) this.init();
    this.cleanup();
    this.currentUrl = url;
    this.currentType = 'mjpeg';

    const img = document.createElement('img');
    img.id = 'mjpegPlayer';
    img.style.cssText = 'position:absolute;top:0;left:0;width:100%;height:100%;object-fit:cover;z-index:5;background:#000;';
    // FIX: Error handling - was black screen forever if camera offline
    img.onerror = () => {
      this.setOverlay(`IP CAM | FAILED - Check same WiFi: ${url}`);
    };
    img.onload = () => {
      this.setOverlay(`IP CAM | LIVE MJPEG - ${url}`);
    };
    // FIX: Timeout - if no frame in 5s, show error
    setTimeout(() => {
      if (this.currentType === 'mjpeg' && img && !img.complete) {
        this.setOverlay(`IP CAM | No stream - Check ${url} in browser first`);
      }
    }, 5000);
    img.src = url;
    this.mjpegImg = img;
    if(this.video) {
      this.video.style.display = 'none'; // Hide phone camera, show MJPEG
      if(this.video.parentElement) this.video.parentElement.appendChild(img);
    }
    this.setOverlay(`IP CAM | Connecting MJPEG - ${url}`);
  },

  // FIX: New - Play HLS from go2rtc - this is how RTSP actually works in browser
  // go2rtc: http://192.168.1.100:1984/api/stream.m3u8?src=rtsp://192.168.1.101:554/stream1
  playHLS(hlsUrl) {
    if (!this.video) this.init();
    this.cleanup();
    this.currentUrl = hlsUrl;
    this.currentType = 'hls';
    this.video.style.display = 'block';
    this.setOverlay(`IP CAM | Connecting HLS - ${hlsUrl}`);

    // Native HLS (Safari / Android) vs HLS.js
    if (this.video.canPlayType('application/vnd.apple.mpegurl')) {
      this.video.src = hlsUrl;
      this.video.play().catch(() => this.setOverlay('IP CAM | HLS play blocked - tap screen'));
    } else if (window.Hls) {
      this.hls = new Hls({ maxBufferLength: 10, liveSyncDurationCount: 2 });
      this.hls.loadSource(hlsUrl);
      this.hls.attachMedia(this.video);
      this.hls.on(Hls.Events.MANIFEST_PARSED, () => {
        this.video.play().catch(() => {});
        this.setOverlay(`IP CAM | LIVE HLS`);
      });
      this.hls.on(Hls.Events.ERROR, (e, data) => {
        if (data.fatal) this.setOverlay(`IP CAM | HLS Error - Check go2rtc: ${data.type}`);
      });
    } else {
      this.setOverlay('IP CAM | HLS.js not loaded - add hls.js script tag');
    }
  },

  // FIX: Real go2rtc WebRTC - was just alert, now actually tries to play
  playRTSP(rtspUrl, go2rtcBase){
    // go2rtcBase e.g. http://192.168.1.100:1984 - if not given, show setup, don't just alert
    if (!go2rtcBase) {
      this.setOverlay(`RTSP | Need go2rtc gateway - ${rtspUrl}`);
      // Show setup in overlay, not alert blocking UI
      const setup = `RTSP needs gateway:\n1. Install go2rtc on PC / Pi: github.com/AlexxIT/go2rtc\n2. Add stream: rtsp: ${rtspUrl}\n3. Then call: rtspEngine.playHLS('http://YOUR-PC:1984/api/stream.m3u8?src='+encodeURIComponent(rtspUrl))\nOr open in VLC: ${rtspUrl}`;
      console.log(setup);
      // Keep old behavior for copy - but as overlay, not blocking prompt
      if (navigator.clipboard) navigator.clipboard.writeText(rtspUrl).catch(()=>{});
      return setup;
    }
    // go2rtc HLS URL
    const hlsUrl = `${go2rtcBase}/api/stream.m3u8?src=${encodeURIComponent(rtspUrl)}`;
    this.playHLS(hlsUrl);
    return hlsUrl;
  },

  // FIX: Honest - browser can't do UDP WS-Discovery, show manual IP UI, not fake scan
  async discoverONVIF(){
    this.setOverlay('ONVIF | Browser cannot auto-scan (no UDP) - Enter IP manually');
    const ip = prompt('Enter IP Camera IP (e.g. 192.168.1.100):','192.168.1.100');
    if(ip) {
      // Try MJPEG first (ESP32 / microscope), fallback to RTSP via go2rtc
      const mjpegUrl = `http://${ip}:81/stream`; // ESP32-CAM default
      this.setOverlay(`ONVIF | Trying MJPEG ${mjpegUrl} - if black, use RTSP via go2rtc`);
      this.playMJPEG(mjpegUrl);
      return ip;
    }
    return null;
  },

  stop(){
    this.cleanup();
    this.currentUrl = null;
    this.currentType = null;
    this.setOverlay('IP CAM | Stopped - Phone Camera');
    // Restart phone camera via GodEye
    if (window.GodEye) window.GodEye.init();
  }
};
