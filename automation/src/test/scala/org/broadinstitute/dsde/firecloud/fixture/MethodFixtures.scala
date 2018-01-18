package org.broadinstitute.dsde.firecloud.fixture

import org.broadinstitute.dsde.workbench.auth.AuthToken
import org.broadinstitute.dsde.workbench.service.test.CleanUp
import org.broadinstitute.dsde.firecloud.test.WebBrowserSpec
import org.broadinstitute.dsde.workbench.service.util.Util.{appendUnderscore, makeUuid}
import org.scalatest.TestSuite

trait MethodFixtures extends CleanUp { self: WebBrowserSpec with TestSuite =>


  def withMethod(testName:String, method:Method, numSnapshots: Int = 1, cleanUp: Boolean = true)
                (testCode: (String) => Any)
                (implicit token: AuthToken): Unit = {
    // create a method
    val methodName: String = appendUnderscore(testName) + makeUuid
    for (i <- 1 to numSnapshots)
      api.methods.createMethod(method.creationAttributes + ("name"->methodName))
    try {
      testCode(methodName)
    } finally {
      if (cleanUp) {
        try {
          for (i <- 1 to numSnapshots)
          api.methods.redact(method.methodNamespace, methodName, i)
        } catch nonFatalAndLog(s"Error redacting method $method.methodName/$methodName")
      }
    }

  }

  def withMethod(methodName: String)
                (testCode: ((String, String)) => Any)
                (implicit token: AuthToken): Unit = {
    val name = methodName + randomUuid
    val attributes = MethodData.SimpleMethod.creationAttributes + ("name" -> name)
    val namespace = attributes("namespace")
    api.methods.createMethod(attributes)

    try {
      testCode((name, namespace))
    } finally {

    }

  }
}
