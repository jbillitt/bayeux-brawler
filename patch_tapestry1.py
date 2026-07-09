import sys

file_path = "C:/Users/Josh/bayeux-brawler/app/src/main/java/com/example/game/TapestryRenderer.kt"
with open(file_path, "r", encoding="utf-8") as f:
    content = f.read()

# 2.1 decapitation blood fountain
old_fountain = """            val fountainPath = Path().apply {
                moveTo(cx - 5f, cy + 10f)
                quadraticTo(cx + flyDir * 20f * progress, cy - 50f * progress, cx + flyDir * 40f * progress, cy - 10f * progress)
            }
            scope.drawPath(fountainPath, Color(0xFF9E3624), style = Stroke(width = 8f, cap = StrokeCap.Round, pathEffect = androidx.compose.ui.graphics.PathEffect.dashPathEffect(floatArrayOf(10f, 10f))))"""

new_fountain = """            val fountainProgress = progress.coerceIn(0f, 1f)
            val fountainPath = Path().apply {
                moveTo(cx - 5f, cy + 10f)
                quadraticTo(
                    cx + flyDir * 25f * fountainProgress,
                    cy - 60f * fountainProgress,
                    cx + flyDir * 45f * fountainProgress,
                    cy - 8f * fountainProgress
                )
            }
            scope.drawPath(fountainPath, Color(0xFF9E3624), style = Stroke(width = 10f, cap = StrokeCap.Round))
            scope.drawPath(fountainPath, Color(0xFFBF4040), style = Stroke(width = 5f, cap = StrokeCap.Round))
            val fountainPath2 = Path().apply {
                moveTo(cx + 3f, cy + 10f)
                quadraticTo(
                    cx + flyDir * 14f * fountainProgress,
                    cy - 42f * fountainProgress,
                    cx + flyDir * 33f * fountainProgress,
                    cy - 18f * fountainProgress
                )
            }
            scope.drawPath(fountainPath2, Color(0xFF9E3624), style = Stroke(width = 6f, cap = StrokeCap.Round))"""
content = content.replace(old_fountain, new_fountain)

# 2.2 and 2.8 character reorder and mount death
old_char_block = """            if (fighter.isDead || fighter.isDying) {
                val progress = if (fighter.isDying) (fighter.animFrame / 6f).coerceIn(0f, 1f) else 1f
                
                when (fighter.deathType) {
                    0 -> { // Fall backwards
                        rotationAngle = if (fighter.facingRight) -90f * progress else 90f * progress
                        offsetY = 65f * progress
                    }
                    1 -> { // Faceplant forward
                        rotationAngle = if (fighter.facingRight) 90f * progress else -90f * progress
                        offsetY = 65f * progress
                    }
                    2 -> { // Cartwheel of death
                        rotationAngle = if (fighter.facingRight) 630f * progress else -630f * progress
                        offsetY = 65f * progress
                    }
                    3 -> { // Squashed pancake falling over
                        rotationAngle = if (fighter.facingRight) -85f * progress else 85f * progress
                        scaleY = (if (fighter.isCrumpled) 0.6f else 1f) - 0.7f * progress
                        offsetY = 65f * progress
                    }
                    4 -> { // Knocked flying backwards landing flat
                        val flyDir = if (fighter.facingRight) -1f else 1f
                        rotationAngle = flyDir * 270f * progress
                        offsetX = flyDir * 150f * progress
                        offsetY = 65f * progress - 100f * sin(progress * Math.PI).toFloat()
                    }
                    else -> { // Fall flat
                        rotationAngle = if (fighter.facingRight) -90f * progress else 90f * progress
                        offsetY = 65f * progress
                    }
                }
            }

            withTransform({
                translate(offsetX, offsetY)
                rotate(rotationAngle, pivot = Offset(cx, cy + 80f))
                scale(1f, scaleY, pivot = Offset(cx, cy + 80f))
            }) {
                if (fighter.isMounted) {
                    drawHorse(this, cx, cy, fighter)
                }

                // 1. Draw Legs (Walking cycle)
                drawLegs(this, cx, cy, fighter)

                // 2. Draw Back Arm and Shield (behind torso)
                drawBackArmAndShield(this, cx, cy, fighter)

                // 3. Draw Tunic / Torso with Chainmail Ring-texture if equipped
                drawTorso(this, cx, cy, fighter)

                // 4. Draw Head (Skin, hair cut, mustache, helmet)
                drawHead(this, cx, cy, fighter)

                // 5. Draw Front Arm & Weapon (swinging angle)
                drawFrontArmAndWeapon(this, cx, cy, fighter)
            }"""

