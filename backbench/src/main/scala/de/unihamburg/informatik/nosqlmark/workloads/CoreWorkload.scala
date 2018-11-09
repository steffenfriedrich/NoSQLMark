package de.unihamburg.informatik.nosqlmark.workloads

import java.util.{HashSet, Vector, HashMap => JHashMap}

import com.yahoo.ycsb._
import com.yahoo.ycsb.generator.{ConstantIntegerGenerator, CounterGenerator, DiscreteGenerator, ExponentialGenerator, HistogramGenerator, HotspotIntegerGenerator, NumberGenerator, ScrambledZipfianGenerator, SkewedLatestGenerator, UniformLongGenerator, ZipfianGenerator}
import de.unihamburg.informatik.nosqlmark.api.{CoreJob}
import org.slf4j.LoggerFactory

/**
  * Created by Steffen Friedrich on 19.10.2015.
  */
class CoreWorkload(job: CoreJob) {
  val log = LoggerFactory.getLogger("Workload")

  val fieldnames = for (i <- 0 until job.counts.fieldcount) yield "field" + i

  val fieldlengthgenerator = job.distributions.fieldlengthdistribution match {
    case "constant" => new ConstantIntegerGenerator(job.counts.fieldlength)
    case "uniform" => new UniformLongGenerator(1, job.counts.fieldlength)
    case "zipfian" => new ZipfianGenerator(1, job.counts.fieldlength)
    case "histogram" => new HistogramGenerator(job.distributions.fieldlengthhistogram)
    case _ => throw new WorkloadException("Unknown field length distribution \"" + job.distributions.fieldlengthdistribution + "\"")
  }

  val recordcount = if (job.counts.recordcount == 0) Integer.MAX_VALUE else job.counts.recordcount

  var keychooser: NumberGenerator = _
  var orderedinserts: Boolean = _

  if (job.distributions.insertorder == "hashed") orderedinserts = false
  else if (job.distributions.requestdistribution == "exponential") {
    val percentile = job.distributions.exponentialpercentile
    val frac = job.distributions.exponentialfrac
    keychooser = new ExponentialGenerator(percentile, recordcount * frac)
  } else orderedinserts = true

  val keysequence = new CounterGenerator(job.counts.insertstart)

  val operationchooser = new DiscreteGenerator()
  if (job.proportions.readproportion > 0) operationchooser.addValue(job.proportions.readproportion, Read.toString())
  if (job.proportions.updateproportion > 0) operationchooser.addValue(job.proportions.updateproportion, Update.toString())
  if (job.proportions.insertproportion > 0) operationchooser.addValue(job.proportions.insertproportion, Insert.toString())
  if (job.proportions.scanproportion > 0) operationchooser.addValue(job.proportions.scanproportion, Scan.toString())
  if (job.proportions.readmodifywriteproportion > 0) operationchooser.addValue(job.proportions.readmodifywriteproportion, ReadModifyWrite.toString())

  val transactioninsertkeysequence = new CounterGenerator(recordcount)
  // ToDo AcknowledgedCounterGenerator

  job.distributions.requestdistribution match {
    case "uniform" => keychooser = new UniformLongGenerator(0, recordcount - 1)
    case "zipfian" => {
      val expectednewkeys = (job.counts.operationcount.toDouble * job.proportions.insertproportion * 2.0).toInt
      keychooser = new ScrambledZipfianGenerator(recordcount + expectednewkeys)
    }
    case "latest" => keychooser = new SkewedLatestGenerator(transactioninsertkeysequence)
    case "hotspot" => {
      keychooser = new HotspotIntegerGenerator(0, recordcount - 1, job.distributions.hotspotdatafraction, job.distributions.hotspotopnfraction)
    }
    case _ => throw new WorkloadException("Unknown request distribution \"" + job.distributions.requestdistribution + "\"")
  }

  val fieldchooser = new UniformLongGenerator(0, job.counts.fieldcount - 1)

  val scanlength = job.distributions.scanlengthdistribution match {
    case "uniform" => new UniformLongGenerator(1, job.distributions.maxscanlength)
    case "zipfian" => new ZipfianGenerator(1, job.distributions.maxscanlength)
    case _ => throw new WorkloadException("Distribution \"" + job.distributions.scanlengthdistribution + "\" not allowed for scan length")
  }


