# Project created via BDEngine

execute unless data storage project:mem {init:1b} run scoreboard objectives add gm_cam_client dummy
execute unless data storage project:mem {init:1b} run data modify storage project:mem init set value 1b
summon block_display ~-0.5 ~-0.5 ~-0.5 {Tags:["project","project_root"]}

summon block_display ~ ~ ~ {Tags:["project","project_camera"]}

data merge entity @e[type=block_display,tag=project_camera,distance=..1,limit=1,sort=nearest] {teleport_duration:2}

execute as @e[tag=project_root,type=block_display,distance=..1,limit=1,sort=nearest] at @s run summon block_display ~ ~ ~ {Passengers:[{id:"minecraft:block_display",block_state:{Name:"minecraft:purple_wool",Properties:{}},transformation:[1f,0f,0f,0f,0f,1f,0f,0f,0f,0f,1f,0f,0f,0f,0f,1f],Tags:["project_0"]}],Tags:["project","project_p0"]}

ride @e[tag=project_p0,limit=1,sort=nearest] mount @e[tag=project_root,type=block_display,limit=1,sort=nearest]

