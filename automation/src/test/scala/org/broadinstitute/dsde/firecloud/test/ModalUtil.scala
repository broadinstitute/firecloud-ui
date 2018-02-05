package org.broadinstitute.dsde.firecloud.test

import org.broadinstitute.dsde.firecloud.component._
import org.scalatest.Matchers

/**
  * Utilities for testing Modals
  */
trait ModalUtil { this: Matchers =>
  /**
    * Tests that the modal can be opened, and closed by the "X" button.
    * Starts and ends with the modal closed.
    * @param openModal code that opens the modal
    */
  def testModalClosabilityByX(openModal: => Modal): Unit = {
    val modal = openModal
    modal.isVisible shouldBe true

    modal.xOut()
    modal.isVisible shouldBe false
  }

  /**
    * Tests that the modal can be opened, and closed by the "Cancel" button.
    * Starts and ends with the modal closed.
    * @param openModal code that opens the modal
    */
  def testModalClosabilityByCancel(openModal: => OKCancelModal): Unit = {
    val modal = openModal
    modal.isVisible shouldBe true

    modal.cancel()
    modal.isVisible shouldBe false
  }

  /**
    * Tests that the modal can be opened, and closed in all ways
    * @param openModal code that opens the modal
    */
  def testModal(openModal: => OKCancelModal): Unit = {
    testModalClosabilityByX(openModal)
    testModalClosabilityByCancel(openModal)
  }
}
