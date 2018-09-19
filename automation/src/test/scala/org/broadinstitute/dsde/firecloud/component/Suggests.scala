package org.broadinstitute.dsde.firecloud.component

import java.time.Duration

import com.typesafe.scalalogging.LazyLogging
import org.openqa.selenium.support.ui.FluentWait
import org.openqa.selenium.{StaleElementReferenceException, TimeoutException, WebDriver}


/**
  * Mix in for Components (ex. SearchField or TextField) which supply dropdown autosuggestions
  * based on user input.
  */
trait Suggests extends LazyLogging { this: Component =>

  def getSuggestions()(implicit webDriver: WebDriver): Seq[String] = {
    val wait = new FluentWait[WebDriver](webDriver)
        .withTimeout(Duration.ofSeconds(30))
        .pollingEvery(Duration.ofMillis(600))
        .withMessage("Reading autoSuggestions")
        .ignoring(classOf[StaleElementReferenceException])
        .ignoring(classOf[TimeoutException])
    wait until ((driver: WebDriver) => readSuggestionText )
  }

  /**
    * get the values of the suggestions displayed to the user. Requires the test code that calls
    * this function to enter text into/click on the relevant field, in order to activate the
    * suggestions.
    *
    * @param webDriver
    * @return
    */
  private def readSuggestionText(implicit webDriver: WebDriver): Seq[String] = {
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
    logger.info(s"(remove) autosuggestion: check aria-owns or aria-controls is nonEmpty")
    await condition {
      Option(uel.getAttribute("aria-owns")).nonEmpty ||
      Option(uel.getAttribute("aria-controls")).nonEmpty
    }

    // reduce the value of the "aria-owns" or "aria-controls" attributes - either could be populated - into
    // the DOM id that contains the suggestions.
    logger.info(s"(remove) autosuggestion: determine dropdownId")
    val ownedId = Option(uel.getAttribute("aria-owns"))
    val controlledId = Option(uel.getAttribute("aria-controls"))
    val dropdownId = (ownedId ++ controlledId).headOption match {
      case Some(id) => id
      case None => throw new Exception(s"Could not determine dropdownId from aria-owns [$ownedId] : aria-controls [$controlledId]." +
        " Is this input field enabled for suggestions? Has your test activated the suggestions dropdown?")
    }

    // wait for dropdown contains at least one option and every option text is visible
    val listOptionXpath = s"#$dropdownId li"
    logger.info(s"(remove) autosuggestion: dropdownId: $dropdownId")
    await condition {
      val options = findAll(cssSelector(listOptionXpath))
      val displayed = options.forall {_.isDisplayed}
      logger.info(s"(remove) autosuggestion: texts displayed: $displayed")
      displayed
    }

    // return the value of the options text
    val li = findAll(cssSelector(listOptionXpath)).map(_.text).toSeq
    logger.info(s"(remove) autosuggestion: dropdown list contains ${li.size} li: $li")
    li
  }

}
