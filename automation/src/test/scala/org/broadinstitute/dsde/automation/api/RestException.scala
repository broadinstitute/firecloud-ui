package org.broadinstitute.dsde.automation.api

case class RestException(message: String = null, cause: Throwable = null) extends Exception(message, cause)
