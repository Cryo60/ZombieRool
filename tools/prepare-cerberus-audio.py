"""Prepare Cerberus audio using the map maker's selected desktop clips."""
from pathlib import Path
import runpy
runpy.run_path(str(Path(__file__).with_name("configure-cerberus-sounds.py")),run_name="__main__")