new_char_block = """            if (fighter.isDead || fighter.isDying) {
                val progress = if (fighter.isDying) (fighter.animFrame / 6f).coerceIn(0f, 1f) else 1f
                
                if (fighter.isMounted) {
                    rotationAngle = if (fighter.facingRight) 90f * progress else -90f * progress
                    offsetY = 30f * progress
                    offsetX = if (fighter.facingRight) -30f * progress else 30f * progress
                } else when (fighter.deathType) {
                    0 -> { // Fall backwards
                        rotationAngle = if (fighter.facingRight) -90f * progress else 90f * progress
                        offsetY = 65f * progress
                    }
                    1 -> { // Faceplant forward
                        rotationAngle = if (fighter.facingRight) 90f * progress else -90f * progress
                        offsetY = 65f * progress
                    }
                    2 -> { // Cartwheel of death
                        rotationAngle = if (fighter.facingRight) 630f * progress else -630f * progress
                        offsetY = 65f * progress
                    }
                    3 -> { // Squashed pancake falling over
                        rotationAngle = if (fighter.facingRight) -85f * progress else 85f * progress
                        scaleY = (if (fighter.isCrumpled) 0.6f else 1f) - 0.7f * progress
                        offsetY = 65f * progress
                    }
                    4 -> { // Knocked flying backwards landing flat
                        val flyDir = if (fighter.facingRight) -1f else 1f
                        rotationAngle = flyDir * 270f * progress
                        offsetX = flyDir * 150f * progress
                        offsetY = 65f * progress - 100f * sin(progress * Math.PI).toFloat()
                    }
                    else -> { // Fall flat
                        rotationAngle = if (fighter.facingRight) -90f * progress else 90f * progress
                        offsetY = 65f * progress
                    }
                }
            }

            if (fighter.isMounted) {
                var horseRot = 0f
                if (fighter.isDead || fighter.isDying) {
                    val progress = if (fighter.isDying) (fighter.animFrame / 6f).coerceIn(0f, 1f) else 1f
                    horseRot = if (fighter.facingRight) -15f * progress else 15f * progress
                }
                withTransform({ rotate(horseRot, pivot = Offset(cx, cy + 80f)) }) {
                    drawHorse(this, cx, cy, fighter)
                }
            }

            withTransform({
                translate(offsetX, offsetY)
                rotate(rotationAngle, pivot = Offset(cx, cy + 80f))
                scale(1f, scaleY, pivot = Offset(cx, cy + 80f))
            }) {
                if (!fighter.isMounted) {
                    drawLegs(this, cx, cy, fighter)
                }

                val mountOffsetY = if (fighter.isMounted) -68f else 0f
                withTransform({ translate(0f, mountOffsetY) }) {
                    drawTorso(this, cx, cy, fighter)
                    drawHead(this, cx, cy, fighter)
                    drawBackArmAndShield(this, cx, cy, fighter)
                    drawFrontArmAndWeapon(this, cx, cy, fighter)
                }
            }"""
content = content.replace(old_char_block, new_char_block)

