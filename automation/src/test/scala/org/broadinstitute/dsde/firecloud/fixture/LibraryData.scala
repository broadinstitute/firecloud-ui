package org.broadinstitute.dsde.firecloud.fixture

object LibraryData {
  val metadata = Map(
    "library:datasetName" -> "name",
    "library:datasetVersion" -> "v1.0",
    "library:datasetDescription" -> "desc",
    "library:datasetCustodian" -> "cust",
    "library:datasetDepositor" -> "depo",
    "library:contactEmail" -> "name@example.com",
    "library:datasetOwner" -> "owner",
    "library:institute" -> Seq("inst", "it", "ute"),
    "library:indication" -> "indic",
    "library:numSubjects" -> 123,
    "library:projectName" -> "proj",
    "library:datatype" -> Seq("data", "type"),
    "library:dataCategory" -> Seq("data", "category"),
    "library:dataUseRestriction" -> "dur",
    "library:useLimitationOption" -> "skip",
    "library:studyDesign" -> "study",
    "library:cellType" -> "cell",
    "library:requiresExternalApproval" -> false,
    "library:technology" -> Seq("is an optional", "array attribute"),
    "_discoverableByGroups" -> Seq("all_broad_users"))

  val consentCodes = Map(
    "library:useLimitationOption" -> "questionnaire",
    "library:HMB" -> true,
    "library:GRU" -> false,
    "library:NCU" -> true,
    "library:NPU" -> true,
    "library:NCTRL" -> false,
    "library:NMDS" -> true,
    "library:RS-PD" -> false,
    "library:IRB" -> false,
    "library:RS-G" -> "N/A",
    "library:NAGR" -> "No")

}