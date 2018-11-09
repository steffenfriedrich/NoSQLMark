package de.unihamburg.informatik.nosqlmark.workloads

import java.util.{Vector, HashMap, Set}

import com.yahoo.ycsb.ByteIterator
import com.yahoo.ycsb.Status

/**
 * Created by Steffen Friedrich on 12.10.2015.
 */
sealed trait CoreOperation {
  override def toString =  this.getClass.getSimpleName
}

object Cleanup extends CoreOperation

case class Read(table: String, key: String, fields: Set[String],
                result: HashMap[String, ByteIterator]) extends CoreOperation

case class Scan(table: String, startkey: String, recordcount: Int, fields: Set[String],
                result: Vector[HashMap[String, ByteIterator]]) extends CoreOperation

case class Update(table: String, key: String, values: HashMap[String, ByteIterator]) extends CoreOperation

case class Insert(table: String, key: String, values: HashMap[String, ByteIterator]) extends CoreOperation

case class Delete(table: String, key: String) extends CoreOperation

case class ReadModifyWrite(table: String, key: String, fields: Set[String], result: HashMap[String, ByteIterator], values: HashMap[String, ByteIterator]) extends CoreOperation

case class MeasurementObject(startTime: Long, operation: String, result: Status, warmedUp: Boolean)