# Project created via BDEngine (scoped)

# Called after play_anim/loop for a specific animation. @s is the camera for
# that animation; its root has the same unique tag and is nearby.

execute as @e[tag=project_root,distance=..2,limit=1,sort=nearest] at @s run function project:k/default/keyframe_0