  def buildKeyName(keynum: Long): String = if (!orderedinserts) "user" + com.yahoo.ycsb.Utils.hash(keynum)
  else "user" + keynum

  /**
   * Builds a value for a randomly chosen field.
   */
  def buildSingleValue(key: String): JHashMap[String, ByteIterator] = {
    val value = new JHashMap[String, ByteIterator]()
    val fieldkey = fieldnames(fieldchooser.nextString().toInt)
    val data: ByteIterator = new RandomByteIterator(fieldlengthgenerator.nextValue.longValue())
    value.put(fieldkey, data)
    value
  }

  /**
   * Builds values for all fields.
   */
  def buildValues(key: String): JHashMap[String, ByteIterator] = {
    val values = new JHashMap[String, ByteIterator]()
    for (fieldkey <- fieldnames) {
      val data = new RandomByteIterator(fieldlengthgenerator.nextValue().longValue())
      values.put(fieldkey, data)
    }
    values
  }

  /**
   * Build a deterministic value given the key information.
   */
  def buildDeterministicValue(key: String, fieldkey: String): String = {
    val size = fieldlengthgenerator.nextValue().intValue()
    val sb = new StringBuilder(size)
    sb.append(key)
    sb.append(':')
    sb.append(fieldkey)
    while (sb.length < size) {
      sb.append(':')
      sb.append(sb.toString.hashCode)
    }
    sb.setLength(size)
    sb.toString
  }

  def nextKeynum: Long = {
    var keynum: Long = 0
    if (keychooser.isInstanceOf[ExponentialGenerator]) {
      do {
        keynum = transactioninsertkeysequence.lastValue() - keychooser.nextValue().intValue()
      } while (keynum < 0)
    } else {
      do {
        keynum = keychooser.nextValue().intValue()
      } while (keynum > transactioninsertkeysequence.lastValue())
    }
    keynum
  }

  def nextInsertOperation: CoreOperation = {
    val keynum = keysequence.nextValue().intValue()
    val dbkey = buildKeyName(keynum)
    val values = buildValues(dbkey)
    Insert(job.table, dbkey, values)
  }

  def nextDeleteOperation: CoreOperation = {
    val keynum = keysequence.nextValue().intValue()
    val dbkey = buildKeyName(keynum)
    Delete(job.table, dbkey)
  }

  def nextOperation: CoreOperation = {
    operationchooser.nextString match {
      case "Read" => {
        val keynum = nextKeynum
        val key = buildKeyName(keynum)
        var fields: HashSet[String] = null
        if (!job.counts.readallfields) {
          val fieldname = fieldnames(fieldchooser.nextString.toInt)
          fields = new HashSet[String]()
          fields.add(fieldname)
        }
        val cells = new JHashMap[String, ByteIterator]()
        Read(job.table, key, fields, cells)
      }
      case "Update" => {
        val keynum = nextKeynum
        val key = buildKeyName(keynum)
        var values: JHashMap[String, ByteIterator] = null
        values = if (job.counts.writeallfields) buildValues(key) else buildSingleValue(key)
        Update(job.table, key, values)
      }
      case "Insert" => {
        val keynum = transactioninsertkeysequence.nextValue().intValue()
        val key = buildKeyName(keynum)
        val values = buildValues(key)
        Insert(job.table, key, values)
      }
      case "Scan" => {
        val keynum = nextKeynum
        val key = buildKeyName(keynum)
        val len = scanlength.nextValue().intValue()
        var fields: HashSet[String] = null
        if (!job.counts.readallfields) {
          val fieldname = fieldnames(fieldchooser.nextString().toInt)
          fields = new HashSet[String]()
          fields.add(fieldname)
        }
        Scan(job.table, key, len, fields, new Vector[JHashMap[String, ByteIterator]]())
      }
      case _ => {
        val keynum = nextKeynum
        val key = buildKeyName(keynum)
        var fields: HashSet[String] = null
        if (!job.counts.readallfields) {
          val fieldname = fieldnames(fieldchooser.nextString.toInt)
          fields = new HashSet[String]()
          fields.add(fieldname)
        }
        var values: JHashMap[String, ByteIterator] = null
        values = if (job.counts.writeallfields) buildValues(key) else buildSingleValue(key)
        val result = new JHashMap[String, ByteIterator]()
        ReadModifyWrite(job.table, key, fields, result, values)
      }
    }
  }
}