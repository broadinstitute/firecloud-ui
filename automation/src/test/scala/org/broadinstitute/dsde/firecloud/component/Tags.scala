package org.broadinstitute.dsde.firecloud.component

import org.openqa.selenium.{By, WebDriver}

case class Tags (queryString: QueryString)(implicit webDriver: WebDriver) extends Component(queryString) {

  def getTags: List[String] = {
    import scala.collection.JavaConversions._
    val elm = find(query)
    if (elm.isDefined)
      elm.get.underlying.findElements(By.tagName("div")).toList.map(_.getText)
    else
      List.empty
  }
}