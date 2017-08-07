import org.openqa.selenium.WebDriver
import org.scalatest.selenium.Page

class GooglePage(implicit webDriver: WebDriver) extends Page {
  override val url: String = "http://google.com/"
}
