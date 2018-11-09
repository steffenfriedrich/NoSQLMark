// Comment to get more information during initialization
logLevel := Level.Warn

resolvers += "Typesafe repository" at "https://repo.typesafe.com/typesafe/releases/"

// Multi-JVM testing
addSbtPlugin("com.typesafe.sbt" % "sbt-multi-jvm" % "0.4.0")

// The Play plugin
addSbtPlugin("com.typesafe.play" % "sbt-plugin" % "2.4.2")

addSbtPlugin("org.xerial.sbt" % "sbt-pack" % "0.6.8")