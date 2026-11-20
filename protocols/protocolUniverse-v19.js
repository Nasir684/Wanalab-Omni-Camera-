{
  "protocol_id": "counting_pro_v141",
  "name": "Counting Pro Anti-Mix",
  "version": "1.4.1",
  "description": "Fixes hand counted as bottle - ID tracking + filters",
  "protocols": {
    "id_tracking": {
      "enabled": true,
      "method": "centroid",
      "max_distance_px": 60,
      "timeout_ms": 800,
      "cleanup_ms": 1200
    },
    "line_crossing": {
      "enabled": true,
      "line_y_percent": 50,
      "count_in": true,
      "count_out": true
    },
    "zone_roi": {
      "enabled": true,
      "default_zone": { "x1_percent": 15, "y1_percent": 25, "x2_percent": 85, "y2_percent": 75 },
      "count_only_inside": true
    },
    "class_filter": {
      "enabled": true,
      "default": "bottle",
      "allowed": ["bottle", "person", "cup", "car", "chair", "all"]
    },
    "anti_mix_filters": {
      "confidence_threshold": 0.62,
      "min_area_percent": 0.8,
      "max_area_percent": 18,
      "shape_check": true,
      "shape_rule_bottle": "height > width * 1.2",
      "stability": {
        "enabled": true,
        "frames_required": 3,
        "history_size": 5,
        "majority_vote": true,
        "delay_ms": 1000
      },
      "size_presets": {
        "bottle": { "min": 0.008, "max": 0.18, "conf": 0.62, "shape": true },
        "person": { "min": 0.05, "max": 0.60, "conf": 0.55, "shape": false }
      }
    },
    "density": {
      "low": "<=3",
      "med": "4-8",
      "high": ">8"
    },
    "logging": {
      "enabled": true,
      "export_csv": true,
      "fields": ["time", "id", "type", "class"]
    }
  }
    }
