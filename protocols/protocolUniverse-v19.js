{
  "protocol_id": "counting_pro_v200",
  "name": "Counting Pro v2.0 - Anti-Mix + Barcode + Manual",
  "version": "2.0",
  "app_version": "1.6.7",
  "shop_version": "2.0",
  "description": "Fixes hand counted as bottle - ID tracking + filters + Barcode Count + Manual Count - Professional Shop Counting",
  "last_updated": "2026-11-21",
  "protocols": {
    "id_tracking": {
      "enabled": true,
      "method": "centroid",
      "max_distance_px": 60,
      "timeout_ms": 800,
      "cleanup_ms": 1200,
      "note": "Same object = Same ID, do not count twice"
    },
    "line_crossing": {
      "enabled": true,
      "line_y_percent": 50,
      "count_in": true,
      "count_out": true,
      "note": "Count only when object crosses line - Best for conveyor / door"
    },
    "zone_roi": {
      "enabled": true,
      "default_zone": { "x1_percent": 15, "y1_percent": 25, "x2_percent": 85, "y2_percent": 75 },
      "count_only_inside": true,
      "note": "Ignore hand outside zone - Hand is outside shelf zone"
    },
    "class_filter": {
      "enabled": true,
      "default": "bottle",
      "allowed": ["bottle", "pepsi", "coke", "water", "juice", "milk", "box", "bag", "cup", "person", "chair", "car", "all"],
      "shop_presets": ["bottle", "pepsi", "coke", "water", "juice", "milk", "box", "bag"]
    },
    "anti_mix_filters": {
      "confidence_threshold": 0.62,
      "min_area_percent": 0.8,
      "max_area_percent": 18,
      "shape_check": true,
      "shape_rule_bottle": "height > width * 1.2",
      "shape_rule_box": "width approx = height",
      "shape_rule_bag": "width > height",
      "hand_reject": {
        "enabled": true,
        "rule": "If object is skin color + moves fast + large area + no stable shape for 3 frames = Hand, do NOT count",
        "skin_detection": true,
        "max_speed_px_per_frame": 80
      },
      "stability": {
        "enabled": true,
        "frames_required": 3,
        "history_size": 5,
        "majority_vote": true,
        "delay_ms": 1000
      },
      "size_presets": {
        "bottle": { "min": 0.008, "max": 0.18, "conf": 0.62, "shape": true },
        "pepsi": { "min": 0.008, "max": 0.18, "conf": 0.62, "shape": true },
        "coke": { "min": 0.008, "max": 0.18, "conf": 0.62, "shape": true },
        "water": { "min": 0.008, "max": 0.20, "conf": 0.62, "shape": true },
        "box": { "min": 0.02, "max": 0.30, "conf": 0.60, "shape": true },
        "bag": { "min": 0.02, "max": 0.35, "conf": 0.60, "shape": true },
        "person": { "min": 0.05, "max": 0.60, "conf": 0.55, "shape": false }
      }
    },
    "density": {
      "low": "<=3",
      "med": "4-8",
      "high": ">8",
      "note": "If high density >8, show warning: Use Barcode Count, Camera AI may be wrong"
    },
    "methods": {
      "method_1_camera_ai": {
        "enabled": true,
        "name": "Camera AI - Blob + Edge",
        "accuracy": "75-90% - Good for shelf count, not for cash",
        "use_when": "Counting shelf, stock check"
      },
      "method_2_barcode_scan": {
        "enabled": true,
        "name": "Barcode Scan Count - Scan = +1",
        "accuracy": "100% - Most accurate for shop",
        "use_when": "Selling, stock in, stock out, cash count",
        "engines": ["native", "zxing", "quagga2", "manual"],
        "auto_stock": true,
        "note": "Scan each item, Count +1, Stock -1, Bill +1 - All automatic"
      },
      "method_3_manual": {
        "enabled": true,
        "name": "Manual + / -",
        "accuracy": "100% - User controls",
        "use_when": "Fix camera error, small count"
      }
    },
    "logging": {
      "enabled": true,
      "export_csv": true,
      "export_xlsx": true,
      "fields": ["time", "id", "type", "class", "method", "confidence", "count_total"]
    },
    "sync": {
      "enabled": true,
      "protocols": ["mqtt", "websocket", "http_post"],
      "mqtt_topic": "godeye/shop/count",
      "note": "Every count change sends to PC / Cloud via MQTT + WS + HTTP"
    }
  }
  }
