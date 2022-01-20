package org.broadinstitute.dsde.firecloud.page.methodrepo

import org.broadinstitute.dsde.firecloud.page.MethodTable
import org.openqa.selenium.WebDriver


class MethodRepoTable(implicit webDriver: WebDriver) extends MethodTable[MethodDetailPage] {
  override protected def awaitInnerPage(namespace: String, name: String): MethodDetailPage = {
    await ready new MethodDetailPage(namespace, name)
  }
}
