package org.broadinstitute.dsde.firecloud.data

object Library {
  val metadata = Map(
     "library:datasetName"->"name",
     "library:datasetVersion"->"v1.0",
     "library:datasetDescription"->"desc",
     "library:datasetCustodian"->"cust",
     "library:datasetDepositor"->"depo",
     "library:contactEmail"->"name@example.com",
     "library:datasetOwner"->"owner",
     "library:institute"->Seq("inst","it","ute"),
     "library:indication"->"indic",
     "library:numSubjects"->123,
     "library:projectName"->"proj",
     "library:datatype"->Seq("data","type"),
     "library:dataCategory"->Seq("data","category"),
     "library:dataUseRestriction"->"dur",
     "library:studyDesign"->"study",
     "library:cellType"->"cell",
     "library:requiresExternalApproval"->false,
     "library:useLimitationOption"->"orsp",
     "library:technology"->Seq("is an optional","array attribute"),
     "library:orsp"->"some orsp",
     "_discoverableByGroups"->Seq("all_broad_users"))

}
