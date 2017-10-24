package org.broadinstitute.dsde.firecloud.component

import org.broadinstitute.dsde.firecloud.FireCloudView
import org.openqa.selenium.{JavascriptExecutor, WebDriver}

sealed trait QueryString
case class TestId(id: String) extends QueryString
case class CSSQuery(text: String) extends QueryString

abstract class Component(queryString: QueryString)(implicit webDriver: WebDriver) extends FireCloudView {
  val query: CssSelectorQuery = queryString match {
    case TestId(id) => testId(id)
    case CSSQuery(text) => CssSelectorQuery(text)
  }

  def findInner(id: String): CssSelectorQuery = testId(id) inside query

  implicit class IDUtil(id: String) {
    def inside(parent: Component): QueryString = {
      CSSQuery(parent.query.queryString + " " + testId(id).queryString)
    }
  }

  def awaitVisible(): Unit = await visible query
  def awaitNotVisible(): Unit = await notVisible query

  def isVisible: Boolean = find(query).isDefined
  def isEnabled: Boolean = enabled(query)

  override def awaitReady(): Unit = awaitVisible()

  def scrollToVisible(): Unit = {
    webDriver.asInstanceOf[JavascriptExecutor].executeScript("arguments[0].scrollIntoView(true)", find(query).get.underlying)
  }
}

object Component {
  implicit def string2QueryString(s: String) = TestId(s)
}
