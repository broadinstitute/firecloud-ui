package org.broadinstitute.dsde.firecloud.component

import org.openqa.selenium.WebDriver

/**
  * Component modeling links (anchor tags), typically created from links.cljs
  *
  * @param queryString the QueryString object representing the root element of the component
  * @param webDriver webdriver
  */
case class Link(queryString: QueryString)(implicit webDriver: WebDriver)
  extends Component(queryString) with Clickable
