package de.unihamburg.informatik.nosqlmark.logging

import java.io.File
import java.util.concurrent.atomic.AtomicBoolean

import ch.qos.logback.core.joran.spi.NoAutoStart
import ch.qos.logback.core.rolling.SizeAndTimeBasedFNATP
import ch.qos.logback.core.rolling.helper.{CompressionMode, Compressor, FileNamePattern}


/**
  * Created by Steffen Friedrich on 20.09.2016.
  */
@NoAutoStart
class MyTrigger[E] extends SizeAndTimeBasedFNATP[E] {
  private val trigger: AtomicBoolean = new AtomicBoolean

  override def isTriggeringEvent(activeFile: File, event: E): Boolean = {
    if (trigger.compareAndSet(false, true) && activeFile.length > 0) {
      setMaxFileSize("1l")
      val time: Long = getCurrentTime
      setDateInCurrentPeriod(time)
      val fileNamePatternWCS: FileNamePattern = new FileNamePattern(Compressor.computeFileNameStr_WCS(tbrp.getFileNamePattern, CompressionMode.NONE), this.context)
      elapsedPeriodsFileName = fileNamePatternWCS.convertMultipleArguments(dateInCurrentPeriod, new Integer(0))
      super.isTriggeringEvent(activeFile, event)
      setMaxFileSize("50MB")
      return true
    }
    return super.isTriggeringEvent(activeFile, event)
  }
}
