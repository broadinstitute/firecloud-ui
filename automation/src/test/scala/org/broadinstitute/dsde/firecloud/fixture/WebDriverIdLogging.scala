package org.broadinstitute.dsde.firecloud.fixture

import com.typesafe.scalalogging.{CanLog, Logger, LoggerTakingImplicit}
import org.openqa.selenium.WebDriver
import org.openqa.selenium.remote.RemoteWebDriver

/**
  * Write WebDriver SessionId to log for easy troubleshooting
  */
trait WebDriverIdLogging {
  protected val log: LoggerTakingImplicit[WebDriver] = Logger.takingImplicit(getClass)(WebDriverIdLogging.CanLogSessionId)
}

object WebDriverIdLogging {

  /*
  final class SessionId()(implicit driver: WebDriver) {
    def id: String = {
      driver.asInstanceOf[RemoteWebDriver].getSessionId.toString
    }
  }

  object SessionId {
    implicit def apply(implicit driver: WebDriver): SessionId = new SessionId()(driver)
  }
  */

  implicit case object CanLogSessionId extends CanLog[WebDriver] {
    override def logMessage(originalMsg: String, driver: WebDriver): String = s"$originalMsg - [${driver.asInstanceOf[RemoteWebDriver].getSessionId.toString}]"
  }
}