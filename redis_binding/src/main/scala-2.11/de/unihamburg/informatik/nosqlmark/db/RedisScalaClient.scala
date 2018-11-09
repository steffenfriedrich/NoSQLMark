package de.unihamburg.informatik.nosqlmark.db

import java.util

import com.redis.RedisClientPool
import com.yahoo.ycsb.{StringByteIterator, _}

import scala.collection.JavaConversions._

/**
  * Created by Steffen Friedrich on 24.04.2017.
  */
class RedisScalaClient extends DB {
  val HOST_PROPERTY = "redis.host"
  val PORT_PROPERTY = "redis.port"
  val PASSWORD_PROPERTY = "redis.password"
  val INDEX_KEY = "_indices"
  var clients: RedisClientPool = _

  @throws[DBException]
  override def init(): Unit = {
    val props = getProperties
    val port = props.getProperty(PORT_PROPERTY, "6379").toInt
    val host = props.getProperty(HOST_PROPERTY, "localhost")
    val password = Some(props.getProperty(PASSWORD_PROPERTY, null))
    clients = new RedisClientPool(host, port, secret = password)
  }

  @throws[DBException]
  override def cleanup() {
    clients.close
  }

  override def update(table: String, key: String, values: util.Map[String, ByteIterator]): Status =
    clients.withClient {
      client => {
        if (client.hmset(key, StringByteIterator.getStringMap(values)) == "OK") Status.OK
        else Status.ERROR
      }
    }

  override def insert(table: String, key: String, values: util.Map[String, ByteIterator]): Status =
    clients.withClient {
      client => {
        if (client.hmset(key, StringByteIterator.getStringMap(values)) == "OK") {
          client.zadd(INDEX_KEY, key.hashCode, key)
          Status.OK
        } else Status.ERROR
      }
    }

  override def delete(table: String, key: String): Status = clients.withClient {
    client => {
      if ((client.del(key).get == 0) && (client.zrem(INDEX_KEY, key).get == 0)) Status.ERROR
      else Status.OK
    }
  }

  override def scan(table: String, startkey: String, recordcount: Int, fields: util.Set[String],
                    result: util.Vector[util.HashMap[String, ByteIterator]]): Status = clients.withClient {
    client => {
      val keys = client.zrangebyscore(INDEX_KEY, startkey.hashCode, true, Double.PositiveInfinity, true, Option((0, recordcount)))
      var values = new util.HashMap[String, ByteIterator]
      keys.get.foreach(key => {
        read(table, key, fields, values)
        result.add(values)
      })
      Status.OK
    }
  }

  override def read(table: String, key: String, fields: util.Set[String], result: util.Map[String, ByteIterator]): Status =
    clients.withClient {
      client => {
        if (fields == null) StringByteIterator.putAllAsByteIterators(result, client.hgetall1(key).get)
        else {
          val values = client.hmget(key, fields)
          values.foreach(value => println(value))

        }
        if(result.isEmpty) Status.ERROR else Status.OK
      }
    }
}
