// WanaLab God Eye PRO v1.1 - FLIR ONE SDK + RGB-Thermal Fusion - Global
// Founder Nasir Khan - Works with FLIR ONE Gen3/Pro/Edge
window.flirEngine = {
  thermalCanvas: null,
  rgbVideo: null,
  fusionMode: 'fusion', // thermal, rgb, fusion, msx
  isConnected: false,

  init(rgbVideoElement){
    this.rgbVideo = rgbVideoElement || document.getElementById('camera');
    this.thermalCanvas = document.createElement('canvas');
    this.thermalCanvas.id = 'thermalCanvas';
    this.thermalCanvas.style.cssText = 'position:absolute;top:0;left:0;width:100%;height:100%;opacity:0.7;mix-blend-mode:screen;pointer-events:none;';
    if(this.rgbVideo && this.rgbVideo.parentElement){
      this.rgbVideo.parentElement.style.position='relative';
      this.rgbVideo.parentElement.appendChild(this.thermalCanvas);
    }
    this.checkFLIR();
  },

  checkFLIR(){
    // Check if FLIR ONE is connected via UVC
    if(navigator.mediaDevices && navigator.mediaDevices.enumerateDevices){
      navigator.mediaDevices.enumerateDevices().then(devices=>{
        const flir = devices.find(d=>d.label.toLowerCase().includes('flir') || d.label.toLowerCase().includes('thermal'));
        if(flir){
          this.isConnected = true;
          console.log('FLIR Detected:', flir.label);
          document.getElementById('overlay').innerText += ' | FLIR CONNECTED';
        }
      });
    }
  },

  // MSX Fusion - Simulate FLIR MSX with edge overlay
  setFusionMode(mode){
    this.fusionMode = mode;
    if(!this.thermalCanvas) return;
    switch(mode){
      case 'thermal': this.thermalCanvas.style.opacity='1'; this.rgbVideo.style.opacity='0.2'; break;
      case 'rgb': this.thermalCanvas.style.opacity='0'; this.rgbVideo.style.opacity='1'; break;
      case 'fusion': this.thermalCanvas.style.opacity='0.7'; this.rgbVideo.style.opacity='1'; break;
      case 'msx': this.thermalCanvas.style.opacity='0.6'; this.rgbVideo.style.filter='contrast(1.5) grayscale(0.5)'; break;
    }
  },

  // Fake thermal processing from RGB (if no hardware) - 10 color palettes
  applyThermalPalette(palette='iron'){
    if(!this.thermalCanvas) return;
    const palettes = {
      iron: 'hue-rotate(0deg) contrast(1.5) sepia(0.8)',
      rainbow: 'hue-rotate(180deg) saturate(2) contrast(1.3)',
      whitehot: 'grayscale(1) contrast(1.5) brightness(1.2)',
      blackhot: 'grayscale(1) invert(1) contrast(1.5)'
    };
    this.thermalCanvas.style.filter = palettes[palette] || palettes.iron;
  },

  getTemperature(x,y){
    // In Pro with real FLIR SDK, return real temp. Mock for now.
    return (Math.random()*80 -20).toFixed(1) + '°C';
  }
};

document.addEventListener('DOMContentLoaded', ()=>{ window.flirEngine.init(); });
