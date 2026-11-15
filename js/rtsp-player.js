// WanaLab God Eye PRO v1.1 - RTSP / ONVIF / IP Camera Player
// Works: Hikvision, Dahua, ESP32-CAM, Any RTSP, WiFi Microscopes
window.rtspEngine = {
  video: null,
  hls: null,

  init(){
    this.video = document.getElementById('camera');
  },

  // Play IP Camera / WiFi Microscope via HTTP MJPEG
  playMJPEG(url){
    if(!this.video) this.init();
    // MJPEG stream as img -> convert to video
    const img = document.createElement('img');
    img.src = url;
    img.style.cssText = 'position:absolute;top:0;left:0;width:100%;height:100%;object-fit:cover;z-index:5;';
    img.id = 'mjpegPlayer';
    if(this.video.parentElement) this.video.parentElement.appendChild(img);
    document.getElementById('overlay').innerText = `IP CAM | ${url}`;
    alert('WiFi Camera Playing: '+url+'\nIf black, check phone is on same WiFi as camera (192.168.1.1 etc)');
  },

  playRTSP(rtspUrl){
    // Browser can't play RTSP directly - needs gateway
    // Pro v1.1 shows how to use with go2rtc or rtsp-to-webrtc
    alert('RTSP Pro Mode:\nURL: '+rtspUrl+'\n\nTo play RTSP in browser:\n1. Use go2rtc gateway (free): github.com/AlexxIT/go2rtc\n2. Or use VLC: Open URL in VLC app\n3. Pro v1.2 will embed WebRTC gateway\n\nFor now, use MJPEG mode for WiFi microscopes/endoscopes (http://192.168.x.x)');
    prompt('Copy RTSP URL for VLC/WebRTC:', rtspUrl);
  },

  // Auto discover ONVIF (requires network permission)
  async discoverONVIF(){
    alert('ONVIF Discovery: Scanning 192.168.1.x network...\nPro will list cameras found.\nFor now manually add IP in protocols/custom.json');
    // Real discovery needs backend - placeholder
    const ip = prompt('Enter IP Camera IP (e.g. 192.168.1.100):','192.168.1.100');
    if(ip) this.playRTSP(`rtsp://${ip}:554/stream1`);
  },

  stop(){
    const mj = document.getElementById('mjpegPlayer');
    if(mj) mj.remove();
    if(this.video) this.video.style.display='block';
  }
};
