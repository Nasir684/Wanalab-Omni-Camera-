// WanaLab God Eye - Emergency Fix Engine v1.1
console.log("God Eye Engine Loading...");
window.GodEye = {
  video: null,
  init: async function() {
    try {
      const v = document.querySelector('video') || document.getElementById('camera') || document.getElementById('godVideo');
      if(!v){ console.log("No video tag found"); return; }
      this.video = v;
      v.setAttribute('autoplay',''); v.setAttribute('playsinline',''); v.muted=true;
      const stream = await navigator.mediaDevices.getUserMedia({video:{facingMode:"environment"}, audio:false});
      v.srcObject = stream;
      await v.play();
      document.body.style.background="#000";
      console.log("Camera LIVE");
    } catch(e){
      alert("Camera Error: "+e.message+" - Allow camera permission in lock icon");
      console.error(e);
    }
  }
};
window.addEventListener('load', ()=>{ setTimeout(()=>window.GodEye.init(), 500); });
document.addEventListener('click', ()=>{ if(window.GodEye.video && !window.GodEye.video.srcObject) window.GodEye.init(); }, {once:true});
