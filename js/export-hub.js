// WanaLab God Eye Export Hub PRO v1.9 FINAL - All Features - Founder Nasir Khan
window.exportHub = {
  pages: JSON.parse(localStorage.getItem('wanaScannedPages')||'[]'),
  
  down(blob, name){
    const a = document.createElement('a');
    a.href = URL.createObjectURL(blob);
    a.download = name; a.click();
    setTimeout(()=>URL.revokeObjectURL(a.href), 1000);
  },

  exportTXT(text){
    const t = text || localStorage.getItem('lastOCR') || `God Eye v1.9 FINAL\nDate: ${new Date().toLocaleString()}\nPages: ${this.pages.length}\n`;
    const blob = new Blob([t], {type:'text/plain'});
    this.down(blob, `GodEye-v19-${Date.now()}.txt`);
  },

  exportJSON(obj){
    const data = obj || {
      text: localStorage.getItem('lastOCR')||'',
      pages: this.pages.length,
      bill: JSON.parse(localStorage.getItem('wanaBillItems')||'[]'),
      total: localStorage.getItem('wanaTotalBill')||'0',
      date: new Date().toISOString(),
      version: 'v1.9 FINAL ALL-IN-ONE',
      features: ['manual-4-corner-crop','count-roi','ocr-roi','barcode-roi','tap-bill','dji-tracking','thermal-slider','night-hdr','searchable-pdf','docx-with-images']
    };
    const blob = new Blob([JSON.stringify(data,null,2)], {type:'application/json'});
    this.down(blob, `GodEye-v19-${Date.now()}.json`);
  },

  exportDocJPG(){
    if(!this.pages.length){ alert('No pages - Scan in index.html'); return; }
    const a = document.createElement('a');
    a.href = this.pages[this.pages.length-1];
    a.download = `GodEye-v19-${Date.now()}.jpg`; a.click();
  },

  exportDocPDF(){
    if(!this.pages.length){ alert('No pages'); return; }
    const {jsPDF} = window.jspdf;
    const doc = new jsPDF();
    this.pages.forEach((img,i)=>{
      if(i>0) doc.addPage();
      doc.addImage(img,'JPEG',0,0,210,297);
    });
    doc.save(`GodEye-v19-IMG-PDF-${Date.now()}.pdf`);
  },

  exportSearchablePDF: async function(text){
    if(!this.pages.length){ alert('No pages'); return; }
    const {jsPDF} = window.jspdf;
    const doc = new jsPDF();
    for(let i=0;i<this.pages.length;i++){
      if(i>0) doc.addPage();
      doc.addImage(this.pages[i],'JPEG',0,0,210,297);
      try{
        const worker = await Tesseract.createWorker('eng');
        const ret = await worker.recognize(this.pages[i]);
        await worker.terminate();
        doc.setFontSize(1);
        doc.setTextColor(255,255,255);
        doc.text(doc.splitTextToSize(ret.data.text,190),5,5);
        localStorage.setItem('lastOCR', ret.data.text);
      }catch(e){}
    }
    doc.save(`GodEye-v19-Searchable-${Date.now()}.pdf`);
  },

  exportPDF(text){
    const t = text || localStorage.getItem('lastOCR')||'God Eye v1.9';
    const {jsPDF} = window.jspdf;
    const doc = new jsPDF();
    doc.setFontSize(10);
    const lines = doc.splitTextToSize(t,180);
    doc.text(lines,10,10);
    let y = 10 + lines.length*6 + 10;
    this.pages.forEach(img=>{
      if(y>200){ doc.addPage(); y=10; }
      try{ doc.addImage(img,'JPEG',10,y,60,80); y+=85; }catch{}
    });
    doc.save(`GodEye-v19-Full-${Date.now()}.pdf`);
  },

  exportDOCXwithImages: async function(){
    if(!this.pages.length){ alert('No pages'); return; }
    if(typeof docx==='undefined'){ alert('docx.js loading - wait 2 sec and try again'); return; }
    let children = [
      new docx.Paragraph({children:[new docx.TextRun({text:`WanaLab God Eye v1.9 FINAL - ${new Date().toLocaleString()}`,bold:true,size:28})]}),
      new docx.Paragraph({text:`Pages: ${this.pages.length} | Manual Crop + ROI + Tracking + Thermal + Tap Bill`, spacing:{after:200}})
    ];
    for(let i=0;i<this.pages.length;i++){
      children.push(new docx.Paragraph({text:`Page ${i+1}`, heading:docx.HeadingLevel.HEADING_1}));
      try{
        const worker = await Tesseract.createWorker('eng');
        const ret = await worker.recognize(this.pages[i]);
        await worker.terminate();
        ret.data.text.split('\n').forEach(l=>{
          if(l.trim()) children.push(new docx.Paragraph({children:[new docx.TextRun(l)]}));
        });
      }catch{}
      try{
        const base64 = this.pages[i].split(',')[1];
        const bytes = Uint8Array.from(atob(base64), c=>c.charCodeAt(0));
        children.push(new docx.Paragraph({
          children:[new docx.ImageRun({data:bytes,[STRIPPED]
        }));
      }catch{}
    }
    const doc = new docx.Document({sections:[{children}]});
    const blob = await docx.Packer.toBlob(doc);
    this.down(blob, `GodEye-v19-DOCX-IMG-${Date.now()}.docx`);
  },

  exportBill(){
    const items = JSON.parse(localStorage.getItem('wanaBillItems')||'[]');
    const total = localStorage.getItem('wanaTotalBill')||'0';
    if(!items.length){ alert('No bill items - Use TAP BILL in camera'); return; }
    const {jsPDF} = window.jspdf;
    const doc = new jsPDF();
    doc.setFontSize(16);
    doc.text(`WanaLab Bill v1.9 FINAL - Total Rs.${total}`,10,14);
    doc.setFontSize(11);
    let y=24;
    items.forEach((it,i)=>{
      doc.text(`${i+1}. ${it.name} - Rs.${it.price}`,10,y);
      y+=8; if(y>280){ doc.addPage(); y=10; }
    });
    doc.save(`Bill-v19-${Date.now()}.pdf`);
  },

  exportMP3(text){
    const t = (text||localStorage.getItem('lastOCR')||'God Eye v1.9').slice(0,800);
    if('speechSynthesis' in window){
      const u = new SpeechSynthesisUtterance(t);
      u.lang = 'en-US'; u.rate = 0.9;
      speechSynthesis.cancel();
      speechSynthesis.speak(u);
    } else { alert('TTS not supported - Use Chrome'); }
  },

  share(text){
    const t = text || localStorage.getItem('lastOCR')||'God Eye v1.9';
    if(navigator.share){ navigator.share({title:'God Eye v1.9 FINAL', text:t}).catch(()=>{}); }
    else { this.exportTXT(t); }
  },

  capturePhoto(){
    const video = document.getElementById('godVideo') || document.getElementById('camera');
    if(!video) return;
    const canvas = document.createElement('canvas');
    canvas.width = video.videoWidth; canvas.height = video.videoHeight;
    canvas.getContext('2d').drawImage(video,0,0);
    const dataUrl = canvas.toDataURL('image/jpeg',0.92);
    const pages = JSON.parse(localStorage.getItem('wanaScannedPages')||'[]');
    pages.push(dataUrl);
    localStorage.setItem('wanaScannedPages',JSON.stringify(pages));
    this.pages = pages;
    const a = document.createElement('a');
    a.download = `GodEye-v19-Photo-${Date.now()}.jpg`;
    a.href = dataUrl; a.click();
  }
};
