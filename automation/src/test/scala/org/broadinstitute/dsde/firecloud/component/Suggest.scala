package org.broadinstitute.dsde.firecloud.component

import com.typesafe.scalalogging.LazyLogging
import org.openqa.selenium.support.ui.WebDriverWait
import org.openqa.selenium.{StaleElementReferenceException, WebDriver}


/**
  * Mix in for Components (ex. SearchField or TextField) which supply dropdown autosuggestions
  * based on user input.
  */
trait Suggest extends LazyLogging { this: Component =>

  def getSuggestions(implicit webDriver: WebDriver): Seq[String] = {
    val wait = new WebDriverWait(webDriver, 10)
    wait until new java.util.function.Function[WebDriver, Seq[String]] {
      override def apply(d: WebDriver): Seq[String] = {
        try {
          texts
        } catch {
          case e: StaleElementReferenceException =>
            logger.warn("getSuggestions",e) // log stacktrace to help troubleshooting in case wait time exhausted
            throw e
        }
      }
    }
  }
  /**
    * get the values of the suggestions displayed to the user. Requires the test code that calls
    * this function to enter text into/click on the relevant field, in order to activate the
    * suggestions.
    *
    * @param webDriver
    * @return
    */
  private def texts(implicit webDriver: WebDriver): Seq[String] = {

    // find the DOM element representing the input component
    val uel = query.element.underlying

    // the  autocomplete JavaScript will add either an aria-owns or an aria-controls attribute to the input element.
    // It's unclear which attribute is used under which conditions by the third-party autocomplete JavaScript,
    // so we look for either here.
    //
    // the value of this attribute will be the id of the DOM element (a div) that contains the suggestion elements.
    // see https://www.w3.org/WAI/PF/aria-1.1/states_and_properties#attrs_relationships for info on aria-owns/aria-controls.
    //
    // wait for the aria-owns/aria-controls attribute to exist:
    await condition {
      Option(uel.getAttribute("aria-owns")).nonEmpty ||
        Option(uel.getAttribute("aria-controls")).nonEmpty
    }

    // reduce the value of the "aria-owns" or "aria-controls" attributes - either could be populated - into
    // the DOM id that contains the suggestions.
    val ownedId = Option(uel.getAttribute("aria-owns"))
    val controlledId = Option(uel.getAttribute("aria-controls"))
    val dropdownId = (ownedId ++ controlledId).headOption match {
      case Some(id) => id
      case None => throw new Exception(s"Could not determine dropdownId from aria-owns [$ownedId] : aria-controls [$controlledId]." +
        " Is this input field enabled for suggestions? Has your test activated the suggestions dropdown?")
    }

    // return the value of the options text
    val listOptionXpath = s"//div[@id='$dropdownId']/ul[@role='listbox']/li[@role='option']"
    await condition {
      find(xpath(s"//div[@id='$dropdownId']")).exists(_.isDisplayed) &&
        findAll(xpath(listOptionXpath)).map(_.text).toSeq.nonEmpty
    }
    findAll(xpath(listOptionXpath)).map(_.text).toSeq
  }

}
