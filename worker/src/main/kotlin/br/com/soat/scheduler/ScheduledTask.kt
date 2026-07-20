package br.com.soat.scheduler

interface ScheduledTask {
    fun execute()
    fun getLockName(): String
    fun getIntervalInSeconds(): Long
}
