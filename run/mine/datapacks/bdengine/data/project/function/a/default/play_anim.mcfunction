# Project created via BDEngine (fixed)

# @s is the camera because Skript runs "as ...project_camera..."
tag @s remove animation_pause
tag @s remove animation_loop
data modify entity @s teleport_duration set value 0
schedule function project:k/default/start 2t
