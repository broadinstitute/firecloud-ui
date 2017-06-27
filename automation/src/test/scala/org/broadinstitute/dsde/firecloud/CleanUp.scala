package org.broadinstitute.dsde.firecloud

import com.typesafe.scalalogging.LazyLogging
import org.scalatest.{Outcome, Suite, SuiteMixin}

import scala.collection.mutable

/**
  * Mix-in for cleaning up data created during a test.
  */
trait CleanUp extends SuiteMixin with LazyLogging { this: Suite =>

  private val cleanUpFunctions = mutable.MutableList[() => Any]()


  object register {

    def cleanUp(f: => Any): Unit = {
      cleanUpFunctions += f _
    }
  }

  abstract override def withFixture(test: NoArgTest): Outcome = {
    try {
      super.withFixture(test)
    } finally {
      cleanUpFunctions foreach { f =>
        try {
          f()
        } catch {
          case e: Throwable => logger.warn("Failure in clean-up function", e)
        }
      }
      // TODO: is this okay if tests are run in parallel?
      cleanUpFunctions.clear()
    }
  }
}
