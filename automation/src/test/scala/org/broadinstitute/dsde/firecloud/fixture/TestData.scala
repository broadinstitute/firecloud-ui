package org.broadinstitute.dsde.firecloud.fixture

import org.broadinstitute.dsde.workbench.service.Orchestration

case class TestData(prefix: String = "participant") {

  val entityId = Orchestration.randomIdWithPrefix("participant")
  val participantEntity = s"entity:participant_id\n$entityId"
  private val samplePrefix = Orchestration.randomIdWithPrefix("sample")

  def hundreAndOneSet: HundredAndOneSampleSet = {
    new HundredAndOneSampleSet()
  }

  /* SAMPLES AND SAMPLE SETS */
  class HundredAndOneSampleSet() {

    val setEntityId = "sampleSet101"
    val sampleSetCreation = "entity:sample_set_id\n" + "sampleSet101"

    def samples() = createSamples(samplePrefix, entityId, 101)
    def sampleSetMembership() = {
      createSampleSet(setEntityId, samplePrefix, 101)
    }

    private def createSamples(samplePrefix: String, participantId: String, numberOfSamples: Int): String = {
      "entity:sample_id\tparticipant_id\n" +
        (0 until numberOfSamples).map(i => s"$samplePrefix$i\t$participantId\n").mkString.stripSuffix("\n")
    }

    private def createSampleSet(sampleSetName: String, samplePrefix: String, numberOfSamples: Int) = {
      "membership:sample_set_id\tsample_id\n" +
        (0 until numberOfSamples).map(i => s"$sampleSetName\t$samplePrefix$i\n").mkString.stripSuffix("\n")
    }
  }

}