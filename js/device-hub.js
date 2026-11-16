// WanaLab God Eye v1.9 FINAL - Device Hub - UVC + WiFi RTSP + BT + Thermal + Tracking + ROI + Tap Bill - Nasir Khan
window.deviceHub = {
  devices: JSON.parse(localStorage.getItem('wanaDevices')||'[]'),
  lastRTSP: localStorage.getItem('wanaLastRTSP')||'',

  save(){
    localStorage.setItem('wanaDevices', JSON.stringify(this.devices));
    if(this.lastRTSP) localStorage.setItem('wanaLastRTSP', this.lastRTSP);
  },

  async connectUSB(){
    try{
      if(!navigator.usb){ alert('USB needs Chrome Android + HTTPS. Use generic camera button in v1.9 - it supports UVC Microscope 1000x + FLIR via camera.'); return; }
      const device = await navigator.usb.requestDevice({filters:[{}]});
      await device.open();
      if(device.configuration===null) await device.selectConfiguration(1);
      const info = {
        id: `usb-${device.vendorId}-${Date.now()}`,
        type:'USB-UVC', 
        name: device.productName || `UVC ${device.vendorId}`,
        vendorId: device.vendorId,
        productId: device.productId,
        resolution: '1920x1080',
        features: ['manual-crop','tracking','count-roi','thermal-filter','tap-bill','docx-img'],
        status: 'connected',
        version: '1.9-final'
      };
      this.devices.push(info);
      this.save();
      const universeEl = document.getElementById('universe');
      if(universeEl) universeEl.innerText = `USB CONNECTED: ${info.name} | v1.9 Features: Tracking + Count ROI + Tap Bill + Thermal Slider`;
      alert(`✅ USB Connected: ${info.name}\nVendorID: ${device.vendorId}\n\nv1.9 NEW:\n- Tap to Track\n- Drag ROI to Count\n- Tap Bill to add price\n- Thermal slider`);
      try{
        const stream = await navigator.mediaDevices.getUserMedia({video:{facingMode:'environment', width:{ideal:1920}}});
        const cam = document.getElementById('godVideo') || document.getElementById('camera');
        if(cam && stream) cam.srcObject = stream;
      }catch{}
    }catch(e){ alert('USB Error: '+e.message+'\nTip: In v1.9 you can use normal camera + Manual Crop + Thermal filter - no USB needed for microscope'); }
  },

  async connectWiFi(){
    const ip = prompt('Enter WiFi Camera IP / RTSP URL:\nExamples:\n192.168.1.1 (WiFi microscope)\n192.168.4.1 (ESP32-CAM)\nrtsp://192.168.1.100:554/stream (Hikvision/Dahua/Tapo)\nhttp://192.168.1.100/capture', this.lastRTSP || '192.168.1.1');
    if(!ip) return;
    this.lastRTSP = ip;
    let url = ip;
    if(!url.startsWith('rtsp://') && !url.startsWith('http')){
      url = `rtsp://${ip}:554/stream`;
      if(ip.includes('192.168.1.1')) url = 'http://192.168.1.1:8080/?action=stream';
      if(ip.includes('192.168.4.1')) url = 'http://192.168.4.1:81/stream';
    }
    const info = {
      id: `wifi-${Date.now()}`,
      type: url.startsWith('rtsp') ? 'RTSP-ONVIF' : 'WIFI-MJPEG',
      name: url.includes('192.168.4.1') ? 'ESP32-CAM v1.9' : url.includes('192.168.1.1') ? 'WiFi Microscope v1.9' : 'IP Camera v1.9',
      url: url,
      ip: ip,
      features: ['tracking','count-roi','tap-bill','manual-crop','thermal','barcode-roi','searchable-pdf','docx-img'],
      status: 'added',
      version: '1.9-final'
    };
    this.devices.push(info);
    this.save();
    const universeEl = document.getElementById('universe');
    if(universeEl) universeEl.innerText = `WIFI CAM v1.9: ${url} | Tracking + Tap Bill + Count ROI ready`;
    alert(`✅ WiFi/IP Camera Added v1.9:\n${url}\n\nv1.9 NEW for IP cams:\n- DJI Tracking (tap object)\n- COUNT ROI (drag box count)\n- TAP BILL (tap product add price)\n- Thermal slider for night\n- DOCX+IMG export\n\nNote: Browser RTSP needs HTTP proxy. Use HTTP MJPEG for direct view.`);
    try{
      if(url.startsWith('http')){
        const cam = document.getElementById('godVideo') || document.getElementById('camera');
        if(cam) cam.style.display='none';
        let img = document.getElementById('wifi-img');
        if(!img){ img=document.createElement('img'); img.id='wifi-img'; img.style.cssText='width:100vw;height:100vh;object-fit:cover;position:fixed;inset:0;z-index:1'; document.body.prepend(img); }
        img.src = url;
        img.style.display='block';
      }
    }catch(e){}
  },

  async scanBluetooth(){
    try{
      if(!navigator.bluetooth){ alert('Bluetooth needs Chrome Android + HTTPS. In v1.9, BT devices can trigger Tap Bill via remote.'); return; }
      const device = await navigator.bluetooth.requestDevice({acceptAllDevices:true, optionalServices:['battery_service','device_information']});
      const info = {
        id: `bt-${Date.now()}`,
        type:'BT-REMOTE',
        name: device.name || 'BT Device',
        features: ['tap-bill-trigger','shutter-remote','tracking-trigger'],
        status: 'connected',
        version: '1.9-final'
      };
      this.devices.push(info);
      this.save();
      const universeEl = document.getElementById('universe');
      if(universeEl) universeEl.innerText = `BT v1.9: ${device.name} connected - Can trigger shutter / bill`;
      alert(`✅ BT Connected v1.9: ${device.name}\n\nUse as remote:\n- Click to capture photo\n- Long press to add to bill\n- v1.9 supports BT remote shutter`);
    }catch(e){ alert('BT Error: '+e.message); }
  },

  connectThermal(){
    const type = prompt('Select Thermal:\n1 = Software Thermal (any camera + JET filter)\n2 = FLIR ONE / Seek (UVC Thermal)\n3 = InfiRay (USB)\n\nEnter 1,2,3:', '1');
    if(!type) return;
    let name = 'Software Thermal JET v1.9';
    if(type==='2') name = 'FLIR ONE Thermal v1.9';
    if(type==='3') name = 'InfiRay Thermal v1.9';
    this.devices.push({
      id: `thermal-${Date.now()}`,
      type: 'THERMAL',
      name: name,
      features: ['live-thermal-0-100','night-mode','hdr-slider','tracking'],
      palette: 'jet',
      status: 'active',
      version: '1.9-final'
    });
    this.save();
    alert(`✅ ${name} added\n\nUse SLIDERS button in v1.9 camera:\n- Thermal slider 0-100%\n- Night slider 0-100%\n- Thermal filter in preview\nWorks instantly, no hardware needed for software mode`);
    if(typeof toggleSliders==='function') toggleSliders();
  },

  list(){
    const all = window.protocolUniverse ? window.protocolUniverse.getAll() : this.devices;
    const v19 = [
      '✅ NEW v1.9 FINAL Features:',
      '- Manual 4-corner crop drag',
      '- COUNT ROI (drag box to count)',
      '- OCR ROI (drag box to read text)',
      '- BARCODE ROI (drag small box fast scan)',
      '- TAP BILL (tap product to add Rs.)',
      '- DJI Tracking (tap object to lock)',
      '- Thermal slider 0-100% JET',
      '- Night HDR slider 0-100%',
      '- Searchable PDF (OCR layer)',
      '- DOCX + Images per page',
      '',
      `Connected Devices: ${this.devices.length}`,
      ...this.devices.map(d=>`• ${d.name} (${d.type}) - ${d.features?d.features.join(','):''}`),
      '',
      `Protocols: ${all.length} loaded`
    ].join('\n');
    alert(v19);
    const universeEl = document.getElementById('universe');
    if(universeEl) universeEl.innerText = `v1.9 Devices: ${this.devices.length} | Protocols: ${all.length} | All features ready`;
  },

  clearAll(){
    if(confirm('Clear all added devices? (v1.9)')){
      this.devices = [];
      localStorage.removeItem('wanaDevices');
      alert('Cleared - v1.9 devices reset');
    }
  }
};