# 2.6 handle chain
old_chain = """            if (fighter.weaponHandle.id == "handle_chain") {
                // Draw a flailing chain
                val timeSecs = System.currentTimeMillis() / 1000f
                val flailAmount = kotlin.math.sin(timeSecs * 10f + fighter.posX) * 15f
                val flailAmountY = kotlin.math.cos(timeSecs * 12f + fighter.posX) * 10f
                
                var cx = hx - 10f
                var cy = hy + 5f
                val linkCount = 8
                val dx = (shaftEnd.x - cx) / linkCount
                val dy = (shaftEnd.y - cy) / linkCount
                for (i in 0..linkCount) {
                    val progress = i.toFloat() / linkCount
                    val pX = cx + dx * i + (flailAmount * progress)
                    val pY = cy + dy * i + (flailAmountY * progress) + (progress * 15f) // sag
                    scope.drawCircle(
                        color = fighter.weaponHandle.color,
                        radius = 3f,
                        center = androidx.compose.ui.geometry.Offset(pX, pY)
                    )
                    scope.drawCircle(
                        color = ThreadColor,
                        radius = 3f,
                        center = androidx.compose.ui.geometry.Offset(pX, pY),
                        style = androidx.compose.ui.graphics.drawscope.Stroke(width = 1f)
                    )
                    if (i == linkCount) {
                        headPos = androidx.compose.ui.geometry.Offset(pX, pY)
                    }
                }
            } else if (fighter.weaponHandle.id == "handle_wheel") {"""

new_chain = """        val isChainHandle = fighter.weaponHandle.id in listOf("handle_chain", "handle_flail_chain")
        if (isChainHandle && !isBowOrSlingshot) {
            val swing = fighter.swingProgress
            val gripEnd = Offset(hx - 10f, hy + 5f)
            val sagAmount = when {
                fighter.isAttacking && swing < 0.5f -> -30f * swing
                fighter.isAttacking                 -> 15f * (swing - 0.5f) / 0.5f
                else                                -> 20f
            }
            val numLinks = 6
            for (i in 0 until numLinks) {
                val t0 = i.toFloat() / numLinks
                val t1 = (i + 1).toFloat() / numLinks
                val x0 = gripEnd.x + (shaftEnd.x - gripEnd.x) * t0
                val y0 = gripEnd.y + (shaftEnd.y - gripEnd.y) * t0 +
                         sagAmount * sin(t0 * Math.PI.toFloat()) * (1f - t0)
                val x1 = gripEnd.x + (shaftEnd.x - gripEnd.x) * t1
                val y1 = gripEnd.y + (shaftEnd.y - gripEnd.y) * t1 +
                         sagAmount * sin(t1 * Math.PI.toFloat()) * (1f - t1)
                scope.drawLine(Color(0xFF636A6E), Offset(x0, y0), Offset(x1, y1),
                    strokeWidth = 4f, cap = StrokeCap.Round)
                scope.drawLine(ThreadColor, Offset(x0, y0), Offset(x1, y1),
                    strokeWidth = 1.5f, cap = StrokeCap.Round)
            }
            headPos = shaftEnd
        } else if (!isBowOrSlingshot) {
            if (fighter.weaponHandle.id == "handle_wheel") {"""
content = content.replace(old_chain, new_chain)

# Handle len fix
old_len = """            when (fighter.weaponHandle.id) {
                "handle_long" -> 110f
                "handle_medium" -> 55f
                "handle_chain" -> 60f
                "handle_double_ended" -> 80f
                else -> 30f // short, iron, wheel, pick, fists
            }"""
new_len = """            when (fighter.weaponHandle.id) {
                "handle_long" -> 110f
                "handle_medium" -> 55f
                "handle_chain", "handle_flail_chain" -> 60f
                "handle_double_ended" -> 80f
                else -> 30f // short, iron, wheel, pick, fists
            }"""
content = content.replace(old_len, new_len)

# 2.6 head flail
old_flail = """                    "head_flail" -> {
                        val ballPos = androidx.compose.ui.geometry.Offset(headPos.x + 20f, headPos.y + 25f)
                        scope.drawLine(ThreadColor, headPos, ballPos, strokeWidth = 3f)
                        scope.drawCircle(fighter.weaponHead.color, radius = 10f, center = ballPos)
                    }"""
