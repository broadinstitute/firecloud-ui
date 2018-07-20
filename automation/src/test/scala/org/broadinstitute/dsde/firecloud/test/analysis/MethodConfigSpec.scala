package org.broadinstitute.dsde.firecloud.test.analysis

import java.io.{File, PrintWriter}

import org.broadinstitute.dsde.firecloud.fixture.{DownloadUtil, UserFixtures}
import org.broadinstitute.dsde.firecloud.page.workspaces.WorkspaceDataPage
import org.broadinstitute.dsde.firecloud.page.workspaces.methodconfigs.WorkspaceMethodConfigDetailsPage
import org.broadinstitute.dsde.workbench.auth.AuthToken
import org.broadinstitute.dsde.workbench.config.UserPool
import org.broadinstitute.dsde.workbench.fixture._
import org.broadinstitute.dsde.workbench.service.test.WebBrowserSpec
import org.scalatest.{FreeSpec, Matchers}

import scala.collection.immutable.ListMap
import scala.io.Source

class MethodConfigSpec extends FreeSpec with Matchers with WebBrowserSpec with WorkspaceFixtures with UserFixtures with DownloadUtil
  with MethodFixtures with BillingFixtures with TestReporterFixture {

  val wdl = """
              |workflow w {
              |  call t
              |}
              |
              |task t {
              |  String inString
              |  String inWorkspaceRef
              |  String inThisRef
              |  Float inFloat
              |  Int inInt
              |  Boolean inBoolean
              |  File inFile
              |  Array[String] inStringArray
              |  Array[String] inStringArray2
              |  Map[String,String] inStringMap
              |
              |  command {
              |    echo inString ${inString}
              |    echo inWorkspaceRef ${inWorkspaceRef}
              |    echo inThisRef ${inThisRef}
              |    echo inFloat ${inFloat}
              |    echo inInt ${inInt}
              |    echo inBoolean ${inBoolean}
              |    echo inFile ${inFile}
              |    echo inStringArray ${sep=" " inStringArray}
              |    echo inStringArray2 ${sep=" " inStringArray2}
              |    echo inStringMap ${write_map(inStringMap)}
              |  }
              |  output {
              |    String out = stdout()
              |  }
              |  runtime {
              |    docker: "ubuntu"
              |  }
              |}
              |""".stripMargin

  val wdlInputsBase = ListMap(
    "w.t.inString" -> "\"test\"",
    "w.t.inFloat"-> "1.5",
    "w.t.inInt" -> "2",
    "w.t.inBoolean" -> "true",
    "w.t.inFile" -> "\"gs://foo/bar\"",
    "w.t.inStringArray" -> """["foo","bar"]""",
    "w.t.inStringArray2" -> """["say \"hi\"!"]""",
    "w.t.inStringMap" -> """{"foo":"bar"}"""
  )

  val refInputs = ListMap(
    "w.t.inWorkspaceRef" -> "workspace.hello",
    "w.t.inThisRef" -> "this.hello"
  )

  val refInputsJsonFormat = ListMap(
    "w.t.inWorkspaceRef" -> "$workspace.hello",
    "w.t.inThisRef" -> "$this.hello"
  )

  val unmatchedVariables = ListMap("unmatched.variable.name" -> "\"surprise!\"")


  "input/output auto-suggest" - {
    "stays current with selected root entity type" in {
      val user = UserPool.chooseProjectOwner
      implicit val authToken: AuthToken = user.makeAuthToken()
      withCleanBillingProject(user) { projectName =>
        withWorkspace(projectName, "MethodConfigSpec", attributes = Some(Map("foo" -> "bar"))) { workspaceName =>
          val configName = s"${SimpleMethodConfig.configName}_$workspaceName"
          api.methodConfigurations.createMethodConfigInWorkspace(
            projectName, workspaceName,
            MethodData.SimpleMethod,
            projectName, configName,
            SimpleMethodConfig.snapshotId,
            Map.empty, Map.empty,
            "sample")

          val workspaceSuggestions = Seq("workspace.foo")
          val participantSuggestions = Seq("this.is_a_participant")

          withWebDriver { implicit driver =>
            withSignIn(user) { _ =>
              val configPage = new WorkspaceMethodConfigDetailsPage(projectName, workspaceName, projectName, configName).open
              configPage.isEditing shouldBe false

              configPage.toggleDataModel() shouldBe false
              configPage.isEditing shouldBe true
              configPage.clickAndReadSuggestions("test.hello.name") should contain theSameElementsAs workspaceSuggestions

              configPage.toggleDataModel() shouldBe true
              configPage.readRootEntityTypeSelect shouldBe "sample"
              configPage.clickAndReadSuggestions("test.hello.name") should contain theSameElementsAs workspaceSuggestions

              api.importMetaData(projectName, workspaceName, "entities",
                "entity:participant_id\tis_a_participant\nparticipant1\ttrue")

              // go somewhere else and come back (because reloadPage loses background signed-in state)
              new WorkspaceDataPage(projectName, workspaceName).open
              configPage.open

              configPage.openEditMode()
              configPage.readRootEntityTypeSelect shouldBe "sample"
              configPage.clickAndReadSuggestions("test.hello.name") should contain theSameElementsAs workspaceSuggestions

              configPage.selectRootEntityType("participant")
              configPage.clickAndReadSuggestions("test.hello.name") should contain theSameElementsAs workspaceSuggestions ++ participantSuggestions

              // Unchecking data model should suppress participant suggestions
              configPage.toggleDataModel() shouldBe false
              configPage.clickAndReadSuggestions("test.hello.name") should contain theSameElementsAs workspaceSuggestions

              configPage.toggleDataModel() shouldBe true
              configPage.readRootEntityTypeSelect shouldBe "participant"
              configPage.clickAndReadSuggestions("test.hello.name") should contain theSameElementsAs workspaceSuggestions ++ participantSuggestions
            }
          }
        }
      }
    }
  }

  "Download inputs.json" - {
    "populates connection inputs" in {
      val user = UserPool.chooseProjectOwner
      implicit val authToken: AuthToken = user.makeAuthToken()

      withCleanBillingProject(user) { projectName =>
        // Create a method
        val methodName = uuidWithPrefix("test_JSON_download")
        val method = Method(
          methodName = methodName,
          methodNamespace = "MethodConfigSpec_download",
          snapshotId = 1,
          rootEntityType = "sample",
          synopsis = "",
          documentation = "",
          wdl)
        api.methods.createMethod(method.creationAttributes)
        register cleanUp api.methods.redact(method)

        withWorkspace(projectName, "MethodConfigSpec", attributes = Some(Map("foo" -> "bar"))) { workspaceName =>
          // Create method config
          val configName = s"test_JSON_populate_config_$workspaceName"
          api.methodConfigurations.createMethodConfigInWorkspace(
            projectName, workspaceName, method,
            projectName, configName, method.snapshotId,
            Map.empty, Map.empty, "sample")

          val downloadDir = makeTempDownloadDirectory()

          withWebDriver(downloadDir) { implicit driver =>
            withSignIn(user) { _ =>
              // Go to method config page
              val configPage = new WorkspaceMethodConfigDetailsPage(projectName, workspaceName, projectName, configName).open

              // We should not be in edit mode
              configPage.isEditing shouldBe false

              val inputs = wdlInputsBase ++ refInputs

              // All the input fields should be empty
              inputs.keys.foreach(name => configPage.readFieldValue(name) shouldBe "")

              // Add values to the input fields
              configPage.editMethodConfig(None, None, None, Option(inputs), None)

              // Download the inputs
              val inputsFile = configPage.downloadInputsJson(downloadDir, "inputs.json")

              // Check the downloaded file contains the right values
              val inputsList = Source.fromFile(inputsFile).mkString
              val expected = """{"w.t.inString":"test","w.t.inFloat":1.5,"w.t.inStringArray2":["say \"hi\"!"],"w.t.inThisRef":"$this.hello","w.t.inWorkspaceRef":"$workspace.hello","w.t.inInt":2,"w.t.inStringMap":{"foo":"bar"},"w.t.inBoolean":true,"w.t.inStringArray":["foo","bar"],"w.t.inFile":"gs://foo/bar"}"""
              inputsList shouldBe expected
            }
          }
        }
      }
    }
  }


  "Upload inputs.json" - {
    "populates connection inputs" in {
      val user = UserPool.chooseProjectOwner
      implicit val authToken: AuthToken = user.makeAuthToken()

      // Create a method
      withCleanBillingProject(user) { projectName =>
        val methodName = uuidWithPrefix("test_JSON_populate")
        val method = Method(
          methodName = methodName,
          methodNamespace = "MethodConfigSpec_populate",
          snapshotId = 1,
          rootEntityType = "sample",
          synopsis = "",
          documentation = "",
          wdl)
        api.methods.createMethod(method.creationAttributes)
        register cleanUp api.methods.redact(method)

        withWorkspace(projectName, "MethodConfigSpec", attributes = Some(Map("foo" -> "bar"))) { workspaceName =>
          // Create method config
          val configName = s"test_JSON_populate_config_$workspaceName"
          api.methodConfigurations.createMethodConfigInWorkspace(
            projectName, workspaceName, method,
            projectName, configName, method.snapshotId,
            Map.empty, Map.empty, "sample")

          withWebDriver { implicit driver =>
            withSignIn(user) { _ =>
              // Go to method config page
              val configPage = new WorkspaceMethodConfigDetailsPage(projectName, workspaceName, projectName, configName).open

              // We should not be in edit mode
              configPage.isEditing shouldBe false

              val inputs = wdlInputsBase ++ refInputsJsonFormat

              // All the input fields should be empty
              inputs.keys.foreach(name => configPage.readFieldValue(name) shouldBe "")

              // Populate input fields from a json containing all the field values and some fields that don't exist
              configPage.populateInputsFromJson(generateInputsJson(inputs ++ unmatchedVariables))

              // We should have been automatically switched to edit mode
              configPage.isEditing shouldBe true

              // for each input, (not included the fields that didn't exist), we should have the correct value
              // for referential inputs ($this._ or $workspace._), it should have removed the "$"
              inputs foreach {
                case (name, expected) =>
                  if (refInputsJsonFormat.contains(name))
                    configPage.readFieldValue(name) shouldBe expected.replace("$", "")
                  else configPage.readFieldValue(name) shouldBe expected
              }
            }
          }
        }
      }
    }
  }

  private def generateInputsJson(inputs: Map[String, String]): File = {
    val file = File.createTempFile("MethodConfigSpec_", "_inputs.json")
    val writer = new PrintWriter(file)
    val rows = inputs map { case (k, v) => {
//        if (v.startsWith("$"))
//          s""""$k": "${v.replaceFirst("$", "")}""""
//        else
          s""""$k": $v"""
      }
    }
    val fileContent = s"""{\n  ${rows.mkString(",\n  ")}\n}"""
    writer.write(fileContent)
    writer.close()
    file
  }
}
