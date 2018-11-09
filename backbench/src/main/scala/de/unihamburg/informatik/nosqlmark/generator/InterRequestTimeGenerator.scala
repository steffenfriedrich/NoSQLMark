package de.unihamburg.informatik.nosqlmark.generator

import com.yahoo.ycsb.generator.ExponentialGenerator
import org.apache.commons.math3.distribution.{ExponentialDistribution, GammaDistribution, LogNormalDistribution}
import org.apache.commons.math3.random.Well19937c


/**
  * Created by Steffen Friedrich on 16.02.2017.
  *
  * The selection of distributions is based on
  * Http://perfdynamics.blogspot.de/2010/05/load-testing-think-time-distributions.html
  */
abstract class InterRequestTimeGenerator(target: Double) {
  val upperBound = target * 100.1 / 100
  val upperBound2 = target * 101 / 100
  val lowerBound = target * 99.99 / 100
  val lowerBound2 = target * 99.9 / 100
  val delta = target / 110
  var actualTarget = target
  var lastThroughput = target

  def nextValue: Long

/*  def correctTarget(throughput: Double) = {
    val deltaThroughput = throughput - lastThroughput
    if (throughput < lowerBound2 && deltaThroughput <= 0) actualTarget = actualTarget + delta
    else if (throughput < lowerBound && deltaThroughput <= 0) actualTarget = actualTarget + 1
    else if (throughput > upperBound2 && deltaThroughput >= 0) actualTarget = actualTarget - delta
    else if (throughput > upperBound && deltaThroughput >= 0) actualTarget = actualTarget - 1
    if (throughput < lowerBound || throughput > upperBound) {
      println(s"$throughput, target $target, actualTarget $actualTarget, deltaThroughput $deltaThroughput, targetOpsPerMs $targetOpsPerMs")
      targetOpsPerMs
      targetOpsTickNs
      newGenerator
    }
    lastThroughput = throughput
  }*/

  def targetOpsPerMs = actualTarget / 1000.0d

  def targetOpsTickNs = (1000000 / targetOpsPerMs)

  def newGenerator
}

class ConstantInterRequestTimeGenerator(target: Double) extends InterRequestTimeGenerator(target: Double) {
  def nextValue: Long = targetOpsTickNs.toLong

  def newGenerator = None
}

class ExponentialInterRequestTimeGenerator(target: Double) extends InterRequestTimeGenerator(target: Double) {
  private var generator = new ExponentialGenerator(targetOpsTickNs)

  def nextValue: Long = generator.nextValue().longValue()

  def newGenerator = {
    generator = new ExponentialGenerator(targetOpsTickNs)
  }
}
/*
class UncommonsExponentialInterRequestTimeGenerator(target: Double) extends InterRequestTimeGenerator(target: Double) {
  val rng = new MersenneTwisterRNG();
  private var gen = new org.uncommons.maths.random.ExponentialGenerator(targetOpsTickNs, rng)

  def nextValue: Long = gen.nextValue().longValue()

  def newGenerator = {
    gen = new org.uncommons.maths.random.ExponentialGenerator(targetOpsTickNs, rng)
  }
}*/

class CommonsExponentialInterRequestTimeGenerator(target: Double) extends InterRequestTimeGenerator(target: Double) {
  private var gen = new ExponentialDistribution(targetOpsTickNs)

  def nextValue: Long = gen.sample().longValue()

  def newGenerator = {
    gen = new ExponentialDistribution(targetOpsTickNs)
  }

}

class GammaInterRequestTimeGenerator(target: Double, scale: Double) extends InterRequestTimeGenerator(target: Double) {
  private var gen = new GammaDistribution(shape, scale)

  def nextValue: Long = gen.sample().longValue()

  def newGenerator = {
    gen = new GammaDistribution(shape, scale)
  }

  def shape = targetOpsTickNs / scale
}

class LognormalInterRequestTimeGenerator(target: Double, scale: Double) extends InterRequestTimeGenerator(target: Double) {
  private var gen = new LogNormalDistribution(shape, scale)

  def nextValue: Long = gen.sample().longValue()

  def newGenerator = {
    gen = new LogNormalDistribution(shape, scale)
  }

  def shape = math.log(targetOpsTickNs) - (scale * scale) / 2
}


class MixtureInterRequestTimeGenerator(target: Double, distributions: List[Tuple2[Double, InterRequestTimeGenerator]])
  extends InterRequestTimeGenerator(target: Double) {
  val sum = distributions.foldLeft(0.0)(_ + _._1)

  if (sum !=  1.0) {
    throw new IllegalArgumentException(s"The probabilities for the mixture distribution to generate inter request times " +
      s"must be summed up to 1.0. Is $sum")
  }
  val random = new Well19937c()
  val sorted = accumulate_probs(distributions)

  override def nextValue: Long = {
    val rnd = random.nextDouble()
    val distribution = sorted.find(_._1 > rnd).get
    distribution._2.nextValue
  }

  override def newGenerator: Unit = {
    // ToDo implement MixtureInterRequestTimeGenerator
  }

  def accumulate_probs(distributions: List[Tuple2[Double, InterRequestTimeGenerator]]): List[Tuple2[Double, InterRequestTimeGenerator]] = {
    def acculate_probs_rec(distributions: List[Tuple2[Double, InterRequestTimeGenerator]]): List[Tuple2[Double, InterRequestTimeGenerator]] = {
      distributions match {
        case x :: xs => x :: acculate_probs_rec(xs match {
          case y :: ys => (x._1 + y._1 -> y._2) :: ys
          case _ => Nil
        })
        case _ => Nil
      }
    }
    acculate_probs_rec(distributions.sortBy(x => (x._1 * -1)))
  }


}

