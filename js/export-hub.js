{
  "version": "v99.4 PRO FINAL EXPORT HUB",
  "author": "Nasir Khan",
  "formats": [
    {
      "id": "jpg-single",
      "name": "JPG Single",
      "method": "exportHub.exportDocJPG()",
      "button": "📸 JPG Single",
      "protocol": "canvas-to-jpeg",
      "status": "active",
      "position": "top-left"
    },
    {
      "id": "img-pdf",
      "name": "IMG PDF",
      "method": "exportHub.exportDocPDF()",
      "button": "📄 IMG PDF",
      "protocol": "jspdf-image",
      "status": "active",
      "position": "top-right"
    },
    {
      "id": "searchable-pdf",
      "name": "Searchable PDF PRO",
      "method": "exportHub.exportSearchablePDF()",
      "button": "🔍 Searchable PDF PRO",
      "protocol": "jspdf + tesseract-5",
      "status": "active",
      "position": "middle-left"
    },
    {
      "id": "docx-img",
      "name": "DOCX + Images",
      "method": "exportHub.exportDOCXwithImages()",
      "button": "📝 DOCX + Images",
      "protocol": "docx-8.5 + tesseract",
      "status": "active",
      "position": "middle-right"
    },
    {
      "id": "bill-pdf",
      "name": "BILL PDF Rs.",
      "method": "exportHub.exportBill()",
      "button": "🧾 BILL PDF",
      "protocol": "jspdf-bill-tap",
      "status": "active",
      "position": "center"
    },
    {
      "id": "json",
      "name": "JSON",
      "method": "exportHub.exportJSON()",
      "button": "📦 JSON",
      "protocol": "json-stringify",
      "status": "active",
      "position": "bottom-left"
    },
    {
      "id": "txt",
      "name": "TXT",
      "method": "exportHub.exportTXT()",
      "button": "📝 TXT",
      "protocol": "blob-text",
      "status": "active",
      "position": "bottom-middle"
    },
    {
      "id": "pdf-full",
      "name": "Text + Img PDF",
      "method": "exportHub.exportPDF()",
      "button": "📄 Text + Img PDF",
      "protocol": "jspdf-text-image",
      "status": "active",
      "position": "bottom-right"
    }
  ],
  "all_buttons_working": true,
  "total_formats": 8,
  "test_url": "https://nasir684.github.io/Wanalab-Omni-Camera-/export.html?v=994"
}
