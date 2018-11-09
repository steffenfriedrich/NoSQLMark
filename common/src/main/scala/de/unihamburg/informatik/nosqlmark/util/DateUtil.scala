package de.unihamburg.informatik.nosqlmark.util

import java.text.{SimpleDateFormat, DateFormat}
import java.util.Date
import java.util.concurrent.TimeUnit

/**
 * Created by Steffen Friedrich on 10.11.2015.
 */
object DateUtil {
  def formatTimeStamp(timestamp: String): String = {
    try {
      formatTimeStamp(timestamp.toLong)
    } catch {
      case e: Exception =>timestamp
    }
  }

  def formatTimeStamp(timestamp: Long): String = {
    val date: Date = new Date(timestamp)
    val formatter: DateFormat = new SimpleDateFormat("YMd_HHmmss_SSS")
    val dateFormatted: String = formatter.format(date)
    return dateFormatted;
  }

  def formatSeconds(seconds: Long): String = {
    var time: String = ""
    var tempSeconds = seconds
    val days = TimeUnit.SECONDS.toDays(seconds)
    if(days > 0) {
      time = time + days + " days "
      tempSeconds -= TimeUnit.DAYS.toSeconds(days);
    }
    val hours = TimeUnit.SECONDS.toHours(tempSeconds);
    if(hours > 0) {
      time = time + hours + " hours "
      tempSeconds -= TimeUnit.HOURS.toSeconds(hours)
    }
    if(days < 1) {
      val minutes = TimeUnit.SECONDS.toMinutes(tempSeconds)
      if(minutes > 0) {
        time = time  + minutes + " minutes "
        tempSeconds -= TimeUnit.MINUTES.toSeconds(minutes)
      }
    }
    if(time.length < 1) {
      time = time + tempSeconds + " seconds "
    }
    time
  }
}
