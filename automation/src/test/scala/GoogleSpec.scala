import org.broadinstitute.dsde.firecloud.test.WebBrowserSpec
import org.scalatest.{FlatSpec, Matchers, ParallelTestExecution}
import org.scalatest.selenium.WebBrowser

/**
  * Example test for running with Selenium Grid until we work out docker
  * networking to be able to access FiaBitC.
  */
class GoogleSpec extends FlatSpec with Matchers with WebBrowserSpec with ParallelTestExecution with WebBrowser {

  behavior of "Google"

  it should "have a search field" in withWebDriver { implicit driver =>
    go to new GooglePage()
    assert(find("q").isDefined)
    find("q") shouldBe 'defined
  }

  it should "have a 'Google Search' button" in withWebDriver { implicit driver =>
    go to new GooglePage()
    val button = find(xpath("//input[@value='Google Search'][@type='submit']"))
    button shouldBe 'defined
  }
}
