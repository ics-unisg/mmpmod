"""
microactivity_detector.py
-----------------------------------------------------------
YOLOv11-based Microactivity Detection (Integration-Ready)
Compatible with Sejma Sijaric’s Ambiguity Resolution System

Performs fine-grained detection of microactivities using YOLOv11 segmentation.
Applies mask–box fusion, temporal persistence for disinfectant wipe, and
a 5-frame aggregation window to provide stable activity predictions.

Microactivities:
 - Injection
 - Tourniquet
 - Disinfection

Output format (Sejma-compatible):
{
    "activity": "Injection",
    "confidence": 0.82
}

Author: Raúl Jiménez Cruz
-----------------------------------------------------------
"""

import os
import cv2
import numpy as np
import pandas as pd
import logging
import threading
from collections import deque, Counter
from ultralytics import YOLO


class MicroactivityDetector:
    def __init__(self, model_path=None, debug=False, window_size=5):
        """
        Initialize YOLOv11 segmentation model for microactivity detection.
        :param model_path: Path to the trained best.pt segmentation model.
        :param debug: If True, enables CSV logging for analysis.
        """
        if model_path is None:
            # Default path inside Sejma’s system
            model_path = os.path.join(os.path.dirname(__file__), "YOLO-model/best.pt")

        self.model_path = os.path.abspath(model_path)
        self.debug = debug
        self.lock = threading.Lock()
        self.window_size = window_size # Number of frames for aggregation

        # --- Load YOLOv11 model ---
        self.model = YOLO(self.model_path)
        logging.info(f"[INFO] YOLOv11 model loaded from: {self.model_path}")

        # --- Canonical object names ---
        self.NAME_SYNONYMS = {
            "syringe": ["white and blue syringe", "syringe", "blue-white syringe"],
            "gloves": ["white gloves", "gloves"],
            "training arm": ["orange arm", "training arm", "arm"],
            "rubber band": ["yellow rubber band", "rubber band", "tourniquet"],
            "disinfectant wipe": [
                "disinfectant wipe", "white wipe", "wipe", "sponge",
                "gauze", "cotton", "cotton pad", "alcohol pad",
                "swab", "swab pad", "pad", "towel"
            ]
        }
        self.raw2canon = {s.lower(): canon for canon, syns in self.NAME_SYNONYMS.items() for s in syns}
        
        # --- Buffers ---
        self.frame_buffer = deque(maxlen=window_size)
        self.activity_buffer = deque(maxlen=window_size)

        # --- Temporal persistence for wipe --- Don´t use if is no a video
        #self.PERSIST_WIPE = 8
        #self.wipe_last_seen = -999
        #self.wipe_buffer = None
        self.frame_id = 0

        # --- Debugging ---
        self.debug_records = []

    # ==========================================================
    # Helper functions
    # ==========================================================
    def compute_iou(self, a, b):
        """Compute bounding box IoU."""
        xA, yA = max(a[0], b[0]), max(a[1], b[1])
        xB, yB = min(a[2], b[2]), min(a[3], b[3])
        inter = max(0, xB - xA) * max(0, yB - yA)
        if inter <= 0:
            return 0.0
        areaA = (a[2] - a[0]) * (a[3] - a[1])
        areaB = (b[2] - b[0]) * (b[3] - b[1])
        return inter / float(areaA + areaB - inter + 1e-6)

    def bbox_center(self, b):
        """Return bounding box center coordinates."""
        return ((b[0] + b[2]) * 0.5, (b[1] + b[3]) * 0.5)

    def fused_iou(self, mask_a, mask_b, box_a, box_b, alpha=0.7):
        """Weighted IoU (mask + box)."""
        if mask_a is None or mask_b is None:
            iou_mask = 0.0
        else:
            inter = cv2.bitwise_and(mask_a, mask_b)
            inter_area = np.count_nonzero(inter)
            if inter_area == 0:
                iou_mask = 0.0
            else:
                union_area = np.count_nonzero(mask_a) + np.count_nonzero(mask_b) - inter_area
                iou_mask = inter_area / float(union_area + 1e-6)
        iou_box = self.compute_iou(box_a, box_b)
        return alpha * iou_mask + (1 - alpha) * iou_box


    # ==========================================================
    # Core logic: fixed 5-frame batch inference
    # ==========================================================
    def infer_frames(self, frame_list):
        """
        Performs inference over a fixed set of 5 frames provided by Sejma's system.
        Aggregates detected activities and computes robust confidence for stability.
        """
        if not frame_list or len(frame_list) == 0:
            return {"activity": "No frames", "confidence": 0.0}
        #print(f"[INFO] Processing {len(frame_list)} frames for microactivity detection..")
        activities, confidences = [], []
        with self.lock:
            for frame_path in frame_list:
                frame = cv2.imread(frame_path)
                self.frame_id += 1
                res = self._process_single_frame(frame)
                if res:
                    activities.append(res["activity"])
                    confidences.append(res["confidence"])

        # Remove empty / unclear frames
        valid_pairs = [(a, c) for a, c in zip(activities, confidences) if a != "No clear activity"]
        if not valid_pairs:
            return {"activity": "No detection", "confidence": 0.0}

        acts, confs = zip(*valid_pairs)
        main_act = Counter(acts).most_common(1)[0][0]

        # Compute mean and robust confidence for the dominant activity
        act_confs = [c for a, c in zip(acts, confs) if a == main_act]
        conf_mean = np.mean(act_confs)
        conf_std = np.std(act_confs)
        robust_conf = conf_mean - 0.5 * conf_std  # robust measure

        # Clip to [0, 1] for safety
        robust_conf = float(np.clip(robust_conf, 0.0, 1.0))

        return {"activity": main_act, "confidence": round(robust_conf, 2)}

    # ==========================================================
    # Main inference function
    # ==========================================================
    def _process_single_frame(self, frame):
        """Performs detection and activity classification for one frame."""
        H, W, _ = frame.shape
        img_diag = (W ** 2 + H ** 2) ** 0.5

        res = self.model(frame, conf=0.25, iou=0.55, imgsz=640, verbose=False)[0]
        if not hasattr(res, "masks") or res.masks is None:
            return {"activity": "No clear activity", "confidence": 0.0}

        cls_ids = res.boxes.cls.cpu().numpy().astype(int)
        boxes = res.boxes.xyxy.cpu().numpy()
        masks = res.masks.data.cpu().numpy()
        confidences = res.boxes.conf.cpu().numpy()
        names = [self.model.names[i].lower() for i in cls_ids]

        # Group masks and boxes by canonical class name
        mask_by_class, box_by_class, conf_by_class = {}, {}, {}
        for cname, m, b, conf in zip(names, masks, boxes, confidences):
            cname = self.raw2canon.get(cname, cname)
            if cname not in mask_by_class:
                mask_by_class[cname], box_by_class[cname], conf_by_class[cname] = [], [], []
            mask_by_class[cname].append((m > 0.5).astype(np.uint8) * 255)
            box_by_class[cname].append(b)
            conf_by_class[cname].append(float(conf))

        # Extract objects    Key objects
        GLOVES = mask_by_class.get("gloves", [])
        ARM = mask_by_class.get("training arm", [])
        SYR = mask_by_class.get("syringe", [])
        RUBBER = mask_by_class.get("rubber band", [])
        WIPE = mask_by_class.get("disinfectant wipe", [])

        GBOXES = box_by_class.get("gloves", [])
        ABXES = box_by_class.get("training arm", [])
        SBXES = box_by_class.get("syringe", [])
        RBXES = box_by_class.get("rubber band", [])
        WBXES = box_by_class.get("disinfectant wipe", [])

        # Temporal persistence for disinfectant wipe --- Don´t use if is no a video
        #if len(WIPE) > 0:
        #    self.wipe_last_seen = self.frame_id
        #    self.wipe_buffer = (WIPE, WBXES)
        #elif self.frame_id - self.wipe_last_seen <= self.PERSIST_WIPE and self.wipe_buffer:
        #    WIPE, WBXES = self.wipe_buffer

        # Merge gloves
        glove_mask, glove_box = None, None
        if len(GLOVES) >= 2 and len(GBOXES) >= 2:
            glove_mask = cv2.bitwise_or(GLOVES[0], GLOVES[1])
            x1 = min(GBOXES[0][0], GBOXES[1][0])
            y1 = min(GBOXES[0][1], GBOXES[1][1])
            x2 = max(GBOXES[0][2], GBOXES[1][2])
            y2 = max(GBOXES[0][3], GBOXES[1][3])
            glove_box = [x1, y1, x2, y2]
        
        # Average contact metric between gloves and object.
        def avg_contact(glove_mask, glove_box, obj_masks, obj_boxes):
            if glove_mask is None or glove_box is None or len(obj_masks) == 0:
                return 0.0, 1.0
            ious, dists = [], []
            g_cx, g_cy = self.bbox_center(glove_box)
            for m, b in zip(obj_masks, obj_boxes):
                iou = self.fused_iou(glove_mask, m, glove_box, b)
                bx, by = self.bbox_center(b)
                d = ((g_cx - bx)**2 + (g_cy - by)**2)**0.5 / (img_diag + 1e-6)
                ious.append(iou)
                dists.append(d)
            return np.mean(ious), np.min(dists)

        # --- Compute interactions ---
        iou_syr, d_syr = avg_contact(glove_mask, glove_box, SYR, SBXES)
        iou_rub, d_rub = avg_contact(glove_mask, glove_box, RUBBER, RBXES)
        iou_wip, d_wip = avg_contact(glove_mask, glove_box, WIPE, WBXES)
        iou_arm, d_arm = avg_contact(glove_mask, glove_box, ARM, ABXES)

        # Rule-based activity detection
        activity = "No clear activity"
        # Static Thresholds for contact
        #touching_syr  = (iou_syr > 0.05) or (d_syr < 0.12)
        #touching_rub  = (iou_rub > 0.05) or (d_rub < 0.12)
        #touching_wipe = (iou_wip > 0.03) or (d_wip < 0.18)

        if len(GLOVES) >= 2 and len(ARM) > 0 and len(SYR) > 0 and (iou_syr > 0.05 or d_syr < 0.12):
            activity = "Injection"
        elif len(GLOVES) >= 2 and len(ARM) > 0 and (iou_rub > 0.05 or d_rub < 0.12) and len(SYR) == 0:
            activity = "Tourniquet"
        elif len(GLOVES) >= 2 and len(ARM) > 0 and not SYR and (len(WIPE) > 0 or iou_arm > 0.01):
            activity = "Disinfection"

        # Confidence aggregation
        def _mean_or_zero(seq): return float(np.mean(seq)) if len(seq) > 0 else 0.0
        conf_glv = _mean_or_zero(conf_by_class.get("gloves", []))
        conf_arm = _mean_or_zero(conf_by_class.get("training arm", []))
        conf_syr = _mean_or_zero(conf_by_class.get("syringe", []))
        conf_rub = _mean_or_zero(conf_by_class.get("rubber band", []))
        conf_wip = _mean_or_zero(conf_by_class.get("disinfectant wipe", []))

        if activity == "Injection":
            conf_activity = np.mean([conf_glv, conf_arm, conf_syr])
        elif activity == "Tourniquet":
            conf_activity = np.mean([conf_glv, conf_arm, conf_rub])
        elif activity == "Disinfection":
            conf_activity = np.mean([conf_glv, conf_arm, conf_wip]) if len(WIPE) > 0 else np.mean([conf_glv, conf_arm])
        else:
            conf_activity = 0.0

        return {"activity": activity, "confidence": round(float(conf_activity), 4)}

# ==========================================================
# Standalone test mode
# ==========================================================
if __name__ == "__main__":
    model_path = os.path.join(os.path.dirname(__file__), "YOLO-model/best.pt")
    video_path = os.path.join(os.path.dirname(__file__), "../../test_videos/sample.mp4")
    cap = cv2.VideoCapture(video_path)
    frames = []
    detector = MicroactivityDetector(model_path=model_path, debug=True)

    while True:
        ok, frame = cap.read()
        if not ok:
            break
        frames.append(frame)
        if len(frames) == 5:
            result = detector.infer_frames(frames)
            print(f"[Batch] Activity={result['activity']} | Conf={result['confidence']}")
            frames = []

    cap.release()