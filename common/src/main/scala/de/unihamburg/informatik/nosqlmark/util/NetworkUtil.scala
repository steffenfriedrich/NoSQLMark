package de.unihamburg.informatik.nosqlmark.util

import java.net.{InetAddress, Inet4Address, NetworkInterface}
import java.nio.ByteBuffer
import scala.collection.JavaConversions._

/**
 * Created by Steffen Friedrich on 07.09.2015.
 */
object NetworkUtil {
  val ip = getIPAddress
  val mac = getMacAddress
  val ipString = getIPAddressString

  private def getIPAddressString = {
    val bb = ByteBuffer.wrap(ip)
    (for(i <- 0 to 3) yield (bb.get() & 0xFF).toString).reduce((a , b) => a + "." + b)
  }

  private def getIPAddress = {
    val ips = NetworkInterface.getNetworkInterfaces.flatMap(intf =>
      intf.getInetAddresses)
    ips.filter(f => {
      f.isInstanceOf[Inet4Address] && !f.isLoopbackAddress
    }).next.getAddress
  }

  private def getMacAddress = {
    try {
      val ips = NetworkInterface.getNetworkInterfaces.flatMap(intf =>
        intf.getInetAddresses)
      val ip = ips.filter(f => {
        f.isInstanceOf[Inet4Address] && !f.isLoopbackAddress
      }).next
      NetworkInterface.getByInetAddress(ip).getHardwareAddress
    } catch { // return loopback addresses mac address
      case e: Exception => NetworkInterface.getByInetAddress(InetAddress.getLoopbackAddress).getHardwareAddress
    }
  }
}
