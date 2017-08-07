package org.broadinstitute.dsde.firecloud.api


case class APIException (message: String = null, cause: Throwable = null) extends Exception(message, cause)