new_flail = """                    "head_flail" -> {
                        val swing = fighter.swingProgress
                        val chainAngle: Float = when {
                            fighter.isAttacking && swing < 0.4f ->
                                (Math.PI * 0.3 + swing * Math.PI * 0.5).toFloat()
                            fighter.isAttacking ->
                                (Math.PI * 0.8 - (swing - 0.4f) / 0.6f * Math.PI).toFloat()
                            else ->
                                (Math.PI * 0.25 + sin(fighter.animFrame * 0.8f) * 0.15f).toFloat()
                        }
                        val chainLength = 38f
                        val ballPos = Offset(
                            headPos.x + cos(chainAngle) * chainLength,
                            headPos.y + sin(chainAngle) * chainLength
                        )
                        scope.drawLine(Color(0xFF636A6E), headPos, ballPos, strokeWidth = 4f, cap = StrokeCap.Round)
                        scope.drawLine(ThreadColor, headPos, ballPos, strokeWidth = 1.5f, cap = StrokeCap.Round)
                        scope.drawCircle(fighter.weaponHead.color, radius = 11f, center = ballPos)
                        scope.drawCircle(ThreadColor, radius = 11f, center = ballPos, style = StitchedStroke)
                        for (i in 0 until 6) {
                            val spikeAngle = chainAngle + i * (Math.PI * 2.0 / 6.0).toFloat()
                            val sp = Offset(
                                ballPos.x + cos(spikeAngle) * 17f,
                                ballPos.y + sin(spikeAngle) * 17f
                            )
                            scope.drawLine(ThreadColor, ballPos, sp, strokeWidth = 2.5f, cap = StrokeCap.Round)
                        }
                    }
                    "head_war_flail" -> {
                        val swing = fighter.swingProgress
                        val baseChainAngle: Float = when {
                            fighter.isAttacking && swing < 0.4f ->
                                (Math.PI * 0.3 + swing * Math.PI * 0.5).toFloat()
                            fighter.isAttacking ->
                                (Math.PI * 0.8 - (swing - 0.4f) / 0.6f * Math.PI).toFloat()
                            else ->
                                (Math.PI * 0.25 + sin(fighter.animFrame * 0.8f) * 0.15f).toFloat()
                        }
                        listOf(baseChainAngle, baseChainAngle - (Math.PI / 4).toFloat()).forEach { chainAngle ->
                            val chainLength = 38f
                            val ballPos = Offset(
                                headPos.x + cos(chainAngle) * chainLength,
                                headPos.y + sin(chainAngle) * chainLength
                            )
                            scope.drawLine(Color(0xFF636A6E), headPos, ballPos, strokeWidth = 4f, cap = StrokeCap.Round)
                            scope.drawLine(ThreadColor, headPos, ballPos, strokeWidth = 1.5f, cap = StrokeCap.Round)
                            scope.drawCircle(fighter.weaponHead.color, radius = 11f, center = ballPos)
                            scope.drawCircle(ThreadColor, radius = 11f, center = ballPos, style = StitchedStroke)
                            for (i in 0 until 6) {
                                val spikeAngle = chainAngle + i * (Math.PI * 2.0 / 6.0).toFloat()
                                val sp = Offset(
                                    ballPos.x + cos(spikeAngle) * 17f,
                                    ballPos.y + sin(spikeAngle) * 17f
                                )
                                scope.drawLine(ThreadColor, ballPos, sp, strokeWidth = 2.5f, cap = StrokeCap.Round)
                            }
                        }
                    }"""
content = content.replace(old_flail, new_flail)

