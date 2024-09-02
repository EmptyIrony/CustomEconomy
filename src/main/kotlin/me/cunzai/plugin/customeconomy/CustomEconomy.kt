package me.cunzai.plugin.customeconomy

import taboolib.common.env.DependencyScope
import taboolib.common.env.RuntimeDependencies
import taboolib.common.env.RuntimeDependency
import taboolib.common.platform.Plugin
import taboolib.common.platform.function.info

@RuntimeDependencies(
    RuntimeDependency(
        value = "com.cronutils:cron-utils:9.2.1",
        scopes = [DependencyScope.COMPILE]
    )
)
object CustomEconomy : Plugin()