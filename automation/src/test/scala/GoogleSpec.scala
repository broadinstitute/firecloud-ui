import org.broadinstitute.dsde.firecloud.test.WebBrowserSpec
import org.scalatest.ParallelTestExecution
import org.scalatest.selenium.WebBrowser
import org.scalatest.FlatSpec

/**
  * Example test for running with Selenium Grid until we work out docker
  * networking to be able to access FiaBitC.
  */
class GoogleSpec extends FlatSpec with WebBrowserSpec with ParallelTestExecution with WebBrowser {

  behavior of "Google"

  it should "have a search field" in withWebDriver { implicit driver =>
    go to new GooglePage()
    assert(find("q").isDefined)
//    Thread sleep 10000
  }

  it should "have a 'Google Search' button" in withWebDriver { implicit driver =>
    go to new GooglePage()
    val button = find(xpath("//input[@value='Google Search'][@type='submit']"))
    assert(button.isDefined)
    //    Thread sleep 10000
  }
}
