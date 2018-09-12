package org.broadinstitute.dsde.firecloud.component

import com.typesafe.scalalogging.LazyLogging
import org.openqa.selenium.{StaleElementReferenceException, WebDriver}

import scala.util.{Failure, Success, Try}

/**
  * Mix in for Components (ex. SearchField or TextField) which supply dropdown autosuggestions
  * based on user input.
  */
trait Suggests extends LazyLogging { this: Component =>

  /**
    * get the values of the suggestions displayed to the user. Requires the test code that calls
    * this function to enter text into/click on the relevant field, in order to activate the
    * suggestions.
    *
    * @param webDriver
    * @return
    */
  def getSuggestions()(implicit webDriver: WebDriver): Seq[String] = {
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

    // wait for dropdown to contain at least one option
    val listOptionXpath = s"//div[@id='$dropdownId']/ul[@role='listbox']/li[@role='option']"
    await condition {
      find(xpath(s"//div[@id='$dropdownId']")).exists(_.isDisplayed) &&
      findAll(xpath(listOptionXpath)).map(_.text).toSeq.nonEmpty // getting Element's text force screen scroll if item is outside of viewport
    }

    // return the value of the options text
    // retry on StaleElementReferenceException
    Try[Seq[String]] {
      findAll(xpath(listOptionXpath)).map(_.text).toSeq
    } match {
      case Success(value) => value
      case Failure(_:StaleElementReferenceException) =>
        Thread sleep 1000
        findAll(xpath(listOptionXpath)).map(_.text).toSeq // retry if encountered StaleElementReferenceException
    }
  }

}
