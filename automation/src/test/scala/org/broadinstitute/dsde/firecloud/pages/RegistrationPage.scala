package org.broadinstitute.dsde.firecloud.pages

import org.openqa.selenium.WebDriver

/**
  * Page class for the registration page.
  * TODO: Refactor this for reuse with a profile edit page.
  */
class RegistrationPage(implicit webDriver: WebDriver) extends FireCloudView {

  /**
    * Fills in and submits the new user registration form. Returns as the browser is being redirected to its post-
    * registration destination.
    */
  def register(firstName: String, lastName: String, title: String, contactEmail: String = "",
               institute: String, institutionalProgram: String, nonProfitStatus: Boolean,
               principalInvestigator: String, city: String, state: String,
               country: String): Unit = {
    gestures.fillFirstName(firstName)
    gestures.fillLastName(lastName)
    gestures.fillTitle(title)
    gestures.fillContactEmail(contactEmail)
    gestures.fillInstitute(institute)
    gestures.fillInstitutionalProgram(institutionalProgram)
    gestures.selectNonProfitStatus(nonProfitStatus)
    gestures.fillPrincipalInvestigator(principalInvestigator)
    gestures.fillProgramLocationCity(city)
    gestures.fillProgramLocationState(state)
    gestures.fillProgramLocationCountry(country)
    gestures.clickRegisterButton()
    waitUntilRegistrationComplete()
  }

  /**
    * Waits for the registration page indicates that registration is complete.
    * This is intended to be called after clicking the "Register" button.
    */
  def waitUntilRegistrationComplete(): Unit = {
    await toggle text("Profile Saved")
  }

  object gestures {

    private val contactEmailInput = testId("contactEmail")
    private val firstNameInput = testId("firstName")
    private val instituteInput = testId("institute")
    private val institutionalProgramInput = testId("institutionalProgram")
    private val lastNameInput = testId("lastName")
    private val nonProfitStatusRadioInput = testId("nonProfitStatus")
    private val principalInvestigatorInput = testId("pi")
    private val programLocationCityInput = testId("programLocationCity")
    private val programLocationCountryInput = testId("programLocationCountry")
    private val programLocationStateInput = testId("programLocationState")
    private val registerButton = testId("register-button")
    private val titleInput = testId("title")

    def clickRegisterButton(): Unit = {
      click on registerButton
    }

    def fillContactEmail(email: String): Unit = {
      textField(contactEmailInput).value = email
    }

    def fillFirstName(firstName: String): Unit = {
      await enabled firstNameInput
      textField(firstNameInput).value = firstName
    }

    def fillInstitute(institute: String): Unit = {
      textField(instituteInput).value = institute
    }

    def fillInstitutionalProgram(institutionalProgram: String): Unit = {
      textField(institutionalProgramInput).value = institutionalProgram
    }

    def fillLastName(lastName: String): Unit = {
      textField(lastNameInput).value = lastName
    }

    def fillPrincipalInvestigator(principalInvestigator: String): Unit = {
      textField(principalInvestigatorInput).value = principalInvestigator
    }

    def fillProgramLocationCity(city: String): Unit = {
      textField(programLocationCityInput).value = city
    }

    def fillProgramLocationCountry(country: String): Unit = {
      textField(programLocationCountryInput).value = country
    }

    def fillProgramLocationState(state: String): Unit = {
      textField(programLocationStateInput).value = state
    }

    def fillTitle(title: String): Unit = {
      textField(titleInput).value = title
    }

    def selectNonProfitStatus(nonProfit: Boolean): Unit = {
      radioButtonGroup("nonProfitStatus").value = nonProfit match {
        case false => "Non-Profit"
        case true => "Profit"
      }
    }
  }
}
