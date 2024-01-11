package sttp.tapir.perf

import io.github.classgraph.ClassGraph
import scala.reflect.ClassTag
import scala.jdk.CollectionConverters._
import sttp.tapir.perf.apis.ServerRunner
import Common._
import io.gatling.core.scenario.Simulation

/**
  * Uses the classgraph library to quickly find all possible server runners (objects extending ServerRunner) or
  * simulations (classes extending Simulation).
  */
object TypeScanner {
  def findAllImplementations[T: ClassTag](rootPackage: String): List[Class[_]] = {
    val superClass = scala.reflect.classTag[T].runtimeClass

    val scanResult = new ClassGraph()
      .enableClassInfo()
      .acceptPackages(rootPackage)
      .scan()

    try {
      val classes = if (superClass.isInterface) 
        scanResult.getClassesImplementing(superClass.getName)
      else
        scanResult.getSubclasses(superClass.getName)
      classes.loadClasses().asScala.toList
    } finally {
      scanResult.close()
    }
  }

  def allServers: List[String] =
    findAllImplementations[ServerRunner](rootPackage)
      .map(_.getName)
      .map(c => c.stripPrefix(s"${rootPackage}.").stripSuffix("Server$"))
  
  def allSimulations: List[String] =
    findAllImplementations[Simulation](rootPackage)
      .map(_.getName)
      .map(c => c.stripPrefix(s"${rootPackage}.").stripSuffix("Simulation"))
}
