package de.unihamburg.informatik.nosqlmark.generator

import better.files.Dsl.SymbolicOperations
import better.files._
import com.yahoo.ycsb.generator._
import de.unihamburg.informatik.nosqlmark.workloads._
import org.HdrHistogram.Recorder
import org.scalatest.{BeforeAndAfter, MustMatchers, WordSpecLike}


class ExponentialGeneratorSpec extends WordSpecLike with MustMatchers with BeforeAndAfter {


  "Decentralized ExponentialGenerator" must {
    "generate the same distribution like the centralized one" in {

      simulateBenchmark("zipfian", "centralized_100keys", 100, 10000000, 0.5, 0.5, 0.0, 1, false)
      simulateBenchmark("zipfian", "decentralized_100keys", 100, 10000000, 0.5, 0.5, 0.0, 5, false)
      simulateBenchmark("zipfian", "decentralized_100keys",  100, 10000000, 0.5, 0.5, 0.0, 5, true)
      //simulateBenchmark("exponential", "centralized_100keys", 0, 100, 10000000, 0.5, 0.5, 0.0, 1)
      //simulateBenchmark("exponential", "decentralized_100keys", 0, 100, 10000000, 0.5, 0.5, 0.0, 5)
      //("exponential", "centralized", 0, 10000, 10000000, 0.5, 0.5, 0.0, 1)
      //simulateBenchmark("exponential", "decentralized", 0, 10000, 10000000, 0.5, 0.5, 0.0, 5)
      //simulateBenchmark("exponential", "centralized_inserts", 0, 10000, 10000000, 0.4999, 0.5, 0.0001, 1)
      //simulateBenchmark("exponential", "decentralized_inserts", 0, 10000, 10000000, 0.4999, 0.5, 0.0001, 5)
    }
  }


  def simulateBenchmark(requestdistrib: String, name: String, recordcount: Int, ops: Int,
                                  readproportion: Double, updateProportion: Double, inserproportion: Double, nodes: Int = 1, diffInsertstart: Boolean): Unit = {
    val output = "results" / requestdistrib / (name + ".csv")
    output.parent.createDirectories()
    output.createIfNotExists()
    output < ""
    val recorder = new Recorder(3)


    for (r <- (1 to nodes)) {

      var insertstart  = if(diffInsertstart) {
        0
      } else 0

      var ops_per_node = math.floor(ops / nodes).toInt
      if (r < ops % nodes) ops_per_node = ops_per_node + 1
      val keychooser = if (requestdistrib == "exponential") {
        new ExponentialGenerator(95.0, recordcount * 0.8571428571)
      }
      else {
        new ScrambledZipfianGenerator(insertstart, recordcount)
      }

      val txinsertkeyseq = new AcknowledgedCounterGenerator(recordcount)
      val opchooser = operationchooser(readproportion, updateProportion, inserproportion)
      for (i <- 0 to ops_per_node) {
        if (opchooser.nextValue() == Insert.toString()) {
          txinsertkeyseq.acknowledge(txinsertkeyseq.nextValue())
        }
        var keynum = 0L
        if (keychooser.isInstanceOf[ExponentialGenerator]) {
          do {
            keynum = txinsertkeyseq.lastValue() - keychooser.nextValue().intValue()
          } while (keynum < 0)
        } else {
          do {
            keynum = keychooser.nextValue().intValue()
          } while (keynum > txinsertkeyseq.lastValue())
        }
        recorder.recordValue(keynum)
      }
    }

    val intervalHistogram = recorder.getIntervalHistogram
    val values = intervalHistogram.allValues().iterator()
    while (values.hasNext) {
      val value = values.next()
      output << value.getValueIteratedTo + ";" + value.getCountAtValueIteratedTo
    }
  }

  def operationchooser(read: Double, update: Double, insert: Double): DiscreteGenerator = {
    val operationchooser = new DiscreteGenerator()
    operationchooser.addValue(read, Read.toString())
    operationchooser.addValue(update, Update.toString())
    operationchooser.addValue(insert, Insert.toString())
    operationchooser
  }
}
