import re

with open('app/src/main/java/com/example/game/TapestryRenderer.kt', 'r', encoding='utf-8') as f:
    code = f.read()

# Add weapon attachment drawing logic at the end of drawWeapon
old_end = '''                    }
                }
            }
        }
    }

    private fun drawHorse('''

new_end = '''                    }
                }
            }
            
            // Draw Attachments
            for ((aIdx, attachmentId) in fighter.weaponAttachments.withIndex()) {
                scope.withTransform({
                    // Attachments jut out near the base of the head, somewhat rotated
                    val rot = 45f + (aIdx * 90f)
                    rotate(rot, pivot = hPos)
                }) {
                    when {
                        attachmentId.contains("spike") -> {
                            val path = androidx.compose.ui.graphics.Path().apply {
                                moveTo(hPos.x, hPos.y - 10f)
                                lineTo(hPos.x + 12f, hPos.y - 12f)
                                lineTo(hPos.x + 18f, hPos.y - 30f) // Spike tip
                                lineTo(hPos.x + 22f, hPos.y - 12f)
                                lineTo(hPos.x + 35f, hPos.y - 10f)
                                close()
                            }
                            scope.drawPath(path, Color(0xFF8C969E))
                            scope.drawPath(path, ThreadColor, style = androidx.compose.ui.graphics.drawscope.Stroke(1.5f))
                        }
                        attachmentId.contains("blade") || attachmentId.contains("axe") -> {
                            val path = androidx.compose.ui.graphics.Path().apply {
                                moveTo(hPos.x, hPos.y)
                                lineTo(hPos.x + 20f, hPos.y - 25f)
                                lineTo(hPos.x + 25f, hPos.y)
                                close()
                            }
                            scope.drawPath(path, Color(0xFF8C969E))
                            scope.drawPath(path, ThreadColor, style = androidx.compose.ui.graphics.drawscope.Stroke(1.5f))
                        }
                        attachmentId.contains("fire") -> {
                            // Draw flame effect
                            scope.drawCircle(Color.Red.copy(alpha=0.6f), radius = 15f, center = Offset(hPos.x + 10f, hPos.y - 15f))
                            scope.drawCircle(Color.Yellow.copy(alpha=0.8f), radius = 8f, center = Offset(hPos.x + 10f, hPos.y - 15f))
                        }
                    }
                }
            }
        }
    }

    private fun drawHorse('''

code = code.replace(old_end, new_end)

with open('app/src/main/java/com/example/game/TapestryRenderer.kt', 'w', encoding='utf-8') as f:
    f.write(code)

print("TapestryRenderer patched for attachments!")
