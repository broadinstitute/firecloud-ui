package org.broadinstitute.dsde.firecloud.data


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
      new Array[String](numberOfSamples).zipWithIndex.map { el => samplePrefix + el._2 + "\t" + participantId + "\n" }.mkString.stripSuffix("\n")

  }

  private def createSampleSet(sampleSetName: String, samplePrefix: String, numberOfSamples: Int) = {
    "membership:sample_set_id\tsample_id\n" +
      new Array[String](numberOfSamples).zipWithIndex.map { el => sampleSetName + "\t" + samplePrefix + el._2 + "\n"}.mkString.stripSuffix("\n")
  }


  /* METHODS */
  object SimpleMethodConfig {
    val name = "test_method"
    val namespace = "qamethods"
    val snapshotId = 1
    val rootEntityType = "participant"
    val inputs = Map("test.hello.name" -> "\"a\"",
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
