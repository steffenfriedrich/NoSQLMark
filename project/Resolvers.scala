import sbt._
import Keys._

object Resolvers {
  val my_resolvers = Seq(
    "Local Maven Repository" at "" + Path.userHome.asFile.toURI.toURL + "/.m2/repository/",
    "Typesafe Repo" at "http://repo.typesafe.com/typesafe/releases/",
    "Sonatype Releases" at "http://oss.sonatype.org/content/repositories/releases",
    "JBoss Releases" at "https://repository.jboss.org/nexus/content/repositories/public",
    "Typesafe Snapshots" at "http://repo.typesafe.com/typesafe/snapshots/",
    "Sonatype Snapshots" at "http://oss.sonatype.org/content/repositories/snapshots",
    "pathikrit" at "https://dl.bintray.com/pathikrit/maven/",
    "nosqlmark" at "http://nosqlmark.informatik.uni-hamburg.de/maven2"
  )
}