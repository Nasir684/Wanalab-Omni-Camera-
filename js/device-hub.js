// WanaLab God Eye v1.0 - Device Hub - USB UVC + WiFi RTSP + BT - Global
window.deviceHub = {
  devices: [],

  async connectUSB(){
    try{
      if(!navigator.usb){ alert('USB needs Chrome Android + HTTPS. Your device: WebUSB not supported. Use generic camera or WiFi.'); return; }
      const device = await navigator.usb.requestDevice({filters:[{}]});
      await device.open();
      if(device.configuration===null) await device.selectConfiguration(1);
      this.devices.push({type:'USB', device, id: device.vendorId});
      document.getElementById('universe').innerText = `USB CONNECTED: ${device.productName || device.vendorId} | Protocol matched: ${window.protocolUniverse.findForDevice(device.vendorId,'UVC').length}`;
      alert(`USB Connected: ${device.productName || 'UVC Device'} - VendorID: ${device.vendorId} - Will use UVC camera stream`);
      // Try to get video stream via UVC
      try{
        const stream = await navigator.mediaDevices.getUserMedia({video:{deviceId:{exact: device.vendorId}}});
        if(stream) document.getElementById('camera').srcObject = stream;
      }catch{ console.log('UVC stream fallback to main camera'); }
    }catch(e){ alert('USB Error: '+e.message); }
  },

  async connectWiFi(){
    const ip = prompt('Enter WiFi Camera IP / RTSP URL:\nExamples:\n192.168.1.1 (WiFi microscope)\nrtsp://192.168.1.100:554/stream\nhttp://192.168.1.100/capture (ESP32-CAM)', '192.168.1.1');
    if(!ip) return;
    let url = ip;
    if(!url.startsWith('rtsp://') && !url.startsWith('http')){
      url = `rtsp://${ip}:554/stream`;
      if(ip.includes('192.168.1.1')) url = 'http://192.168.1.1:8080/?action=stream';
    }
    this.devices.push({type:'WIFI', url});
    document.getElementById('universe').innerText = `WIFI CAM: ${url} | Add via protocols/ip-cameras.json`;
    alert(`WiFi Camera Added: ${url}\nNote: Browser RTSP needs proxy. For now use HTTP MJPEG or use native viewer. Protocol saved.`);
    // Try to load as img
    try{
      if(url.startsWith('http')){
        document.getElementById('camera').style.display='none';
        let img = document.getElementById('wifi-img');
        if(!img){ img=document.createElement('img'); img.id='wifi-img'; img.style.cssText='width:100vw;height:100vh;object-fit:cover'; document.body.prepend(img); }
        img.src = url;
      }
    }catch(e){}
  },

  async scanBluetooth(){
    try{
      if(!navigator.bluetooth){ alert('Bluetooth needs Chrome Android'); return; }
      const device = await navigator.bluetooth.requestDevice({acceptAllDevices:true, optionalServices:['battery_service']});
      this.devices.push({type:'BT', device});
      document.getElementById('universe').innerText = `BT: ${device.name} connected`;
      alert(`BT Connected: ${device.name}`);
    }catch(e){ alert('BT Error: '+e.message); }
  },

  list(){
    const all = window.protocolUniverse ? window.protocolUniverse.getAll() : [];
    alert(`God Eye Protocols Loaded: ${all.length}\n` + all.slice(0,10).map(p=>p.name).join('\n') + (all.length>10? `\n...and ${all.length-10} more`:'') + '\n\nSee pages/devices.html for full list');
  }
};
