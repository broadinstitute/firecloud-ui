package org.broadinstitute.dsde.firecloud.fixture

import com.typesafe.scalalogging.{CanLog, Logger, LoggerTakingImplicit}
import org.openqa.selenium.WebDriver
import org.openqa.selenium.remote.RemoteWebDriver

/**
  * Write WebDriver SessionId to log for easy troubleshooting
  */
trait WebDriverLogging {
  protected val log: LoggerTakingImplicit[WebDriver] = Logger.takingImplicit(getClass)(WebDriverLogging.CanLogSessionId)
}

object WebDriverLogging {

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
    override def logMessage(originalMsg: String, driver: WebDriver): String = s"(${driver.asInstanceOf[RemoteWebDriver].getSessionId.toString}) $originalMsg"
  }
}