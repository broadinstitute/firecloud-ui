package org.broadinstitute.dsde.firecloud.util

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

  def retry[T](remainingBackOffIntervals: Seq[FiniteDuration])(op: => Option[T]): Option[T] = {
    op match {
      case Some(x) => Some(x)
      case None if remainingBackOffIntervals.isEmpty => None
      case None if remainingBackOffIntervals.nonEmpty =>
        Thread sleep remainingBackOffIntervals.head.toMillis
        retry(remainingBackOffIntervals.tail)(op)
    }
  }
}
