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
    val viewportScript =
      "if (arguments[0].getBoundingClientRect) {\n" +
        "var rect = arguments[0].getBoundingClientRect();\n" +
        "return (\n" +
        "  rect.top >= 0 &&\n" +
        "  rect.left >= 0 &&\n" +
        "  rect.bottom <= (window.innerHeight || document.documentElement.clientHeight) &&\n" +
        "  rect.right <= (window.innerWidth || document.documentElement.clientWidth));\n" +
        "} else { return false; };";

    val js = webDriver.asInstanceOf[JavascriptExecutor]

    val inViewport: Boolean = js.executeScript(viewportScript, find(query).get.underlying).asInstanceOf[Boolean]
    if (!inViewport) {
      // does page has a vertical scrollbar?
      val scrollbarScript = "return document.documentElement.scrollHeight > document.documentElement.clientHeight;"
      val verticalScroll = js.executeScript(scrollbarScript).asInstanceOf[Boolean]
      logger.debug(s"vertical scroll on page check: $verticalScroll")
      if (verticalScroll) {
        // if vertical scroll exist and WebElement is outside of viewport, then scroll into viewport
        logger.debug(s"executed scrllIntoView Javascript on ${query.element.underlying}")
        webDriver.asInstanceOf[JavascriptExecutor].executeScript("arguments[0].scrollIntoView(true)", find(query).get.underlying)
        Thread.sleep(250) // hack to wait page stop "moving" after scrollToVisible
      }
    }
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
