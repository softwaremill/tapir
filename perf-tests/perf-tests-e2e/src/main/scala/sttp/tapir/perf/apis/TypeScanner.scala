package sttp.tapir.perf.apis

import io.github.classgraph.ClassGraph

import scala.jdk.CollectionConverters._
import scala.reflect.ClassTag

import sttp.tapir.perf.Common._

/** Uses the classgraph library to quickly find all possible server runners (objects extending ServerRunner)
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
}
