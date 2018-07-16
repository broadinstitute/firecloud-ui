package org.broadinstitute.dsde.firecloud.test.analysis

import java.io.{File, PrintWriter}
import java.nio.file.Files
import java.nio.file.attribute.PosixFilePermission

import org.broadinstitute.dsde.firecloud.fixture.UserFixtures
import org.broadinstitute.dsde.firecloud.page.workspaces.WorkspaceDataPage
import org.broadinstitute.dsde.firecloud.page.workspaces.methodconfigs.WorkspaceMethodConfigDetailsPage
import org.broadinstitute.dsde.workbench.auth.AuthToken
import org.broadinstitute.dsde.workbench.config.UserPool
import org.broadinstitute.dsde.workbench.fixture._
import org.broadinstitute.dsde.workbench.service.test.WebBrowserSpec
import org.scalatest.{FreeSpec, Matchers, ParallelTestExecution}

import scala.collection.immutable.ListMap

class MethodConfigSpec extends FreeSpec with Matchers with WebBrowserSpec with WorkspaceFixtures with UserFixtures with MethodFixtures with BillingFixtures {


  private def makeTempDownloadDirectory(): String = {
    /*
     * This might work some day if docker permissions get straightened out... or it might not be
     * needed. For now, we instead `chmod 777` the directory in run-tests.sh.
    new File("chrome").mkdirs()
    val downloadPath = Files.createTempDirectory(Paths.get("chrome"), "downloads")
    val permissions = Set(PosixFilePermission.OWNER_WRITE, PosixFilePermission.GROUP_WRITE, PosixFilePermission.OTHERS_WRITE)
    Files.setPosixFilePermissions(downloadPath, permissions.asJava)
    downloadPath.toString
     */

    val downloadPath = s"chrome/downloads/${makeRandomId(5)}"
    val dir = new File(downloadPath)
    dir.deleteOnExit()
    dir.mkdirs()
    val path = dir.toPath
    logger.info(s"mkdir: $path")
    val permissions = Set(
      PosixFilePermission.OWNER_WRITE, PosixFilePermission.OWNER_READ, PosixFilePermission.OWNER_EXECUTE,
      PosixFilePermission.GROUP_WRITE, PosixFilePermission.GROUP_READ, PosixFilePermission.GROUP_EXECUTE,
      PosixFilePermission.OTHERS_WRITE, PosixFilePermission.OTHERS_READ, PosixFilePermission.OTHERS_EXECUTE)
    import scala.collection.JavaConverters._
    Files.setPosixFilePermissions(path, permissions.asJava)
    path.toString
  }

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
        println("in billing project")
        val methodName = uuidWithPrefix("test_JSON_download")
        val method = Method(
          methodName = methodName,
          methodNamespace = "MethodConfigSpec",
          snapshotId = 1,
          rootEntityType = "sample",
          synopsis = "",
          documentation = "",
          payload = """
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
                      |""".stripMargin)
        api.methods.createMethod(method.creationAttributes)
        println("created Method")
        register cleanUp api.methods.redact(method)
        println("registered a method")
        withWorkspace(projectName, "MethodConfigSpec", attributes = Some(Map("foo" -> "bar"))) { workspaceName =>
          println("in a workspace")
          val configName = s"test_JSON_populate_config_$workspaceName"
          api.methodConfigurations.createMethodConfigInWorkspace(
            projectName, workspaceName, method,
            projectName, configName, method.snapshotId,
            Map.empty, Map.empty, "sample")
          println("created a method config")

          val downloadDir = makeTempDownloadDirectory()

          withWebDriver(downloadDir) { implicit driver =>
            println("with web driver")
            withSignIn(user) { _ =>
              println("with sign in")
              val variables = ListMap(
                "w.t.inString" -> "\"test\"",
                "w.t.inFloat"-> "1.5",
                "w.t.inInt" -> "2",
                "w.t.inBoolean" -> "true",
                "w.t.inFile" -> "\"gs://foo/bar\"",
                "w.t.inStringArray" -> """["foo","bar"]""",
                "w.t.inStringArray2" -> """["say \"hi\"!"]""",
                "w.t.inStringMap" -> """{"foo":"bar"}"""
              )
              val unmatchedVariables = ListMap(
                "unmatched.variable.name" -> "\"surprise!\""
              )

              val configPage = new WorkspaceMethodConfigDetailsPage(projectName, workspaceName, projectName, configName).open
              println("created a config page")

              configPage.isEditing shouldBe false
              variables.keys.foreach(name => configPage.readFieldValue(name) shouldBe "")

              val inputs = variables ++ unmatchedVariables map {
                case (name, json) => (name, json)
              }
              val inputsFile = configPage.downloadInputsJson(Option(downloadDir)).get
              println("INPUTS FILE " + inputsFile)

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
          methodNamespace = "MethodConfigSpec",
          snapshotId = 1,
          rootEntityType = "sample",
          synopsis = "",
          documentation = "",
          payload = """
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
                      |""".stripMargin)
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
              val variables = ListMap(
                "w.t.inString" -> "\"test\"",
                "w.t.inFloat"-> "1.5",
                "w.t.inInt" -> "2",
                "w.t.inBoolean" -> "true",
                "w.t.inFile" -> "\"gs://foo/bar\"",
                "w.t.inStringArray" -> """["foo","bar"]""",
                "w.t.inStringArray2" -> """["say \"hi\"!"]""",
                "w.t.inStringMap" -> """{"foo":"bar"}"""
              )
              val unmatchedVariables = ListMap(
                "unmatched.variable.name" -> "\"surprise!\""
              )

              val configPage = new WorkspaceMethodConfigDetailsPage(projectName, workspaceName, projectName, configName).open

              configPage.isEditing shouldBe false
              variables.keys.foreach(name => configPage.readFieldValue(name) shouldBe "")

              val inputs = variables ++ unmatchedVariables map {
                case (name, json) => (name, json)
              }
              configPage.populateInputsFromJson(generateInputsJson(inputs))

              configPage.isEditing shouldBe true
              variables foreach {
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
