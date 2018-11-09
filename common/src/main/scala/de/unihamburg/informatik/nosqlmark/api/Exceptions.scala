package de.unihamburg.informatik.nosqlmark.api

/**
  * Created by Steffen Friedrich on 24.08.2016.
  */
object Exceptions {
  case class MeasurementException(smth:String)  extends Exception(smth)
}
