package org.broadinstitute.dsde.firecloud.util

import java.io.File
import java.nio.file.Files
import java.util.UUID

import com.typesafe.scalalogging.LazyLogging

import scala.util.Random

/**
  */
object Util extends LazyLogging {

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
    val destFile = new File(destPath)
    if (!destFile.getParentFile.exists()) {
      destFile.getParentFile.mkdirs()
    }
    val source = new File(sourcePath).toPath
    val dest = destFile.toPath
    logger.info(s"Moving $source to $dest")
    Files.move(source, dest)
  }
}
