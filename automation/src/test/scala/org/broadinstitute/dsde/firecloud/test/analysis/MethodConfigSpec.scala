package org.broadinstitute.dsde.firecloud.test.analysis

import java.io.{File, PrintWriter}

import org.broadinstitute.dsde.firecloud.fixture.{DownloadFixtures, UserFixtures}
import org.broadinstitute.dsde.firecloud.page.workspaces.WorkspaceDataPage
import org.broadinstitute.dsde.firecloud.page.workspaces.methodconfigs.WorkspaceMethodConfigDetailsPage
import org.broadinstitute.dsde.workbench.auth.AuthToken
import org.broadinstitute.dsde.workbench.config.UserPool
import org.broadinstitute.dsde.workbench.fixture._
import org.broadinstitute.dsde.workbench.service.test.WebBrowserSpec
import org.scalatest.{FreeSpec, Matchers}

import scala.collection.immutable.ListMap
import scala.io.Source

class MethodConfigSpec extends FreeSpec with Matchers with WebBrowserSpec with WorkspaceFixtures with UserFixtures with DownloadFixtures
  with MethodFixtures with BillingFixtures with TestReporterFixture {

  val wdl = """
              |workflow w {
              |  call t
              |}
              |
              |task t {
              |  String inString
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

  val wdlInputs = ListMap(
    "w.t.inString" -> "\"test\"",
    "w.t.inFloat"-> "1.5",
    "w.t.inInt" -> "2",
    "w.t.inBoolean" -> "true",
    "w.t.inFile" -> "\"gs://foo/bar\"",
    "w.t.inStringArray" -> """["foo","bar"]""",
    "w.t.inStringArray2" -> """["say \"hi\"!"]""",
    "w.t.inStringMap" -> """{"foo":"bar"}"""
  )

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
          val configName = s"test_JSON_populate_config_$workspaceName"
          api.methodConfigurations.createMethodConfigInWorkspace(
            projectName, workspaceName, method,
            projectName, configName, method.snapshotId,
            Map.empty, Map.empty, "sample")

          val downloadDir = makeTempDownloadDirectory()

          withWebDriver(downloadDir) { implicit driver =>
            withSignIn(user) { _ =>

              val configPage = new WorkspaceMethodConfigDetailsPage(projectName, workspaceName, projectName, configName).open

              configPage.isEditing shouldBe false
              wdlInputs.keys.foreach(name => configPage.readFieldValue(name) shouldBe "")

              configPage.editMethodConfig(None, None, None, Option(wdlInputs), None)

              val inputsFile = configPage.downloadInputsJson(downloadDir, "inputs.json")
              val inputsList = Source.fromFile(inputsFile).mkString

              inputsList shouldBe """{"w.t.inFloat":1.5,"w.t.inStringArray2":["say \"hi\"!"],"w.t.inStringMap":{"foo":"bar"},"w.t.inString":"test","w.t.inFile":"gs://foo/bar","w.t.inStringArray":["foo","bar"],"w.t.inBoolean":true,"w.t.inInt":2}"""
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
          val configName = s"test_JSON_populate_config_$workspaceName"
          api.methodConfigurations.createMethodConfigInWorkspace(
            projectName, workspaceName, method,
            projectName, configName, method.snapshotId,
            Map.empty, Map.empty, "sample")

          withWebDriver { implicit driver =>
            withSignIn(user) { _ =>
              val unmatchedVariables = ListMap(
                "unmatched.variable.name" -> "\"surprise!\""
              )

              val configPage = new WorkspaceMethodConfigDetailsPage(projectName, workspaceName, projectName, configName).open

              configPage.isEditing shouldBe false
              wdlInputs.keys.foreach(name => configPage.readFieldValue(name) shouldBe "")

              val inputs = wdlInputs ++ unmatchedVariables map {
                case (name, json) => (name, json)
              }
              configPage.populateInputsFromJson(generateInputsJson(inputs))

              configPage.isEditing shouldBe true
              wdlInputs foreach {
                case (name, expected) =>
                  configPage.readFieldValue(name) shouldBe expected
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
    val rows = inputs map { case (k, v) => s""""$k": $v""" }
    val fileContent = s"""{\n  ${rows.mkString(",\n  ")}\n}"""
    writer.write(fileContent)
    writer.close()
    file
  }
}
