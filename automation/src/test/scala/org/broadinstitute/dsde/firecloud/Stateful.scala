package org.broadinstitute.dsde.firecloud

import org.broadinstitute.dsde.workbench.service.test.Awaiter
import org.openqa.selenium.WebDriver
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

  override def awaitReady(): Unit = await condition isReady
}

trait ReadyComponent { this: FireCloudView =>
  implicit val webDriver: WebDriver

  val readyComponent: Awaiter

  override def awaitReady(): Unit = readyComponent.awaitReady()
}
