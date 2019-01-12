package org.broadinstitute.dsde.firecloud

import org.broadinstitute.dsde.workbench.service.test.Awaiter
import org.openqa.selenium.{TimeoutException, WebDriver}
import org.scalatest.exceptions.TestFailedException

trait Stateful { this: FireCloudView =>
  val query: Query

  def getState(implicit webDriver: WebDriver): String = {
    stateOf(query)
  }

  def stateOf(query: Query)(implicit webDriver: WebDriver): String = {
    query.element.attribute("data-test-state").getOrElse("")
  }

  def awaitState(state: String)(implicit webDriver: WebDriver): Unit = {
    await condition {
      try {
        getState == state
      } catch {
        case _: TestFailedException => false
      }
    }
  }
}

trait SignalsReadiness extends Stateful { this: FireCloudView =>
  implicit val webDriver: WebDriver

  def isReady(implicit webDriver: WebDriver): Boolean = getState == "ready"

  override def awaitReady(): Unit = {
    try {
      await condition isReady
    } catch {
      case e: TimeoutException => throw new TimeoutException(s"Timed out waiting for [$query] data-test-state == ready on page ${webDriver.getCurrentUrl}. Actural value is ${getState}")
    }
  }
}

trait ReadyComponent { this: FireCloudView =>
  implicit val webDriver: WebDriver

  val readyComponent: Awaiter

  override def awaitReady(): Unit = readyComponent.awaitReady()
}
