{
  "version": "1.4.2",
  "author": "Nasir Khan",
  "app": "WanaLab God Eye",
  "formats": [
    {
      "id": "jpg-single",
      "name": "JPG Single",
      "method": "exportHub.exportDocJPG()",
      "button": "📸 JPG Single",
      "protocol": "canvas-to-jpeg",
      "status": "active",
      "position": "top-left",
      "needsLib": []
    },
    {
      "id": "img-pdf",
      "name": "IMG PDF",
      "method": "exportHub.exportDocPDF()",
      "button": "📄 IMG PDF",
      "protocol": "jspdf-image",
      "status": "active",
      "position": "top-right",
      "needsLib": ["jspdf"]
    },
    {
      "id": "searchable-pdf",
      "name": "Searchable PDF PRO",
      "method": "exportHub.exportSearchablePDF()",
      "button": "🔍 Searchable PDF PRO",
      "protocol": "jspdf + tesseract-5",
      "status": "active",
      "position": "middle-left",
      "needsLib": ["jspdf", "tesseract"],
      "note": "Lazy-load Tesseract only on tap - don't load on page load"
    },
    {
      "id": "docx-img",
      "name": "DOCX + Images",
      "method": "exportHub.exportDOCXwithImages()",
      "button": "📝 DOCX + Images",
      "protocol": "docx-8.5 + tesseract",
      "status": "active",
      "position": "middle-right",
      "needsLib": ["docx", "tesseract"]
    },
    {
      "id": "bill-pdf",
      "name": "BILL PDF Rs.",
      "method": "exportHub.exportBill()",
      "button": "🧾 BILL PDF",
      "protocol": "jspdf-bill-tap",
      "status": "active",
      "position": "center",
      "needsLib": ["jspdf"]
    },
    {
      "id": "json",
      "name": "JSON",
      "method": "exportHub.exportJSON()",
      "button": "📦 JSON",
      "protocol": "json-stringify",
      "status": "active",
      "position": "bottom-left",
      "needsLib": []
    },
    {
      "id": "txt",
      "name": "TXT",
      "method": "exportHub.exportTXT()",
      "button": "📝 TXT",
      "protocol": "blob-text",
      "status": "active",
      "position": "bottom-middle",
      "needsLib": []
    },
    {
      "id": "pdf-full",
      "name": "Text + Img PDF",
      "method": "exportHub.exportPDF()",
      "button": "📄 Text + Img PDF",
      "protocol": "jspdf-text-image",
      "status": "active",
      "position": "bottom-right",
      "needsLib": ["jspdf"]
    }
  ],
  "total_formats": 8,
  "test_url": "https://nasir684.github.io/Wanalab-Omni-Camera-/export.html"
}
