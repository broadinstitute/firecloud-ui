package org.broadinstitute.dsde.firecloud.fixture

object LibraryData {

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

  val metadataBasic = Map(
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
     "library:useLimitationOption"-> "skip",
     "library:technology"->Seq("is an optional","array attribute"),
     "_discoverableByGroups"->Seq("all_broad_users"))

   val metadataORSP = metadataBasic ++ Map(
      "library:useLimitationOption"->"orsp",
      "library:orsp"->"some orsp")

   val metadatabrca = Map (
      "library:requiresExternalApproval" ->  true,
      "library:studyDesign" ->  "Tumor/Normal",
      "library:cohortCountry" ->  "USA",
      "library:indication" ->  "Breast Invasive Carcinoma",
      "library:contactEmail" ->  "birger@broadinstitute.org",
      "library:numSubjects" ->  1098,
      "library:datasetOwner" ->  "NCI",
      "library:datatype" ->  Seq(
      "Whole Exome",
      "Genotyping Array",
      "RNA-Seq",
      "miRNA-Seq",
      "Methylation Array",
      "Protein Expression Array"
      ),
      "library:primaryDiseaseSite" ->  "Breast",
      "library:datasetCustodian" ->  "dbGAP",
      "library:projectName" ->  "TCGA",
      "library:cellType" ->  "Primary tumor cell, Whole blood",
      "library:institute" ->  Seq(
      "NCI"
      ),
      "library:orsp" ->  "TODO",
      "library:dataUseRestriction" ->  "General Research Use",
      "library:datasetDepositor" ->  "Chet Birger",
      "library:reference" ->  "GRCh37/hg19",
      "library:datasetVersion" ->  "V1-0_data",
      "library:datasetName" -> "name",
      "library:dataCategory" ->  Seq(
      "Simple Nucleotide Variation",
      "Raw Sequencing data",
      "Copy Number Variation",
      "Expression Quantification",
      "DNA-Methylation",
      "Clinical phenotypes",
      "Biosample metadata"
      ),
      "library:dataFileFormats" ->  Seq(
      "TXT",
      "MAF",
      "BAM"
      ),
      "library:useLimitationOption" ->  "skip",
      "library:datasetDescription" ->  "This cohort is part of The Cancer Genome Atlas project (https://cancergenome.nih.gov/abouttcga/overview).  This cohort includes raw data and analysis of cancer patients samples by genomic DNA copy number arrays, DNA methylation, exome sequencing, mRNA arrays, microRNA sequencing and reverse phase protein arrays. De-identified patients’ clinical phenotypes and metadata are also included. For more information see the full TCGA cohorts publication list at: https://cancergenome.nih.gov/publications; Data description is also summarized at : https://TCGA_data.nci.nih.gov/docs/publications//tcga/datatype.html"
   )

}


