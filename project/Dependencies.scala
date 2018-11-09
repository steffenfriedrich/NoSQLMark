import sbt._

object Dependencies {
  // implicit filter that applies the exclude to each dependency in a list
  implicit def dependencyFilterer(deps: Seq[ModuleID]) = new Object {
    def excluding(group: String, artifactId: String) =
      deps.map(_.exclude(group, artifactId))

    def excluding(excludes: Seq[Pair[String, String]]): Seq[ModuleID] = {
      deps.map(dep => {
        excludes.foreach(e => dep.exclude(e._1, e._2))
        dep
      })
    }
  }

  // Versions
  val akka_version = "2.4.11"
  val akka_kryo_version = "0.5.0"
  val play_version = "2.4.4"
  val scala_version = "2.11.8"
  val netty_version = "4.0.33.Final"
  val logback_version = "1.1.3"
  val scalatest_version = "2.2.4"
  val ycsb_version = "0.14.0-SNAPSHOT"
  val jline_version = "2.11"
  val hdrhistogram_version = "2.1.8"
  val sickstore_version = "1.9.2"
  val scallop_version = "0.9.5"
  val scalameter_version = "0.9"
  val redis_version = "2.9.0"
  val xchart_version = "3.5.0"
  val breeze_version = "0.13.2"
  val better_files_version = "3.4.0"

  val jacksonVersion = "2.9.4"
  val jacksons = Seq(
    "com.fasterxml.jackson.core" % "jackson-core",
    "com.fasterxml.jackson.core" % "jackson-annotations",
    "com.fasterxml.jackson.core" % "jackson-databind",
    "com.fasterxml.jackson.datatype" % "jackson-datatype-jdk8",
    "com.fasterxml.jackson.datatype" % "jackson-datatype-jsr310"
  ).map(_ % jacksonVersion)

  // Libraries
  val excluded_deps = Seq(
    "org.scala-lang" % "scala-compiler" % scala_version,
    "org.scala-lang" % "scala-library" % scala_version,
    "org.scala-lang" % "scala-reflect" % scala_version,
    "jline" % "jline" % jline_version
  )

  val excluded_ycsb_deps = Seq(
    "commons-io" % "commons-io" % "2.4",
    "com.google.guava" % "guava" % "16.0.1",
    "com.google.guava" % "guava-jdk5" % "17.0",
    "org.hdrhistogram" % "HdrHistogram" % hdrhistogram_version
  )

  val logging_deps = Seq(
    "ch.qos.logback" % "logback-classic" % logback_version,
    "com.typesafe.akka" %% "akka-slf4j" % akka_version
  )

  val common_deps = excluded_deps ++ excluded_ycsb_deps ++ logging_deps ++ jacksons ++ Seq(
    "com.typesafe.akka" %% "akka-actor" % akka_version,
    "com.typesafe.akka" %% "akka-cluster" % akka_version,
    "com.typesafe.akka" %% "akka-cluster-tools" % akka_version,
    "com.typesafe.akka" %% "akka-testkit" % akka_version % "test",
    "com.typesafe.akka" %% "akka-multi-node-testkit" % akka_version % "test",
    "com.github.romix.akka" %% "akka-kryo-serialization" % akka_kryo_version,
    "com.typesafe.play" %% "play-json" % play_version,
    "org.scalatest" %% "scalatest" % scalatest_version % "test",
    "org.rogach" % "scallop_2.11" % scallop_version,
    "com.github.pathikrit" %% "better-files" % better_files_version,
    "com.storm-enroute" %% "scalameter" % "0.7",
    "org.knowm.xchart" % "xchart" % xchart_version,
    "org.scalanlp" %% "breeze" % breeze_version
  ).excluding("org.scala-lang", "scala-compiler").
    excluding("org.scala-lang", "scala-library").
    excluding("org.jboss.netty", "netty").
    excluding("jline", "jline").
    excluding("org.apache.logging.log4j", "log4j-slf4j-impl").
    excluding("org.slf4j", "slf4j-api").
    excluding("org.slf4j", "slf4j-simple").
    excluding("org.slf4j", "slf4j-log4j12").
    excluding("commons-io", "commons-io").
    excluding("com.google.guava", "guava").
    excluding("com.google.guava", "guava-jdk5").
    excluding("com.google.collections", "google-collections").
    excluding("com.fasterxml.jackson.core", "jackson-core").
    excluding("com.fasterxml.jackson.core", "jackson-annotations").
    excluding("com.fasterxml.jackson.core", "jackson-databind").
    excluding("com.fasterxml.jackson.datatype", "jackson-datatype-jdk8").
    excluding("com.fasterxml.jackson.datatype", "jackson-datatype-jsr310")


  val webjars = Seq(
    "org.webjars" % "requirejs" % "2.1.16",
    "org.webjars" % "jquery" % "2.1.3",
    "org.webjars" % "d3js" % "3.5.3",
    "org.webjars" % "bootstrap" % "3.3.4" exclude("org.webjars", "jquery"))

  val ycsb_core = Seq("com.yahoo.ycsb" % "core" % ycsb_version)

