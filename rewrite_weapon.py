import re

with open("app/src/main/java/com/example/game/TapestryRenderer.kt", "r") as f:
    lines = f.readlines()

start_idx = -1
end_idx = -1
for i, line in enumerate(lines):
    if "private fun drawWeapon(scope: DrawScope, hx: Float, hy: Float, fighter: FighterState) {" in line:
        start_idx = i
    elif start_idx != -1 and "private fun drawBackArmAndShield" in line:
        end_idx = i
        break

if start_idx != -1 and end_idx != -1:
    print(f"Found drawWeapon from {start_idx} to {end_idx}")
