package org.broadinstitute.dsde.firecloud.fixture

import org.broadinstitute.dsde.firecloud.config.AuthToken
import org.broadinstitute.dsde.firecloud.test.{CleanUp, WebBrowserSpec}
import org.broadinstitute.dsde.firecloud.util.Util.{appendUnderscore, makeUuid}
import org.scalatest.Suite

trait MethodFixtures extends CleanUp { self: WebBrowserSpec with Suite =>


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

}
