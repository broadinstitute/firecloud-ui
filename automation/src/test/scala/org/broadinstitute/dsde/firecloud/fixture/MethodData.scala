package org.broadinstitute.dsde.firecloud.fixture

object MethodData {

  /* METHODS */
  object SimpleMethod {
    val methodName = "test_method"
    val methodNamespace = "qamethods"
    val snapshotId = 1
    val rootEntityType = "participant"
    val creationAttributes = Map(
      "namespace"->methodNamespace,
      "name"->methodName,
      "synopsis"->"testtestsynopsis",
      "documentation"->"",
      "payload"->"task hello {\n  String? name\n\n  command {\n    echo 'hello ${name}!'\n  }\n  output {\n    File response = stdout()\n  }\n  runtime {\n    docker: \"ubuntu\"\n  }\n}\n\nworkflow test {\n  call hello\n}",
      "entityType"->"Workflow"
    )
  }

  object InputRequiredMethod {
    val methodName = "test_method_input_required"
    val methodNamespace = "qamethods"
    val snapshotId = 1
    val rootEntityType = "participant"
    val creationAttributes = Map(
      "namespace"->methodNamespace,
      "name"->methodName,
      "synopsis"->"method with required inputs for testing",
      "documentation"->"",
      "entityType"->"Workflow")
  }

  /* CONFIGS */
  object SimpleMethodConfig {
    val configName = "test1_config"
    val configNamespace = "qamethods"
    val snapshotId = 1
    val rootEntityType = "participant"
    val inputs = Map("test.hello.name" -> "\"a\"", // shouldn't be needed for config
      "test.hello.response" -> "workspace.result")
  }

  object InputRequiredMethodConfig {
    val inputs = Map("test.hello.name" -> "\"a\"",
      "test.hello.response" -> "workspace.result")
  }

}
