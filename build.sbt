name := "Scheduler"

version := "0.1"

scalaVersion := "2.12.6"

// For Akka 2.5.x and Scala 2.12.x
libraryDependencies += "com.enragedginger" %% "akka-quartz-scheduler" % "1.6.1-akka-2.5.x"

libraryDependencies += "com.typesafe.akka" %% "akka-actor" % "2.5.13"

libraryDependencies += "net.ruippeixotog" %% "scala-scraper" % "2.1.0"
mainClass in (Compile, packageBin) := Some("MainPackage.Main")
libraryDependencies ++= Seq(
  "io.spray" %% "spray-json" % "1.3.4",
  "org.tpolecat" %% "doobie-core" % "0.5.1",
  "org.scalikejdbc" %% "scalikejdbc" % "3.2.1",
  "org.scalikejdbc" %% "scalikejdbc-config" % "3.2.1",
  "org.scalikejdbc" %% "scalikejdbc-test" % "3.2.1" % "test",
  "ch.qos.logback" % "logback-classic" % "1.2.1",
  "com.typesafe.play" %% "anorm" % "2.5.3",
  "org.mongodb" %% "casbah" % "3.1.1"
)
