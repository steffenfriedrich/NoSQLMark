package de.unihamburg.informatik.nosqlmark.util

import java.text.{SimpleDateFormat, DateFormat}
import java.util.{Date, Properties}

import de.unihamburg.informatik.nosqlmark.api.CoreJob$
import scala.reflect.runtime.universe._
import java.lang.{Iterable => JavaItb}
import java.util.{Iterator => JavaItr}

/**
 * Created by Steffen Friedrich on 26.04.2015.
 */
object Util {
  /**
   * Scala map conversion into Java Properties
   */
  def map2Properties(givenprops: Map[String, String]): Properties = {
    val props: Properties = new Properties()
    givenprops.foreach(p => props.setProperty(p._1, p._2))
    props
  }
}
