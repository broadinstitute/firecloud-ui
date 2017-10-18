package org.broadinstitute.dsde.firecloud.page.user

import org.broadinstitute.dsde.firecloud.FireCloudView
import org.broadinstitute.dsde.firecloud.component.{Button, TextField}
import org.openqa.selenium.WebDriver

/**
  * Page class for the registration page.
  * TODO: Refactor this for reuse with a profile edit page.
  */
class RegistrationPage(implicit webDriver: WebDriver) extends FireCloudView {

  override def awaitReady(): Unit = await text "User Info"

  private val contactEmailInput = TextField("contactEmail")
  private val firstNameInput = TextField("firstName")
  private val lastNameInput = TextField("lastName")
  private val instituteInput = TextField("institute")
  private val institutionalProgramInput = TextField("institutionalProgram")
  private val principalInvestigatorInput = TextField("pi")
  private val programLocationCityInput = TextField("programLocationCity")
  private val programLocationCountryInput = TextField("programLocationCountry")
  private val programLocationStateInput = TextField("programLocationState")
  private val registerButton = Button("register-button")
  private val registrationCompleteMessage = withText("Profile saved")
  private val titleInput = TextField("title")

  /**
    * Fills in and submits the new user registration form. Returns as the browser is being redirected to its post-
    * registration destination.
    */
  def register(firstName: String, lastName: String, title: String,
               contactEmail: Option[String] = None, institute: String,
               institutionalProgram: String, nonProfitStatus: Boolean,
               principalInvestigator: String, city: String, state: String,
               country: String): Unit = {
    firstNameInput.setText(firstName)
    lastNameInput.setText(lastName)
    titleInput.setText(title)
    contactEmail.foreach(contactEmailInput.setText)
    instituteInput.setText(institute)
    institutionalProgramInput.setText(institutionalProgram)
    radioButtonGroup("nonProfitStatus").value = if (nonProfitStatus) "Profit" else "Non-Profit"
    principalInvestigatorInput.setText(principalInvestigator)
    programLocationCityInput.setText(city)
    programLocationStateInput.setText(state)
    programLocationCountryInput.setText(country)

    registerButton.doClick()
    await condition registrationCompleteMessageIsPresent()
    await condition !registrationCompleteMessageIsPresent()
  }

  private def registrationCompleteMessageIsPresent(): Boolean = {
    find(registrationCompleteMessage).isDefined
  }
}
