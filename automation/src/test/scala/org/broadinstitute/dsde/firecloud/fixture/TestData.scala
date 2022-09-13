package org.broadinstitute.dsde.firecloud.fixture

import org.broadinstitute.dsde.workbench.service.test.RandomUtil

case class TestData(dataPrefix: String = "participant") extends RandomUtil {

  val participantId = randomIdWithPrefix(dataPrefix)
  val participantEntity = s"entity:participant_id\n$participantId"
  private val samplePrefix = randomIdWithPrefix("sample")

  def hundredAndOneSet: HundredAndOneSampleSet = {
    new HundredAndOneSampleSet()
  }

  /* SAMPLES AND SAMPLE SETS */
  class HundredAndOneSampleSet() {

    val sampleSetId = "sampleSet101"
    val sampleSetCreation = "entity:sample_set_id\n" + "sampleSet101"

    lazy val samples = createSamples(samplePrefix, participantId, 101)
    lazy val sampleSetMembership = createSampleSet(sampleSetId, samplePrefix, 101)

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