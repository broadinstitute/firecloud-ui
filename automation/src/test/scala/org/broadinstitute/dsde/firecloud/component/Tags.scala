package org.broadinstitute.dsde.firecloud.component

import org.openqa.selenium.{By, WebDriver}

case class Tags (queryString: QueryString)(implicit webDriver: WebDriver) extends Component(queryString) {

  def getTags: Seq[String] = {
    val elm = find(query)
    if (elm.isDefined)
      elm.get.attribute("title").get.split(",").toSeq.map(_.trim)
    else
      Seq.empty
  }
}