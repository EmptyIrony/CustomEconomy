package me.cunzai.plugin.customeconomy.misc

import com.cronutils.model.Cron
import com.cronutils.model.time.ExecutionTime
import java.time.LocalDateTime
import java.time.ZoneId
import kotlin.jvm.optionals.getOrNull

fun Cron.getLastAndNextExecutionTime(): Pair<LocalDateTime, LocalDateTime> {
    val next = ExecutionTime
        .forCron(this)
        .nextExecution(LocalDateTime.now().atZone(ZoneId.systemDefault()))
        .getOrNull() ?: return Pair(LocalDateTime.now(), LocalDateTime.now())
    val last = ExecutionTime
        .forCron(this)
        .lastExecution(LocalDateTime.now().atZone(ZoneId.systemDefault()))
        .getOrNull() ?: return Pair(LocalDateTime.now(), LocalDateTime.now())

    return Pair(last.toLocalDateTime(), next.toLocalDateTime())
}