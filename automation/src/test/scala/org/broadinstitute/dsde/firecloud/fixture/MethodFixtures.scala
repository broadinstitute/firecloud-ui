package org.broadinstitute.dsde.firecloud.fixture

import org.broadinstitute.dsde.firecloud.config.AuthToken
import org.broadinstitute.dsde.firecloud.fixture.MethodData.{SimpleMethod, SimpleMethodConfig}
import org.broadinstitute.dsde.firecloud.test.{CleanUp, WebBrowserSpec}
import org.broadinstitute.dsde.firecloud.util.Util.{appendUnderscore, makeUuid}
import org.scalatest.Suite

trait MethodFixtures extends CleanUp { self: WebBrowserSpec with Suite =>


  def withConfigForRedactedMethodInWorkspace(testname:String, wsnamespace: String, wsname: String, withUnredactedSnapshots: Boolean)
                                            (testCode: (String) => Any)
                                            (implicit token: AuthToken): Unit = {
    // create a method
    val methodName = appendUnderscore(testname) + makeUuid
    val configName = methodName + "Config"
    api.methods.createMethod(SimpleMethod.creationAttributes + ("name"->methodName))
    if (withUnredactedSnapshots)
      api.methods.createMethod(SimpleMethod.creationAttributes + ("name"->methodName))

    // create a config for workspace
    api.methodConfigurations.createMethodConfigInWorkspace(wsnamespace, wsname, 1, SimpleMethod.methodNamespace, methodName, 1, SimpleMethodConfig.configNamespace, configName,
      SimpleMethodConfig.inputs, SimpleMethodConfig.outputs, "participant")

    // redact the method
    api.methods.redact(SimpleMethod.methodNamespace, methodName, 1)

    try {
      testCode(configName)
    } finally {
      try {
        if (withUnredactedSnapshots)
          api.methods.redact(SimpleMethod.methodNamespace, methodName, 2)
      } catch nonFatalAndLog(s"Error redacting method in withConfigForRedactedMethodInWorkspace clean-up: ${SimpleMethod.methodNamespace}/$methodName")
    }
  }
}
