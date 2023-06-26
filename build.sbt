name := "datamarts-smdmitrieva"
organization := "ru.beeline"

version := "0.1"

//resolvers += "Nexus local" at "https://nexus-repo.dmp.vimpelcom.ru/repository/sbt_releases_/"

ThisBuild / scalaVersion := "2.11.8"
val sparkVersion = "2.3.2"

libraryDependencies ++= Seq(
  "org.apache.spark" %% "spark-core" % sparkVersion  % "provided",
  "org.apache.spark" %% "spark-sql" % sparkVersion  % "provided",
  "org.postgresql" % "postgresql" % "42.3.3"
)

assembly / artifact := {
  val art = (assembly / artifact).value
  art.withClassifier(Some("assembly"))
}

addArtifact(assembly / artifact, assembly)

assemblyMergeStrategy in assembly := {
  case path if path.contains("META-INF/services") => MergeStrategy.concat
  case PathList("META-INF", _*) => MergeStrategy.discard
  case _ => MergeStrategy.first
}


//If you need to publish artifact to nexus
publishTo := {
  val nexus = "https://nexus-repo.dmp.vimpelcom.ru/"
  if (version.value.endsWith("-9999"))
    Some("Sonatype Nexus Repository Manager" at nexus + "repository/snapshots_")
  else
    Some("Sonatype Nexus Repository Manager" at nexus + "repository/sbt_releases_")
}
credentials += Credentials(Path.userHome / ".sbt" / ".credentials")