package org.broadinstitute.dsde.firecloud.pages

import org.broadinstitute.dsde.firecloud.PageUtil
import org.openqa.selenium.WebDriver

class ErrorModal (implicit webDriver: WebDriver) extends FireCloudView {

  def validateLocation: Boolean = {
    testId("push-error").element != null
  }

}
