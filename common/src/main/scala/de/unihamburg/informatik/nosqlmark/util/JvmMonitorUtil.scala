package de.unihamburg.informatik.nosqlmark.util

import collection.JavaConverters._

/**
  * Created by Steffen Friedrich on 08.07.2016.
  */
object JvmMonitorUtil {
  private val runtime = Runtime.getRuntime()

  def getJvmStats: JvmStats = {
    val maxMemory = runtime.maxMemory()  / (1024 * 1024)
    val totalMemory = runtime.totalMemory / (1024 * 1024)
    val freeMemory = runtime.freeMemory / (1024 * 1024)
    val usedMemory = totalMemory - freeMemory
    val threads = Thread.getAllStackTraces.keySet.asScala.filter(t => t.isAlive)
    val threadCount = threads.size
    val ioThreads = threads.filter(_.getName.contains("blocking-io-dispatcher"))
    val ioThreadCount = ioThreads.size
    val memoryPerThread = usedMemory / threadCount.toDouble
    JvmStats(maxMemory, totalMemory, freeMemory, usedMemory, threadCount, ioThreadCount, memoryPerThread)
  }
}

case class JvmStats(maxMemory: Double, totalMemory: Double, freeMemory: Double, usedMemory: Double, threadCount: Int, ioThreadCount: Int,  memoryPerThread: Double)