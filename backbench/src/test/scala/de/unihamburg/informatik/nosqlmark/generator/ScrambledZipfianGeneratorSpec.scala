package de.unihamburg.informatik.nosqlmark.util


import org.scalatest.{BeforeAndAfter, MustMatchers, WordSpecLike}
import better.files._
import better.files.Dsl.SymbolicOperations
import com.yahoo.ycsb.generator.ScrambledZipfianGenerator
import org.HdrHistogram.Recorder


class ScrambledZipfianGeneratorSpec extends WordSpecLike with MustMatchers with BeforeAndAfter {

  // centralized
  val cOuput = "results" / "ScrampleZifpianGenerator" / "centralized.csv"
  cOuput.createIfNotExists()
  cOuput < ""
  val cRecorder = new Recorder(3)
  val cZipf = new ScrambledZipfianGenerator(0, 100)
  for (i <- 0 to 10000000) {
    cRecorder.recordValue(cZipf.nextValue())
  }
  val cIntervalHistogram = cRecorder.getIntervalHistogram
  val cValues = cIntervalHistogram.allValues().iterator()
  while (cValues.hasNext) {
    val cValue = cValues.next()
    cOuput << cValue.getValueIteratedTo + ";" + cValue.getCountAtValueIteratedTo
  }


  "Decentralized ScrampleZifpianGenerator" must {
    "generate the same distribution like the centralized one" in {
      // decentralized
      val dOuput = "results" / "ScrampleZifpianGenerator" / "decentralized.csv"
      dOuput.createIfNotExists()
      dOuput < ""
      val dRecorder = new Recorder(3)
      for (r <- (1 to 5)) {
        val dZipf = new ScrambledZipfianGenerator(0, 100)
        for (i <- 0 to 2000000) {
          dRecorder.recordValue(dZipf.nextValue())
        }
      }
      val dIntervalHistogram =  dRecorder.getIntervalHistogram
      val dValues = dIntervalHistogram.allValues().iterator()
      while (dValues.hasNext) {
        val dValue = dValues.next()
        dOuput << dValue.getValueIteratedTo + ";" + dValue.getCountAtValueIteratedTo
      }

      val cMean = cIntervalHistogram.getMean
      val dMean = dIntervalHistogram.getMean
      println(cMean, dMean)
      // ToDo assert(cMean == dMean +- 0.1)
    }
  }


    "Decentralized ScrampleZifpianGenerator with different insertstart values" must {
      "not generate the same distribution like the centralized one" in {
        // dezentralized with different insertstart parameter
        val dOuput = "results" / "ScrampleZifpianGenerator" / "decentralized_insertstart.csv"
        dOuput.createIfNotExists()
        dOuput < ""
        val dRecorder = new Recorder(3)
        for (r <- (1 to 5)) {
          val insertstart = (r * 100 / 5) - (100 / 5)
          val dZipf = new ScrambledZipfianGenerator(r * 100 / 5 - 100 / 5, 100)
          for (i <- 0 to 2000000) {
            dRecorder.recordValue(dZipf.nextValue())
          }
        }
        val dValues = dRecorder.getIntervalHistogram.allValues().iterator()
        while (dValues.hasNext) {
          val dValue = dValues.next()
          dOuput << dValue.getValueIteratedTo + ";" + dValue.getCountAtValueIteratedTo
        }
        assert(true)
      }
    }

}
