package org.broadinstitute.dsde.firecloud

import org.openqa.selenium.{StaleElementReferenceException, WebDriver}
import org.openqa.selenium.support.ui.{ExpectedCondition, WebDriverWait}
import org.scalatest.exceptions.TestFailedException
import org.scalatest.selenium.WebBrowser

/**
  * Mix-in utilities for ScalaTest's WebBrowser.
  */
trait WebBrowserUtil extends WebBrowser {
  val defaultTimeOutInSeconds: Long = 11

  /**
    * Override of the base find() method to retry in the case of a
    * StaleElementReferenceException.
    */
  abstract override def find(query: Query)(implicit driver: WebDriver): Option[Element] = {
    try {
      super.find(query)
    } catch {
      case _: StaleElementReferenceException => this.find(query)
    }
  }

  /**
    * Extension to ScalaTest's Selenium DSL for waiting on changes in browser
    * state. Example:
    *
    * <pre>
    * await enabled id("myButton")
    * </pre>
    */
  object await {

    /**
      * Waits for a condition to be met.
      *
      * @param condition function returning the Boolean result of the condition check
      * @param timeOutInSeconds number of seconds to wait for the condition to be true
      * @param webDriver implicit WebDriver for the WebDriverWait
      */
    def condition(condition: => Boolean, timeOutInSeconds: Long = defaultTimeOutInSeconds)(implicit webDriver: WebDriver): Unit = {
      val wait = new WebDriverWait(webDriver, timeOutInSeconds)
      wait until new ExpectedCondition[Boolean] {
        override def apply(d: WebDriver): Boolean = {
          condition
        }
      }
    }

    /**
      * Waits for an element to be enabled. Returns the element found by the
      * query to facilitate call chaining, e.g.:
      *
      *   click on (await enabled id("my-button"))
      *
      * @param query Query to locate the element
      * @param timeOutInSeconds number of seconds to wait for the enabled element
      * @param webDriver implicit WebDriver for the WebDriverWait
      * @return the found element
      */
    def enabled(query: Query, timeOutInSeconds: Long = defaultTimeOutInSeconds)(implicit webDriver: WebDriver): Element = {
      val wait = new WebDriverWait(webDriver, timeOutInSeconds)
      wait until new ExpectedCondition[Element] {
        override def apply(d: WebDriver): Element = {
          find(query).filter(_.isEnabled).orNull
        }
      }
    }

    /**
      * Waits for an element to be enabled, then clicks it.
      *
      * @param query Query to locate the element
      * @param timeOutInSeconds number of seconds to wait for the enabled element
      * @param webDriver implicit WebDriver for the WebDriverWait
      */
    def thenClick(query: Query, timeOutInSeconds: Long = defaultTimeOutInSeconds)(implicit webDriver: WebDriver): Unit = {
      val wait = new WebDriverWait(webDriver, timeOutInSeconds)
      val element: Element = wait until new ExpectedCondition[Element] {
        override def apply(d: WebDriver): Element = {
          find(query).filter(_.isEnabled).orNull
        }
      }
      click on element
    }

    /**
      * Waits for an element containing the given text.
      * TODO: this is currently untested
      *
      * @param text the text to search for
      * @param timeOutInSeconds number of seconds to wait for the text
      * @param webDriver implicit WebDriver for the WebDriverWait
      */
    def text(text: String, timeOutInSeconds: Long = defaultTimeOutInSeconds)(implicit webDriver: WebDriver): Unit = {
      await condition (find(withText(text)).isDefined, timeOutInSeconds)
    }

    /**
      * Waits for an element to appear and then disappear.
      *
      * @param query Query to locate the element
      * @param timeOutInSeconds number of seconds to wait for each change of state
      * @param webDriver implicit WebDriver for the WebDriverWait
      */
    def toggle(query: Query, timeOutInSeconds: Long = defaultTimeOutInSeconds)(implicit webDriver: WebDriver): Unit = {
      val wait = new WebDriverWait(webDriver, timeOutInSeconds)
      wait until new ExpectedCondition[Boolean] {
        override def apply(d: WebDriver): Boolean = {
          find(query).isDefined
        }
      }
      wait until new ExpectedCondition[Boolean] {
        override def apply(d: WebDriver): Boolean = {
          find(query).isEmpty
        }
      }
    }
  }

  def enabled(q: Query)(implicit webDriver: WebDriver): Boolean = {
    find(q).exists(_.isEnabled)
  }

  /**
    * Extension to ScalaTest's Selenium DSL for working with option elements.
    */
  object option {

    /**
      * Determines the value of an option based on its text. Example:
      *
      * <pre>
      * singleSel("choices").value = option value "Choice 1"
      * </pre>
      *
      * @param text text label of the option
      * @param webDriver implicit WebDriver for the WebDriverWait
      * @return the value of the option
      */
    def value(text: String)(implicit webDriver: WebDriver): String = {
      find(xpath(s"//option[text()='$text']")).get.underlying.getAttribute("value")
    }
  }

  /**
    * Creates a Query for an element with a data-test-id attribute.
    *
    * @param id the expected data-test-id
    * @param webDriver implicit WebDriver for the WebDriverWait
    * @return a Query for the data-test-id
    */
  def testId(id: String)(implicit webDriver: WebDriver): Query = {
    cssSelector(s"[data-test-id='$id']")
  }

  def typeSelector(selector: String)(implicit webDriver: WebDriver): Query = {
    cssSelector(s"[type='$selector']")
  }

  def withText(text: String)(implicit webDriver: WebDriver): Query = {
    xpath(s"//*[contains(text(),'$text'")
  }

  /**
    * Creates a query for an element containing the given text.
    * TODO: this is currently untested
    *
    * @param text the text to search for
    * @param webDriver implicit WebDriver for the WebDriverWait
    * @return a Query for the text
    */
  def text(text: String)(implicit webDriver: WebDriver): Query = {
    xpath(s"//*[contains(text(),'$text')]")
  }

  /**
    * Creates a Query for an element with a title attribute.
    *
    * @param title the expected title
    * @param webDriver implicit WebDriver for the WebDriverWait
    * @return a Query for the title
    */
  def title(title: String)(implicit webDriver: WebDriver): Query = {
    cssSelector(s"[title='$title']")
  }
}
