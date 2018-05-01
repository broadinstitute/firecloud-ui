package org.broadinstitute.dsde.firecloud.component

import com.typesafe.scalalogging.LazyLogging
import org.broadinstitute.dsde.firecloud.FireCloudView
import org.openqa.selenium.{JavascriptExecutor, WebDriver}

/**
  * Components can be specified either by an arbitrary CSS selector query, or by the data-test-id directly
  *
  * This class is needed because Query (and CssSelectorQuery) are an inner type of WebBrowser, so
  * every distinct class ends up with a unique Query type which is incompatible with others. To get
  * around this, we communicate ids and queries as Strings
  */
sealed trait QueryString
case class TestId(id: String) extends QueryString
case class CSSQuery(text: String) extends QueryString

/**
  * Base UI component modeling class, subclasses should typically correspond to items in
  * components.cljs or the components package
  *
  * @param queryString the QueryString object representing the root element of the component
  * @param webDriver webdriver
  */
abstract class Component(queryString: QueryString)(implicit webDriver: WebDriver) extends FireCloudView  with LazyLogging {

  val query: CssSelectorQuery = queryString match {
    case TestId(id) => testId(id)
    case CSSQuery(text) => CssSelectorQuery(text)
  }

  /**
    * Generates a CssSelectorQuery for a child of this component
    *
    * This method may be deprecated in the future. Ideally, each element would be modeled by a
    * Component and we would use the "inside" pattern (see below). This hasn't been fully explored yet.
    *
    * @param id data-test-id of inner component
    * @return CssSelectorQuery specifying the inner component
    */
  def findInner(id: String): CssSelectorQuery = testId(id) inside query

  /**
    * String extension for generating QueryStrings for subcomponents
    * @param id data-test-id of inner component
    */
  implicit class IDUtil(id: String) {
    /**
      * Generates a QueryString for a child of the given component
      * @param parent the parent Component
      * @return CSSQuery for the child component
      */
    def inside(parent: Component): QueryString = {
      CSSQuery(parent.query.queryString + " " + testId(id).queryString)
    }
  }

  def awaitVisible(): Unit = await visible query
  def awaitNotVisible(): Unit = await notVisible query
  def awaitEnabled(): Unit = await enabled query

  def isVisible: Boolean = find(query).exists(_.isDisplayed)
  def isEnabled: Boolean = enabled(query)

  override def awaitReady(): Unit = awaitVisible()

  /**
    * References:
    *  .getBoundingClient browser compatibility: https://developer.mozilla.org/en-US/docs/Web/API/Element/getBoundingClientRect
    *  viewport script source: https://stackoverflow.com/questions/123999/how-to-tell-if-a-dom-element-is-visible-in-the-current-viewport
    *
    *  When there is a vertical scroll on page and WebElement is outside of view, scroll WebElement into view
    */
  def scrollToVisible(): Unit = {
    // explanation: https://developer.mozilla.org/en-US/docs/Web/API/Element/scrollIntoView
    // Not working every time
    // webDriver.asInstanceOf[JavascriptExecutor].executeScript("arguments[0].scrollIntoView({behavior: \"instant\"});", find(query).get.underlying)

    if (scrollbar && !inViewport) {
      // Only scroll if needed
      val script = "arguments[0].scrollIntoView(true)"
      val js = webDriver.asInstanceOf[JavascriptExecutor]
      js.executeScript(script, find(query).get.underlying)
      Thread.sleep(1000) // hack to wait page stop "moving"
      logger.debug("executed javaScript scrollIntoView ")
    }
  }

  /**
    * Determine whether vertical scrollbar exist on browser
    * @return True if vertical scrollbar found on page
    */
  private def scrollbar: Boolean = {
    val script = "return document.documentElement.scrollHeight > document.documentElement.clientHeight;"
    val js = webDriver.asInstanceOf[JavascriptExecutor]
    js.executeScript(script).asInstanceOf[Boolean]
  }

  /**
    * Determine whether WebElement is inside of viewport
    * @return True if WebElement is visible inside of viewport
    */
  private def inViewport: Boolean = {
    val script =
      "var rect = arguments[0].getBoundingClientRect();\n" +
        "return (\n" +
        "  rect.top >= 0 &&\n" +
        "  rect.left >= 0 &&\n" +
        "  rect.bottom <= (window.innerHeight || document.documentElement.clientHeight) &&\n" +
        "  rect.right <= (window.innerWidth || document.documentElement.clientWidth));"

    val js = webDriver.asInstanceOf[JavascriptExecutor]
    js.executeScript(script, find(query).get.underlying).asInstanceOf[Boolean]
  }

}

/**
  * Import org.broadinstitute.dsde.firecloud.component.Component._ to allow creating
  * Components with String data-test-ids directly
  */
object Component {
  import scala.language.implicitConversions
  implicit def string2QueryString(s: String) = TestId(s)
}
