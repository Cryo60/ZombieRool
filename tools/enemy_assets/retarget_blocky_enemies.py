"""Compatibility entry point for full source-motion retargeting.
Use --extract-cod to rebuild disposable CoD inputs first. The old direction-only
retargeter discarded root translation, head motion and rotation around limbs.
"""
from rebuild_blocky_motion import main
if __name__ == '__main__':
    main()
