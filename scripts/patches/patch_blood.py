import re

with open("app/src/main/java/com/example/MainActivity.kt", "r") as f:
    content = f.read()

target = """                // 5. Draw Blood Particles
                viewModel.particlesState.value.forEach { part ->
                    val px = part.x * playerScaleX
                    val py = 200f + (part.y - 200f) * scaleFactor
                    drawCircle(
                        color = Color(0xFF9E3624).copy(alpha = 1f - (part.age / part.maxAge)),
                        radius = 2.5f + (Math.random() * 2f).toFloat(),
                        center = Offset(px, py)
                    )
                }"""
                
replacement = """                // 5. Draw Blood Particles
                viewModel.particlesState.value.forEach { part ->
                    val px = part.x * playerScaleX
                    val py = 200f + (part.y - 200f) * scaleFactor
                    
                    if (part.y >= 240f) {
                        val poolWidth = 5f + (part.age / part.maxAge) * 15f
                        val poolHeight = 2f + (part.age / part.maxAge) * 5f
                        drawOval(
                            color = Color(0xFF9E3624).copy(alpha = (1f - (part.age / part.maxAge) * 0.5f).coerceIn(0f, 1f)),
                            topLeft = Offset(px - poolWidth / 2, py - poolHeight / 2),
                            size = Size(poolWidth, poolHeight)
                        )
                    } else {
                        drawCircle(
                            color = Color(0xFF9E3624).copy(alpha = (1f - (part.age / part.maxAge)).coerceIn(0f, 1f)),
                            radius = 2.5f + (Math.random() * 2f).toFloat(),
                            center = Offset(px, py)
                        )
                    }
                }"""
                
content = content.replace(target, replacement)

with open("app/src/main/java/com/example/MainActivity.kt", "w") as f:
    f.write(content)

