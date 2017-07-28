package org.broadinstitute.dsde.firecloud.fixture

object TestData {

  /* PARTICIPANTS AND PARTICIPANT SETS*/
  object SingleParticipant {
    val participantEntity = "entity:participant_id\nparticipant1"
    val entityId = "participant1"
  }


  /* SAMPLES AND SAMPLE SETS */
  object HundredAndOneSampleSet {
    val entityId = "sampleSet101"
    val sampleSetCreation = "entity:sample_set_id\n" +
      "sampleSet101"
    private val samplePrefix = "sample"
    val participantId = SingleParticipant.entityId
    val samples = createSamples(samplePrefix, participantId, 101)
    val sampleSetMembership = createSampleSet(entityId, samplePrefix, 101)
  }

  private def createSamples(samplePrefix: String, participantId: String, numberOfSamples: Int): String = {
    "entity:sample_id\tparticipant_id\n" +
      (0 until numberOfSamples).map(i => s"$samplePrefix$i\t$participantId\n").mkString.stripSuffix("\n")

  }

  private def createSampleSet(sampleSetName: String, samplePrefix: String, numberOfSamples: Int) = {
    "membership:sample_set_id\tsample_id\n" +
      (0 until numberOfSamples).map(i => s"$sampleSetName\t$samplePrefix$i\n").mkString.stripSuffix("\n")
  }


  /* METHODS */
  object SimpleMethod {
    val name = "test_method"
    val namespace = "qamethods"
    val snapshotId = 1
    val rootEntityType = "participant"
    val inputs = Map("test.hello.name" -> "\"a\"",
      "test.hello.response" -> "workspace.result")
  }
  object SimpleMethodConfig {
    val name = "test1_config"
    val namespace = "qamethods"
    val snapshotId = 1
    val rootEntityType = "participant"
    val inputs = Map("test.hello.name" -> "\"a\"", // shouldn't be needed for config
      "test.hello.response" -> "workspace.result")
  }

  object InputRequiredMethodConfig {
    val name = "test_method_input_required"
    val namespace = "qamethods"
    val snapshotId = 1
    val rootEntityType = "participant"
    val inputs = Map("test.hello.name" -> "\"a\"",
      "test.hello.response" -> "workspace.result")
  }

}
