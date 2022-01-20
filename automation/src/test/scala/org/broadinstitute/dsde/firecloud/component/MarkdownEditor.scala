package org.broadinstitute.dsde.firecloud.component

import org.openqa.selenium.WebDriver

case class MarkdownEditor(queryString: QueryString)(implicit webDriver: WebDriver)
  extends Component(queryString) {

  val editTab = Link("edit-tab" inside this)
  val previewTab = Link("preview-tab" inside this)
  val sideBySideTab = Link("side-by-side-tab" inside this)

  val textArea = TextArea("markdown-editor-text-area" inside this)

  def setText(text: String): Unit = {
    editTab.doClick()
    textArea.setText(text)
  }
}
