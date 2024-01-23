package sttp.tapir.perf

import io.gatling.core.scenario.Simulation
import io.github.classgraph.ClassGraph
import sttp.tapir.perf.apis.ServerRunner

import scala.jdk.CollectionConverters._
import scala.reflect.ClassTag
import scala.util.{Failure, Success, Try}

import Common._

/** Uses the classgraph library to quickly find all possible server runners (objects extending ServerRunner) or simulations (classes
  * extending Simulation).
  */
object TypeScanner {
  def findAllImplementations[T: ClassTag](rootPackage: String): List[Class[_]] = {
    val superClass = scala.reflect.classTag[T].runtimeClass

    val scanResult = new ClassGraph()
      .enableClassInfo()
      .acceptPackages(rootPackage)
      .scan()

    try {
      val classes =
        if (superClass.isInterface)
          scanResult.getClassesImplementing(superClass.getName)
        else
          scanResult.getSubclasses(superClass.getName)
      classes.loadClasses().asScala.toList
    } finally {
      scanResult.close()
    }
  }

  lazy val allServers: List[String] =
    findAllImplementations[ServerRunner](rootPackage)
      .map(_.getName)
      .map(c => c.stripPrefix(s"${rootPackage}.").stripSuffix("Server$"))

  lazy val allSimulations: List[String] =
    findAllImplementations[Simulation](rootPackage)
      .map(_.getName)
      .map(c => c.stripPrefix(s"${rootPackage}.").stripSuffix("Simulation"))

  def enusureExist(serverShortNames: List[String], simShortNames: List[String]): Try[Unit] = {
    val missingServers = serverShortNames.filterNot(allServers.contains)
    val missingSims = simShortNames.filterNot(allSimulations.contains)

    if (missingServers.isEmpty && missingSims.isEmpty) {
      Success(())
    } else {
      val missingServersMessage =
        if (missingServers.nonEmpty)
          s"Unrecognized servers: ${missingServers.mkString(", ")}. Available servers: ${allServers.mkString(", ")}.\n"
        else ""
      val missingSimsMessage =
        if (missingSims.nonEmpty)
          s"Unrecognized simulations: ${missingSims.mkString(", ")}. Available simulations: ${allSimulations.mkString(", ")}"
        else ""
      Failure(new IllegalArgumentException(s"$missingServersMessage $missingSimsMessage".trim))
    }
  }
}
