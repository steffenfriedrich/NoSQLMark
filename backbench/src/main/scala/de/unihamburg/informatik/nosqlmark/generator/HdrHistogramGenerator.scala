package de.unihamburg.informatik.nosqlmark.generator


import org.HdrHistogram.{Histogram}
import org.apache.commons.math3.random.Well19937c
import breeze.linalg._
import com.yahoo.ycsb.generator.NumberGenerator
import de.unihamburg.informatik.nosqlmark.util.MeasurementUtil
import better.files._
import scala.collection.JavaConverters._


class HdrHistogramGenerator(val histogram: Histogram) extends NumberGenerator {

  def this(hdrHistogramFile: String = "./histogram.hdr") {
    this(histogram =  MeasurementUtil.decompressHistogram(hdrHistogramFile))
  }

  val data = getData(histogram).unzip
  // values / bins and counts / hist
  val bins = DenseVector(data._1)
  val hist = DenseVector(data._2)
  // compute bin midpoints
  val bin_midpoints = bins(0 to (bins.length - 2)) + (diff(bins) / 2.0)
  // cumulative sum
  val acc_hist =  accumulate(hist)
  // normalize  cumulative sum
  val cdf = acc_hist / acc_hist(-1)

  val random = new Well19937c()

  override def mean() = histogram.getMean

  override def nextValue() = {
    val r = random.nextDouble()
    val index = cdf.iterator.indexWhere(_._2 >= r)
    bins(index).toLong
  }

  private def getData(histogram: Histogram)= {
    val values = histogram.allValues().asScala
    val pairs = (for (value <- values) yield {
      (value.getDoubleValueIteratedTo, value.getCountAtValueIteratedTo.toDouble)
    })
    pairs.toArray.sortBy(_._1).filter(valCount => valCount._2 > 0.0 )
  }
}


object HdrHistogramGenerator{
/*    val recorder = new Recorder(3)
    val recorder2 = new Recorder(3)


    val data = for(step <- 0 to 0) yield {
      val generator = new ExponentialInterRequestTimeGenerator(100.0 + step * 100)
      for(i <- 0 to 1000) {
        recorder.recordValue(generator.nextValue / 10000000)
      }
      val histogram = recorder.getIntervalHistogram
      val data = getData(histogram)
      val xy = data.unzip



      // values / bins and counts / hist
      val bins = DenseVector(xy._1)
      val hist = DenseVector(xy._2)

      // compute bin midpoints
      val bin_midpoints = bins(0 to (bins.length - 2)) + (diff(bins) / 2.0)

      // cumulative sum
      val acc_hist =  accumulate(hist)
      // normalize  cumulative sum
      val cdf = acc_hist / acc_hist(-1)

      // draw random from histogram
      val generator2 = new Well19937c()
      for(i <- 0 to 1000) {
        val well = generator2.nextDouble()
        val index = cdf.iterator.indexWhere(_._2 >= well)
        recorder2.recordValue(bins(index).toLong)
      }
      val histogram2 = recorder2.getIntervalHistogram


      val data2 = getData(histogram2)
      val xy2 = data2.unzip



      (Series(s"${100.0 + step * 10} ops/sec", xy._1, xy._2),  Series(s"from histogram - ${100.0 + step * 10} ops/sec", xy2._1, xy2._2))
    }



    val chart = getXYChart(data flatMap (x => List(x._1, x._2)))
    new SwingWrapper[XYChart](chart).displayChart()*/




}