# 2.7 Front Arm
old_front_arm = """        var thrustOffset = Offset.Zero
        val armAngle = if (fighter.isDead || fighter.isDying) {
            if (fighter.isDying) {
                // Wild, hilarious ragdoll flailing!
                sin(fighter.animFrame * 1.5f) * 85f
            } else {
                // Limply sprawling on the floor
                45f
            }
        } else if (fighter.isAttacking) {
            if (isThrusting) {
                // Thrust animation (mostly forward motion, slight angle)
                if (swing < 0.3f) {
                    // Pull back
                    thrustOffset = Offset(-25f * (swing / 0.3f), 0f)
                    -10f * (swing / 0.3f)
                } else {
                    // Thrust forward
                    val thrustExt = sin((swing - 0.3f) / 0.7f * Math.PI).toFloat()
                    thrustOffset = Offset(55f * thrustExt, -5f * thrustExt)
                    -10f + 15f * thrustExt
                }
            } else if (isScythe) {
                // Wide horizontal/lower sweep
                if (swing < 0.3f) {
                    20f * (swing / 0.3f) // pull back lower
                } else {
                    20f - 100f * ((swing - 0.3f) / 0.7f) // sweep up
                }
            } else if (isHeavy) {
                // Massive overhead smash
                if (swing < 0.5f) {
                    -75f * (swing / 0.5f) // pull back very high
                } else {
                    -75f + 160f * ((swing - 0.5f) / 0.5f) // slam down
                }
            } else if (isBowOrSlingshot) {
                // Raise arm and hold, then snap forward
                if (swing < 0.4f) {
                    -35f * (swing / 0.4f) // raise arm
                } else if (swing < 0.85f) {
                    -35f // hold tension
                } else {
                    -35f + 45f * ((swing - 0.85f) / 0.15f) // release
                }
            } else {
                // Standard Smash down arc
                if (swing < 0.4f) {
                    -45f * (swing / 0.4f) // back
                } else {
                    -45f + 130f * ((swing - 0.4f) / 0.6f) // smash forward
                }
            }
        } else {
            // Idle breathing swing
            sin(fighter.animFrame * 0.5f) * 10f
        }"""
new_front_arm = """        var thrustOffset = Offset.Zero
        val isLanceCompatible = fighter.weaponHead.id in listOf("head_pike", "head_spear", "head_halberd")
        val armAngle = if (fighter.isDead || fighter.isDying) {
            if (fighter.isDying) {
                sin(fighter.animFrame * 1.5f) * 85f
            } else {
                45f
            }
        } else if (fighter.isAttacking) {
            if (isThrusting) {
                if (swing < 0.3f) {
                    thrustOffset = Offset(-25f * (swing / 0.3f), 0f)
                    -10f * (swing / 0.3f)
                } else {
                    val thrustExt = sin((swing - 0.3f) / 0.7f * Math.PI).toFloat()
                    thrustOffset = Offset(55f * thrustExt, -5f * thrustExt)
                    -10f + 15f * thrustExt
                }
            } else if (isScythe) {
                if (swing < 0.3f) {
                    20f * (swing / 0.3f)
                } else {
                    20f - 100f * ((swing - 0.3f) / 0.7f)
                }
            } else if (isHeavy) {
                if (swing < 0.5f) {
                    -75f * (swing / 0.5f)
                } else {
                    -75f + 160f * ((swing - 0.5f) / 0.5f)
                }
            } else if (isBowOrSlingshot) {
                if (swing < 0.4f) {
                    -35f * (swing / 0.4f)
                } else if (swing < 0.85f) {
                    -35f
                } else {
                    -35f + 45f * ((swing - 0.85f) / 0.15f)
                }
            } else {
                if (swing < 0.4f) {
                    -45f * (swing / 0.4f)
                } else {
                    -45f + 130f * ((swing - 0.4f) / 0.6f)
                }
            }
        } else if (fighter.isMounted && isLanceCompatible && kotlin.math.abs(fighter.velocityX) > 30f) {
            -25f
        } else {
            sin(fighter.animFrame * 0.5f) * 10f
        }

        if (fighter.missingArm) {
            scope.withTransform({
                rotate(armAngle, pivot = Offset(cx - 15f, cy + 25f))
            }) {
                val sleeveColor = if (fighter.isPlayer) Color(0xFF265063) else Color(0xFF9E3624)
                drawStitchedStrap(this, Offset(cx - 15f, cy + 25f), Offset(cx + 4f, cy + 28f), sleeveColor)
                scope.drawCircle(Color(0xFF9E3624), radius = 8f, center = Offset(cx + 4f, cy + 28f))
                scope.drawCircle(Color(0xFFBF2A2A), radius = 4f, center = Offset(cx + 4f, cy + 28f))
            }
            return
        }"""
content = content.replace(old_front_arm, new_front_arm)

with open(file_path, "w", encoding="utf-8") as f:
    f.write(content)
print("Updated TapestryRenderer.kt part 1")
