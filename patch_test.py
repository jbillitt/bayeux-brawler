import re

with open("app/src/test/java/com/example/game/GameViewModelTest.kt", "r") as f:
    content = f.read()

target = """    @Test
    fun `toggleDualWield changes state correctly`() {"""

replacement = """    @Test
    fun `startBattle generates enemies without crashing`() {
        // Arrange
        // (ViewModel is already initialized)

        // Act
        // This should invoke generateRandomSaxon inside startBattle.
        // If it throws NoSuchElementException (due to empty filtered gear lists), the test will fail.
        viewModel.startBattle()
        val state = viewModel.uiState.value

        // Assert
        assertTrue("Battle should be active", state.isBattleActive)
        
        val enemies = viewModel.enemiesState.value
        assertTrue("Enemies should be generated", enemies.isNotEmpty())
        
        // Verify the enemy has valid equipment
        val firstEnemy = enemies.first()
        assertTrue("Enemy should have a valid weapon head", firstEnemy.weaponHead.id.isNotEmpty())
        assertTrue("Enemy should have a valid armor", firstEnemy.armor.id.isNotEmpty())
    }

    @Test
    fun `toggleDualWield changes state correctly`() {"""

if target in content:
    content = content.replace(target, replacement)
    with open("app/src/test/java/com/example/game/GameViewModelTest.kt", "w") as f:
        f.write(content)
    print("Test patched successfully")
else:
    print("Test Target not found.")
