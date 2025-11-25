import json
import logging
import sys
from pathlib import Path
from typing import Iterable, List

import cv2
import numpy as np

# from ml_api.ml_model import model  # import fine-tuned model
from microactivity_detector import MicroactivityDetector

detector = MicroactivityDetector(model_path="YOLO-model/best.pt", debug=False)


def _to_frame_list(frame_paths) -> List[str]:
    """Ensure frame paths are returned as a non-empty list of strings."""

    if isinstance(frame_paths, (str, Path)):
        frame_list: Iterable[str] = [str(frame_paths)]
    elif isinstance(frame_paths, Iterable):
        frame_list = [str(Path(p)) for p in frame_paths]
    else:
        return []

    return [p for p in frame_list if p]


def model_inference(frame_paths, use_raul=True):
    """
    Unified inference function allowing to switch between Sejma’s and Raúl’s models.
    """

    if use_raul:
        # Run Raúl's YOLOv11 segmentation-based microactivity detector
        if not frame_paths:
            return {"error": "No image found!"}
        #print("[INFO] Using Raúl’s YOLOv11 segmentation model...")
        result = detector.infer_frames(frame_paths)  # process up to 5 frames
        return result

def format_model_output(results, frame_paths):
    if not results:
        logging.error("Model returned no results!")
        return {"error": "Empty result"}

    all_probabilities = []

    for result in results:
        if not hasattr(result, "probs") or result.probs is None:
            logging.error("No probability returned by the model.")
            continue
        all_probabilities.append(result.probs.data.tolist())

    if not all_probabilities:
        logging.error("No valid probabilities returned by the model.")
        return {"error": "No valid probabilities returned by model!"}

    avg_prob = np.mean(all_probabilities, axis=0)
    class_names = results[0].names
    class_probs = {
        class_names[i]: float(prob) for i, prob in enumerate(avg_prob)
    }

    top_class = max(class_probs, key=class_probs.get)
    top_confidence = class_probs[top_class]
    logging.info("Probabilities returned by the model: %s", class_probs)
    return {
        "top_class": top_class,
        "confidence": top_confidence,
        "all_class_probabilities": class_probs,
        "frame_paths": frame_paths,
    }


def main():
    logging.basicConfig(level=logging.INFO)
    try:
        frames = _to_frame_list(sys.argv[1:])
        if not frames:
            raise ValueError("No valid frame paths provided")
        result = model_inference(frames, use_raul=True)
        print(json.dumps(result))
    except Exception as exc:  # pylint: disable=broad-except
        logging.exception("Inference failed: %s", exc)
        print(json.dumps({"error": str(exc)}))
        sys.exit(1)


if __name__ == "__main__":
    main()