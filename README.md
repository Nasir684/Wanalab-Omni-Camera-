
### How Protocol Universe Works (1 Repo)
1. **Offline:** Core 10 modes + generic UVC driver works without internet + cached protocols from localStorage
2. **Online:** `protocol-universe.js` fetches `protocols/index.json` → loads all `protocols/*.json` from same repo → 24+ protocols active → cached for offline next time
3. **Community:** Anyone adds device to `protocols/custom.json` or new file via GitHub PR → Auto available to all users worldwide next time online

**No second repo needed. Same repo = app + universe.**

---

## 🔧 Deploy in 1 Click

**Vercel (Recommended):**
1. Push this repo to `wanalab/wana-omni-camera`
2. Vercel.com → Add New Project → Import GitHub repo
3. Framework: Other → Build command: none → Output: . → Deploy
4. Live in 30 sec

**Cloudflare Pages:** Same flow.

**Local:** `python -m http.server 8000` → open `http://localhost:8000`

---

## 💰 Pricing - Global

- **Free $0 Forever:** All 10 modes, USB devices, Export TXT/PDF/PNG, Protocol universe, Offline
- **Pro $19 Lifetime (one-time):** Thermal fusion, SuperRes unlimited, DOCX/JSON/MP3/OBJ/MP4, Remove footer, Commercial license
- **Enterprise:** White label + On-premise → Email Nasir@wanalab.org

**Pay:** GitHub Sponsors / Wise / Contact email

**Ads:** Ethical only, online only, never on camera viewfinder, never offline. Only on export/devices pages. Microscope/telescope affiliates.

---

## 🔍 Keywords - SEO - Why Users Will Find You

**Full list in `keywords.json` and `index.html` meta - 300+ keywords:**
Book scanner, microscope 1000x, usb microscope, telescope app, night vision, thermal camera flir, 3D measure, live translate, safety detector, usb endoscope, otoscope, ip camera rtsp, drone camera, phone lab, STEM app, offline OCR, super resolution, etc.

Search "usb microscope app android", "book scanner offline", "phone telescope", "thermal app flir" → You.

---

## 🤝 Contribute Protocol (Add Your Device)

1. Fork repo
2. Edit `protocols/custom.json` or create `protocols/my-device.json`
3. Add: `{ "id": "my-device", "name": "My Device", "type": "UVC", "vendorIds": [1234], "mode": "micro" }`
4. Add file name to `protocols/index.json` → `files` array
5. PR → Merged → Auto loaded for all users worldwide when online

**Contact:** Nasir@wanalab.org | Subject: God Eye Protocol

---

## 📄 License

Free for personal use with WanaLab footer. Pro $19 Lifetime removes footer + commercial license.

© WanaLab | Wana-Omni-Camera | God Eye v1.0 | Global | Founder Nasir Khan | Nasir@wanalab.org | Offline First, Global Scale

---

**Version:** God Eye v1.0 | Build: 2026.11.15 FINAL | 1 Repo | 24 Protocols | Offline + Online Universe | Global
