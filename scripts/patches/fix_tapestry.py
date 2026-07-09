import re

# Read the old drawWeapon text to extract drawFrontArmAndWeapon
with open("old_draw_weapon.txt", "r") as f:
    old_text = f.read()
    
# Split by private fun drawFrontArmAndWeapon
parts = old_text.split("private fun drawFrontArmAndWeapon")
if len(parts) > 1:
    draw_front_arm_code = "    private fun drawFrontArmAndWeapon" + parts[1]
else:
    print("Error: Could not find drawFrontArmAndWeapon in old text")
    exit(1)

with open("app/src/main/java/com/example/game/TapestryRenderer.kt", "r") as f:
    current_text = f.read()
    
# We will insert draw_front_arm_code just before drawBackArmAndShield
if "private fun drawBackArmAndShield" in current_text:
    current_text = current_text.replace("    private fun drawBackArmAndShield", draw_front_arm_code + "\n    private fun drawBackArmAndShield")
    with open("app/src/main/java/com/example/game/TapestryRenderer.kt", "w") as f:
        f.write(current_text)
    print("Restored drawFrontArmAndWeapon successfully")
else:
    print("Error: could not find drawBackArmAndShield to insert before")

