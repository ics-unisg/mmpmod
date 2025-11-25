import argparse
import logging
import os
import sys
from datetime import datetime

import cv2
import yaml

logging.basicConfig(level=logging.INFO)

config_path = os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", "camera-module-config.yaml")
with open(config_path, "r") as file:
    config = yaml.safe_load(file)

SAVE_DIR = config["camera_api"]["SAVE_DIR"]
os.makedirs(SAVE_DIR, exist_ok=True)

camera = None


def start_camera():
    global camera
    if camera is not None and camera.isOpened():
        return True, "Camera already running."

    camera = cv2.VideoCapture(0)
    if not camera.isOpened():
        if camera is not None:
            camera.release()
        camera = None
        message = "Camera could not be opened. Check permissions and device availability."
        logging.error(message)
        return False, message
    return True, "Camera started."


def stop_camera():
    global camera
    if camera is None:
        return True, "Camera already stopped."

    if camera.isOpened():
        camera.release()
    camera = None
    return True, "Camera stopped."


def capture_frame():
    global camera
    if camera is None or not camera.isOpened():
        message = "Camera is not started or lost connection."
        logging.error(message)
        return False, message

    ret, frame = camera.read()
    if not ret:
        message = "Failed to read frame from camera."
        logging.error(message)
        return False, message

    timestamp = datetime.now().strftime("%Y%m%d_%H%M%S_%f")[:-3]
    filepath = os.path.abspath(os.path.join(SAVE_DIR, f"frame_{timestamp}.jpg"))
    if not cv2.imwrite(filepath, frame):
        message = "Failed to write frame to disk."
        logging.error(message)
        return False, message

    return True, filepath


def execute_command(command: str):
    command = command.strip().lower()
    if command == "start":
        return start_camera()
    if command == "capture":
        return capture_frame()
    if command == "stop":
        return stop_camera()
    if command in {"exit", "quit"}:
        stop_camera()
        return True, "__EXIT__"
    return False, f"Unknown command: {command}"


def handle_commands(commands):
    for command in commands:
        success, payload = execute_command(command)
        if payload and payload != "__EXIT__":
            print(("OK:" + payload) if success else ("ERROR:" + payload), flush=True)
        else:
            print("OK" if success else ("ERROR:" + (payload or "")), flush=True)
        if success and payload == "__EXIT__":
            return 0
        if not success:
            return 1
    return 0


def read_commands_from_stdin():
    for line in sys.stdin:
        command = line.strip()
        if not command:
            continue
        yield command


def main():
    parser = argparse.ArgumentParser(add_help=False)
    parser.add_argument("commands", nargs="*")
    args, unknown = parser.parse_known_args()

    if args.commands:
        exit_code = handle_commands(args.commands + unknown)
        sys.exit(exit_code)

    exit_code = 0
    for command in read_commands_from_stdin():
        exit_code = handle_commands([command])
        if command in {"exit", "quit"}:
            break
        if exit_code != 0:
            sys.exit(exit_code)

    if exit_code != 0:
        sys.exit(exit_code)


if __name__ == "__main__":
    main()
