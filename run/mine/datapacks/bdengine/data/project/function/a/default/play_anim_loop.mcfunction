# Project created via BDEngine (scoped to current animation instance)

# Called from Skript as the specific camera entity (tagged with a unique id).
# Use @s as the associated camera, and only modify its own root/children.

tag @s remove animation_pause
tag @s add animation_loop

# Set teleport_duration on this camera only (no global @e scan).
data modify entity @s teleport_duration set value 0

# Start keyframes for this animation's root only.
schedule function project:k/default/start 0.1s