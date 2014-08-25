scalaVersion := "2.11.1"

libraryDependencies += "org.scala-lang" % "scala-reflect" % "2.11.1"

scalacOptions += "-language:implicitConversions"

scalacOptions += "-deprecation"

scalacOptions += "-Yinline-warnings"

unmanagedJars in Compile <<= baseDirectory map { base => (base ** "*.jar").classpath }

// there is a bug in SBT that does not allow compiler plugins to have dependencies
addCommandAlias("embed", ";project lego-core ;embed ;project root; legolifter/run ; clean")
