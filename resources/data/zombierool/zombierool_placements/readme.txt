# ZombieRool addon placements
# One line = one machine. The addon mod registers the type in Java:
#   AddonPlacements.register("GobblegumMachine", (level, pos, facing, enabled, params) -> { ... return true; });
# ZombieRool does not ship Gobblegum. An unknown type is skipped.
#
# Format:
#   Type x y z [facing] [on|off] key=value key=value
#
# facing: north south east west up down
# on / off (or true / false): whether the machine starts enabled
# Every other token is free: price, channel, uses, color, id, map...
# map=WorldFolder limits the line to that world folder name.
#
# Example (does nothing until an addon registers GobblegumMachine):
# GobblegumMachine 12 65 -8 north on price=500 channel=1 uses=3 color=blue map=Dark Origin
#
# Other folders that are read:
#   config/zombierool/placements/*.txt
#   <world>/zombierool/placements/*.txt
