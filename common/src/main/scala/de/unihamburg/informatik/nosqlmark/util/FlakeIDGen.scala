package de.unihamburg.informatik.nosqlmark.util

import java.nio.ByteBuffer

/**
 * Flake ids are 128-bits wide described here from most significant to least significant bits.
 * 64-bit timestamp - milliseconds since the epoch (Jan 1 1970)
 * 48-bit worker id - MAC address from a configurable device
 * 16-bit sequence # - usually, 0 incremented when more than one id is requested in the same millisecond and reset to 0 when the clock ticks forward
 *
 * Created by Steffen Friedrich on 22.05.2015.
 */
object FlakeIDGen {

  var lastTime: Long = 0
  var sequence: Int = 0
  var counter: Int = 0


  def getSnowflakeMacId(mac: Array[Byte]): Array[Byte] = {
    this.synchronized {
      val currentTime = System.currentTimeMillis
      if (currentTime < lastTime) {

      } else if (currentTime > lastTime) {
        sequence = 0
      } else {
        sequence = sequence + 1
      }
      lastTime = currentTime

      val bb = ByteBuffer.allocate(16)
      bb.rewind
      bb.putLong(currentTime)
      bb.put(mac)
      bb.putShort(sequence.toShort)
      return bb.array
    }
  }

  def getSnowflakeIpId(ip: Array[Byte]): Array[Byte] = {
    this.synchronized {
      val currentTime = System.currentTimeMillis
      if (currentTime < lastTime) {

      } else if (currentTime > lastTime) {
        sequence = 0
      } else {
        sequence = sequence + 1
      }
      lastTime = currentTime

      val bb = ByteBuffer.allocate(14)
      bb.rewind
      bb.putLong(currentTime)
      bb.put(ip)
      bb.putShort(sequence.toShort)
      return bb.array
    }
  }

  def getIPCounterID(ip: Array[Byte]): Array[Byte] = {
    this.synchronized {
      counter += 1
      val bb = ByteBuffer.allocate(8)
      bb.rewind
      bb.put(ip)
      bb.putInt(counter)
      return bb.array
    }
  }

  def getIPCounterID(ip: Array[Byte], nr: Int): Array[Byte] = {
    this.synchronized {
      val bb = ByteBuffer.allocate(8)
      bb.rewind
      bb.put(ip)
      bb.putInt(nr)
      return bb.array
    }
  }

  def getIpCounterIDString(ip: Array[Byte]): String ={
    ipCounterIdToString(getIPCounterID(ip))
  }

  def getIpCounterIDString(ip: Array[Byte], nr: Int): String ={
    ipCounterIdToString(getIPCounterID(ip, nr))
  }

  def getCounterID: Int = {
    this.synchronized {
      counter += 1
      return counter
    }
  }

  def getCounterIDString: String = {
    this.synchronized {
      counter += 1
      return counter.toString
    }
  }

  def getSnowflakeMacIdString(mac: Array[Byte]): String = {
    snowflakeMacIdToString(getSnowflakeMacId(mac))
  }

  def getSnowflakeIpIdString(ip: Array[Byte]): String ={
    snowflakeIpIdToString(getSnowflakeIpId(ip))
  }



  private def snowflakeMacIdToString(ba: Array[Byte] ): String = {
    val bb = ByteBuffer.wrap(ba)
    val ts = bb.getLong
    val node_0 = bb.getInt
    val node_1 = bb.getShort
    val seq = bb.getShort
    ts + "-" + Integer.toHexString(node_0) + Integer.toHexString(node_1) + "-" + seq
  }

  private def snowflakeIpIdToString(ba: Array[Byte] ): String = {
    val bb = ByteBuffer.wrap(ba)
    val ts = bb.getLong
    val ip = (for(i <- 0 to 3) yield (bb.get() & 0xFF).toString).reduce((a , b) => a + b)
    val seq = bb.getShort
    ts + "-" + ip + "-" + seq
  }

  private def ipCounterIdToString(ba: Array[Byte] ): String = {
    val bb = ByteBuffer.wrap(ba)
    val ip = (for(i <- 0 to 3) yield (bb.get() & 0xFF).toString).reduce((a , b) => a + b)
    val seq = bb.getInt
    ip + "-" + seq
  }
}
