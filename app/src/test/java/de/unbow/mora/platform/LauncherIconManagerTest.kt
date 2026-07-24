package de.unbow.mora.platform

import de.unbow.mora.data.LauncherIcon
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LauncherIconManagerTest {

    @Test
    fun `launcher icon aliases keep their published class names`() {
        assertEquals(".launcher.Indigo", launcherIconAliasClassName(LauncherIcon.INDIGO))
        assertEquals(".launcher.Pine", launcherIconAliasClassName(LauncherIcon.PINE))
        assertEquals(".launcher.Night", launcherIconAliasClassName(LauncherIcon.NIGHT))
    }

    @Test
    fun `selected icon is enabled before the other aliases are disabled`() {
        assertEquals(
            listOf(
                ComponentChange(
                    launcherIcon = LauncherIcon.PINE,
                    state = LauncherIconComponentState.ENABLED,
                ),
                ComponentChange(
                    launcherIcon = LauncherIcon.INDIGO,
                    state = LauncherIconComponentState.DISABLED,
                ),
                ComponentChange(
                    launcherIcon = LauncherIcon.NIGHT,
                    state = LauncherIconComponentState.DISABLED,
                ),
            ),
            planLauncherIconChanges(LauncherIcon.PINE),
        )
    }

    @Test
    fun `every icon plan contains three aliases with exactly one enabled target`() {
        LauncherIcon.entries.forEach { target ->
            val changes = planLauncherIconChanges(target)

            assertEquals(3, changes.size)
            assertEquals(target, changes.first().launcherIcon)
            assertEquals(LauncherIconComponentState.ENABLED, changes.first().state)
            assertEquals(LauncherIcon.entries.toSet(), changes.map { it.launcherIcon }.toSet())
            assertEquals(
                1,
                changes.count { it.state == LauncherIconComponentState.ENABLED },
            )
            assertTrue(
                changes
                    .filterNot { it.launcherIcon == target }
                    .all { it.state == LauncherIconComponentState.DISABLED },
            )
        }
    }

    @Test
    fun `reconciliation repairs zero multiple and wrong enabled aliases`() {
        val target = LauncherIcon.PINE
        val expectedRepair = planLauncherIconChanges(target)

        assertEquals(
            expectedRepair,
            planLauncherIconReconciliation(target, emptySet()),
        )
        assertEquals(
            expectedRepair,
            planLauncherIconReconciliation(
                target,
                setOf(LauncherIcon.INDIGO, LauncherIcon.PINE),
            ),
        )
        assertEquals(
            expectedRepair,
            planLauncherIconReconciliation(target, setOf(LauncherIcon.NIGHT)),
        )
        assertTrue(
            planLauncherIconReconciliation(target, setOf(target)).isEmpty(),
        )
    }
}
