# Project created via BDEngine (scoped)

# When called from Skript, use the unique id tag in the selector there.
# Here, just delete entities that are part of the calling animation graph.

execute as @e[type=minecraft:block_display,tag=project,limit=50,sort=nearest] on passengers run kill @s
execute as @e[type=minecraft:block_display,tag=project,limit=50,sort=nearest] run kill @s
