# Project created via BDEngine (scoped)

# Only advance or stop the root that belongs to this camera.

execute as @e[tag=project_root,distance=..2,limit=1,sort=nearest] if entity @s[tag=animation_loop] at @s run function project:k/default/keyframe_0
execute as @e[tag=project_root,distance=..2,limit=1,sort=nearest] unless entity @s[tag=animation_loop] at @s run function project:_/stop_anim