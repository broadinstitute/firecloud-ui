package org.broadinstitute.dsde.firecloud.util

import java.io.File
import java.nio.file.Files
import java.util.UUID

import scala.concurrent.duration.FiniteDuration
import scala.util.Random

/**
  */
object Util {

  def appendUnderscore(string: String): String = {
    string match {
      case "" => ""
      case s => s + "_"
    }
  }

  def makeRandomId(length: Int = 7): String = {
    Random.alphanumeric.take(length).mkString
  }

  def makeUuid: String = {
    UUID.randomUUID().toString
  }

  /**
    * Move a file, making sure the destination directory exists.
    *
    * @param sourcePath path to source file
    * @param destPath path to desired destination file
    */
  def moveFile(sourcePath: String, destPath: String): Unit = {
    val dest = new File(destPath)
    if (!dest.getParentFile.exists()) {
      dest.getParentFile.mkdirs()
    }
    Files.move(new File(sourcePath).toPath, dest.toPath)
  }
}
