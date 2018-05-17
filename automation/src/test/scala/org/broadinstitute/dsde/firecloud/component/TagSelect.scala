package org.broadinstitute.dsde.firecloud.component

import org.broadinstitute.dsde.firecloud.Stateful
import org.openqa.selenium.WebDriver

case class TagSelect(queryString: QueryString)(implicit webDriver: WebDriver)
  extends Component(queryString) with Stateful {

   val tagsSearchField = SearchField(CSSQuery("[data-test-id='library-tags-select'] ~ * input.select2-search__field"))
   val tagsClear = Link(TestId("tag-clear"))

  def getTags: Seq[String] = {
    val elm = find(query)
    if (elm.isDefined)
      elm.get.attribute("title").get.split(",").toSeq.map(_.trim)
    else
      Seq.empty
  }

  def doSearch(tags: String*): Unit = {
    clearTag()
    tags.foreach { tag =>
      tagsSearchField.setText(tag)
      // select tag from Select2 dropdown. don't use key 'Enter'
      val allOptions = findSelectResult()
      allOptions.find(_.text == tag).foreach(click on _)
    }
  }

  /**
    * clear Tags input field
    */
  def clearTag(): Unit = {
    tagsClear.doClick()
    awaitReady()
  }

  def findSelectResult(): Iterator[Element] = {
    // css selector is used b/c it's difficult to insert 'data-test-id' in Select2 (3rd party-tool)
    val option = ".select2-container--open ul.select2-results__options li"
    val query: CssSelectorQuery = CssSelectorQuery(option)
    await visible query
    findAll(query)
  }

  def readSelectResultText(): List[String] = {
    findSelectResult().map {_.text}.toList
  }

}