  val ycsb_bindings = Seq(
    //"com.yahoo.ycsb" % "accumulo-binding" % ycsb_version,
    "com.yahoo.ycsb" % "aerospike-binding" % ycsb_version,
    "com.yahoo.ycsb" % "arangodb-binding" % ycsb_version,
    "com.yahoo.ycsb" % "arangodb3-binding" % ycsb_version,
    "com.yahoo.ycsb" % "asynchbase-binding" % ycsb_version,
    "com.yahoo.ycsb" % "azuredocumentdb-binding" % ycsb_version,
    "com.yahoo.ycsb" % "azuretablestorage-binding" % ycsb_version,
    "com.yahoo.ycsb" % "cassandra-binding" % ycsb_version,
    "com.yahoo.ycsb" % "cloudspanner-binding" % ycsb_version,
    "com.yahoo.ycsb" % "couchbase-binding" % ycsb_version,
    "com.yahoo.ycsb" % "couchbase2-binding" % ycsb_version,
    "com.yahoo.ycsb" % "dynamodb-binding" % ycsb_version,
    "com.yahoo.ycsb" % "elasticsearch-binding" % ycsb_version,
    "com.yahoo.ycsb" % "elasticsearch5-binding" % ycsb_version,
    "com.yahoo.ycsb" % "geode-binding" % ycsb_version,
    "com.yahoo.ycsb" % "googlebigtable-binding" % ycsb_version,
    "com.yahoo.ycsb" % "googledatastore-binding" % ycsb_version,
    // "com.yahoo.ycsb" % "hbase094-binding" % ycsb_version,
    "com.yahoo.ycsb" % "hbase098-binding" % ycsb_version,
    "com.yahoo.ycsb" % "hbase10-binding" % ycsb_version,
    "com.yahoo.ycsb" % "hbase12-binding" % ycsb_version,
    "com.yahoo.ycsb" % "hypertable-binding" % ycsb_version,
    "com.yahoo.ycsb" % "infinispan-binding" % ycsb_version,
    "com.yahoo.ycsb" % "jdbc-binding" % ycsb_version,
    "com.yahoo.ycsb" % "kudu-binding" % ycsb_version,
    "com.yahoo.ycsb" % "memcached-binding" % ycsb_version,
    "com.yahoo.ycsb" % "mongodb-binding" % ycsb_version,
    "com.yahoo.ycsb" % "nosqldb-binding" % ycsb_version,
    "com.yahoo.ycsb" % "orientdb-binding" % ycsb_version,
    "com.yahoo.ycsb" % "rados-binding" % ycsb_version,
    "com.yahoo.ycsb" % "redis-binding" % ycsb_version,
    "com.yahoo.ycsb" % "rest-binding" % ycsb_version,
    "com.yahoo.ycsb" % "riak-binding" % ycsb_version,
    "com.yahoo.ycsb" % "s3-binding" % ycsb_version,
    "com.yahoo.ycsb" % "sickstore-binding" % ycsb_version,
    "com.yahoo.ycsb" % "solr-binding" % ycsb_version,
    "com.yahoo.ycsb" % "solr6-binding" % ycsb_version,
    "com.yahoo.ycsb" % "tarantool-binding" % ycsb_version)

  val ycsb = (excluded_ycsb_deps ++ logging_deps ++ ycsb_core ++ ycsb_bindings ++ jacksons).
    excluding("org.jboss.netty", "netty").
    excluding("org.hdrhistogram", "HdrHistogram").
    excluding("jline", "jline").
    excluding("org.apache.logging.log4j", "log4j-slf4j-impl").
    excluding("org.slf4j", "slf4j-api").
    excluding("org.slf4j", "slf4j-simple").
    excluding("org.slf4j", "slf4j-log4j12").
    excluding("org.apache.avro", "avro-tools").
    excluding("org.apache.avro", "avro-service-archetype").
    excluding("commons-io", "commons-io").
    excluding("com.google.guava", "guava").
    excluding("com.google.guava", "guava-jdk5").
    excluding("com.google.collections", "google-collections").
    excluding("com.fasterxml.jackson.core", "jackson-core").
    excluding("com.fasterxml.jackson.core", "jackson-annotations").
    excluding("com.fasterxml.jackson.core", "jackson-databind").
    excluding("com.fasterxml.jackson.datatype", "jackson-datatype-jdk8").
    excluding("com.fasterxml.jackson.datatype", "jackson-datatype-jsr310")


  val sickstore = excluded_ycsb_deps ++ logging_deps ++ ycsb_core ++ jacksons ++ excludeAllDeps(Seq(
    "de.unihamburg" % "sickstore" % sickstore_version
  ))

  val redis = excluded_ycsb_deps ++ ycsb_core ++ excludeAllDeps(Seq(
    "redis.clients" % "jedis" % redis_version,
    "net.debasishg" %% "redisclient" % "3.4"
  ))

  def excludeAllDeps(deps: Seq[ModuleID]) = deps.excluding("org.hdrhistogram", "HdrHistogram").
    excluding("org.jboss.netty", "netty").
    excluding("jline", "jline").
    excluding("org.apache.logging.log4j", "log4j-slf4j-impl").
    excluding("org.slf4j", "slf4j-api").
    excluding("org.slf4j", "slf4j-simple").
    excluding("org.slf4j", "slf4j-log4j12").
    excluding("org.apache.avro", "avro-tools").
    excluding("org.apache.avro", "avro-service-archetype").
    excluding("commons-io", "commons-io").
    excluding("com.google.guava", "guava").
    excluding("com.google.guava", "guava-jdk5").
    excluding("com.google.collections", "google-collections").
    excluding("com.fasterxml.jackson.core", "jackson-core").
    excluding("com.fasterxml.jackson.core", "jackson-annotations").
    excluding("com.fasterxml.jackson.core", "jackson-databind").
    excluding("com.fasterxml.jackson.datatype", "jackson-datatype-jdk8").
    excluding("com.fasterxml.jackson.datatype", "jackson-datatype-jsr310")

}
