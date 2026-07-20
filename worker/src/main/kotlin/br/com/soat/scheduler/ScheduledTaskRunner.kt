package br.com.soat.scheduler

import net.javacrumbs.shedlock.core.DefaultLockingTaskExecutor
import net.javacrumbs.shedlock.core.LockConfiguration
import net.javacrumbs.shedlock.core.LockingTaskExecutor
import net.javacrumbs.shedlock.provider.jdbc.JdbcLockProvider
import org.slf4j.LoggerFactory
import java.time.Duration
import java.time.Instant
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.TimeUnit
import javax.sql.DataSource

class ScheduledTaskRunner(
    private val tasks: List<ScheduledTask>
) {
    private val logger = LoggerFactory.getLogger(ScheduledTaskRunner::class.java)
    private lateinit var scheduler: ScheduledExecutorService
    private lateinit var lockingTaskExecutor: LockingTaskExecutor

    fun start(dataSource: DataSource) {
        logger.info("Initializing ScheduledTaskRunner with ShedLock")

        val lockProvider = JdbcLockProvider(dataSource)
        lockingTaskExecutor = DefaultLockingTaskExecutor(lockProvider)

        scheduler = Executors.newScheduledThreadPool(tasks.size)

        tasks.forEach { task ->
            scheduler.scheduleAtFixedRate(
                { executeTaskWithLock(task) },
                0,
                task.getIntervalInSeconds(),
                TimeUnit.SECONDS
            )
            logger.info("Scheduled task: ${task.getLockName()} with interval of ${task.getIntervalInSeconds()}s")
        }

        logger.info("ScheduledTaskRunner started successfully")
    }

    private fun executeTaskWithLock(task: ScheduledTask) {
        val lockConfiguration = LockConfiguration(
            Instant.now(),
            task.getLockName(),
            Duration.ofSeconds(task.getIntervalInSeconds()),
            Duration.ofSeconds(task.getIntervalInSeconds() - 1)
        )

        lockingTaskExecutor.executeWithLock(Runnable {
            try {
                task.execute()
            } catch (e: Exception) {
                logger.error("Error executing task ${task.getLockName()}", e)
            }
        }, lockConfiguration)
    }

    fun stop() {
        if (::scheduler.isInitialized) {
            logger.info("Shutting down ScheduledTaskRunner")
            scheduler.shutdown()
            try {
                if (!scheduler.awaitTermination(10, TimeUnit.SECONDS)) {
                    scheduler.shutdownNow()
                }
            } catch (_: InterruptedException) {
                scheduler.shutdownNow()
            }
        }
    }
}
