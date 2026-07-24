package de.unbow.mora.platform

import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.annotation.RequiresApi
import de.unbow.mora.data.LauncherIcon

internal enum class LauncherIconComponentState {
    ENABLED,
    DISABLED,
}

internal data class ComponentChange(
    val launcherIcon: LauncherIcon,
    val state: LauncherIconComponentState,
)

internal fun launcherIconAliasClassName(launcherIcon: LauncherIcon): String = when (launcherIcon) {
    LauncherIcon.INDIGO -> ".launcher.Indigo"
    LauncherIcon.PINE -> ".launcher.Pine"
    LauncherIcon.NIGHT -> ".launcher.Night"
}

internal fun planLauncherIconChanges(target: LauncherIcon): List<ComponentChange> =
    buildList {
        add(ComponentChange(target, LauncherIconComponentState.ENABLED))
        LauncherIcon.entries
            .filterNot { it == target }
            .forEach { add(ComponentChange(it, LauncherIconComponentState.DISABLED)) }
    }

class LauncherIconManager(context: Context) {
    private val packageManager = context.packageManager
    private val packageName = context.packageName

    fun reconcile(storedIcon: LauncherIcon): Boolean {
        val alreadyReconciled = runCatching {
            hasExactlyOneEnabledAlias(storedIcon)
        }.getOrDefault(false)
        return alreadyReconciled || changeLauncherIcon(storedIcon)
    }

    fun changeLauncherIcon(target: LauncherIcon): Boolean {
        val changed = runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                applyAtomic(target)
            } else {
                applyTargetFirst(target)
            }
            hasExactlyOneEnabledAlias(target)
        }.getOrDefault(false)

        if (!changed) {
            // A failed legacy sequence may leave more than one entry enabled, but it must never
            // strand the app with no launcher entry. The stored choice remains unchanged.
            ensureEnabled(target)
        }
        return changed
    }

    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    private fun applyAtomic(target: LauncherIcon) {
        packageManager.setComponentEnabledSettings(
            planLauncherIconChanges(target).map { change ->
                PackageManager.ComponentEnabledSetting(
                    componentName(change.launcherIcon),
                    change.state.toPackageManagerState(),
                    PackageManager.DONT_KILL_APP,
                )
            },
        )
    }

    private fun applyTargetFirst(target: LauncherIcon) {
        val changes = planLauncherIconChanges(target)
        val targetChange = changes.first()
        setComponentState(targetChange)
        check(isEnabled(target)) {
            "Launcher target was not enabled before disabling other aliases."
        }
        changes.drop(1).forEach(::setComponentState)
    }

    private fun setComponentState(change: ComponentChange) {
        packageManager.setComponentEnabledSetting(
            componentName(change.launcherIcon),
            change.state.toPackageManagerState(),
            PackageManager.DONT_KILL_APP,
        )
    }

    private fun ensureEnabled(target: LauncherIcon) {
        listOf(target, LauncherIcon.INDIGO, LauncherIcon.PINE, LauncherIcon.NIGHT)
            .distinct()
            .firstOrNull { recoveryIcon ->
                runCatching {
                    packageManager.setComponentEnabledSetting(
                        componentName(recoveryIcon),
                        PackageManager.COMPONENT_ENABLED_STATE_ENABLED,
                        PackageManager.DONT_KILL_APP,
                    )
                    isEnabled(recoveryIcon)
                }.getOrDefault(false)
            }
    }

    private fun hasExactlyOneEnabledAlias(target: LauncherIcon): Boolean =
        LauncherIcon.entries.filter(::isEnabled) == listOf(target)

    private fun isEnabled(launcherIcon: LauncherIcon): Boolean = when (
        packageManager.getComponentEnabledSetting(componentName(launcherIcon))
    ) {
        PackageManager.COMPONENT_ENABLED_STATE_ENABLED -> true
        PackageManager.COMPONENT_ENABLED_STATE_DEFAULT -> launcherIcon == LauncherIcon.INDIGO
        else -> false
    }

    private fun componentName(launcherIcon: LauncherIcon): ComponentName = ComponentName(
        packageName,
        packageName + launcherIconAliasClassName(launcherIcon),
    )
}

private fun LauncherIconComponentState.toPackageManagerState(): Int = when (this) {
    LauncherIconComponentState.ENABLED -> PackageManager.COMPONENT_ENABLED_STATE_ENABLED
    LauncherIconComponentState.DISABLED -> PackageManager.COMPONENT_ENABLED_STATE_DISABLED
}
