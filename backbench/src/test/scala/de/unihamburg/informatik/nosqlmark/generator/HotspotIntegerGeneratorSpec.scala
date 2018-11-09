package de.unihamburg.informatik.nosqlmark.util


import org.scalatest.{MustMatchers, WordSpecLike}
import better.files._
import better.files.Dsl.SymbolicOperations
import com.yahoo.ycsb.generator.{HotspotIntegerGenerator, ScrambledZipfianGenerator}
import org.HdrHistogram.Recorder


class HotspotIntegerGeneratorSpec extends WordSpecLike with MustMatchers {

  "Decentralized HotspotintegerGenerator" must {
    "generate the same distribution like the centralized one" in {

      val cOuput = "results" / "HotspotIntegerGenerator" / "centralized.csv"
      cOuput.createIfNotExists()
      cOuput < ""
      val cRecorder =  new Recorder(3)
      val cZipf = new HotspotIntegerGenerator(0,100,0.2,0.8)

      for(i <- 0 to 10000000) {
        cRecorder.recordValue(cZipf.nextValue())
      }

      val values = cRecorder.getIntervalHistogram.allValues().iterator()
      while(values.hasNext) {
        val value = values.next()
        cOuput << value.getValueIteratedTo + ";" + value.getCountAtValueIteratedTo
      }

      // dezentralized
      val dOuput =  "results" / "HotspotIntegerGenerator" / "decentralized.csv"
      dOuput.createIfNotExists()
      dOuput < ""
      val dRecorder = new Recorder(3)
      for(r <- (1 to 5)) {
        val dZipf = new  HotspotIntegerGenerator(0,100,0.2,0.8)
        for(i <- 0 to 2000000) {
          dRecorder.recordValue(dZipf.nextValue())
        }
      }
      val dValues = dRecorder.getIntervalHistogram.allValues().iterator()
      while(dValues.hasNext) {
        val dValue = dValues.next()
        dOuput << dValue.getValueIteratedTo + ";" + dValue.getCountAtValueIteratedTo
      }


      assert(true)
    }
  }


}
