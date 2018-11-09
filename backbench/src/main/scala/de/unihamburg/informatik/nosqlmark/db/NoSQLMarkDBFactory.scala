package de.unihamburg.informatik.nosqlmark.db

import java.util.Properties

import com.yahoo.ycsb.{DB, DBWrapper, UnknownDBException}

/**
 * Created by Steffen Friedrich on 09.10.2015.
 */
object NoSQLMarkDBFactory {

  @throws(classOf[UnknownDBException])
  def newDB(dbname: String, properties: Properties): DB = {

    try {
      val dbclass: Class[_] = if (loadClass(dbname).isDefined) loadClass(dbname).get
      else {
        val nosqlmarkDBName = "de.unihamburg.informatik.nosqlmark.db." + dbname
        if (loadClass(nosqlmarkDBName).isDefined) loadClass(nosqlmarkDBName).get
        else {
          val ycsbDBName = "com.yahoo.ycsb.db." + dbname
          if (loadClass(ycsbDBName).isDefined) loadClass(ycsbDBName).get
            else {
            val basicDB = "com.yahoo.ycsb." + dbname
            if (loadClass(basicDB).isDefined) loadClass(basicDB).get
            else throw new UnknownDBException("can't find database client class " + dbname)
          }
          }
      }
      val db = dbclass.newInstance().asInstanceOf[DB]
      db.setProperties(properties)
      db
    }
    catch {
      case e: Exception => {
        e.printStackTrace
        return null
      }
    }
  }


  private def loadClass(className: String): Option[Class[_]] = {
    val classLoader: ClassLoader = this.getClass().getClassLoader()
    try  {
      Some(classLoader.loadClass(className))
    } catch  {
      case e: Exception => None
    }
  }
}

