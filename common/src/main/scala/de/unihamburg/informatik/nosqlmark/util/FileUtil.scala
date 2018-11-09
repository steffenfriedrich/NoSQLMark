package de.unihamburg.informatik.nosqlmark.util

import better.files._

/**
 * Created by Steffen Friedrich on 17.11.2015.
 */
object FileUtil {
  def splitBigLogFile(logfile: File, maxFileSize: Int) = {
    var counter = 1
    var outputfile = "logs" / ("timeseries." + counter + ".log") createIfNotExists(false)
    logfile.lines.foreach(line => {
      if(outputfile.size > 1000000 * maxFileSize) {
        counter += 1
        outputfile = "logs" / ("timeseries." + counter + ".log") createIfNotExists(false)
      }
      outputfile.appendLine(line)
    })
  }
}
