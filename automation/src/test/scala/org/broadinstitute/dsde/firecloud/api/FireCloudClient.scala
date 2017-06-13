package org.broadinstitute.dsde.firecloud.api

import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.http.scaladsl.model.HttpMethods._
import akka.http.scaladsl.model.headers.{Authorization, OAuth2BearerToken}
import akka.http.scaladsl.model.{Multipart, _}
import akka.stream.ActorMaterializer
import akka.stream.scaladsl.{Sink, _}
import akka.util.ByteString
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.scala.DefaultScalaModule
import org.broadinstitute.dsde.firecloud.auth.AuthToken

import scala.concurrent.duration._
import scala.concurrent.{Await, ExecutionContext, Future}

class FireCloudClient {
  implicit val system = ActorSystem()
  implicit val materializer = ActorMaterializer()
  implicit val ec = ExecutionContext.global

  val mapper = new ObjectMapper()
  mapper.registerModule(DefaultScalaModule)

  private def makeAuthHeader(token: AuthToken): Authorization = {
    headers.Authorization(OAuth2BearerToken(token.value))
  }

  private def sendRequest(httpRequest: HttpRequest) : String = {
    val response = Await.result(Http().singleRequest(httpRequest), 60.seconds)
    response.status.isSuccess() match {
      case true =>
        val byteStringSink: Sink[ByteString, Future[ByteString]] = Sink.fold(ByteString("")) { (z, i) => z.concat(i) }
        val entityFuture = response.entity.dataBytes.runWith(byteStringSink)
        Await.result(entityFuture, 50.millis).decodeString("UTF-8")
      case _ => throw new APIException(response.entity.toString)
    }
  }

  private def requestWithJsonContent(method: HttpMethod, uri:String, content:Any)(implicit token: AuthToken): String = {
    val req = HttpRequest(method, uri, List(makeAuthHeader(token)), HttpEntity(ContentTypes.`application/json`, mapper.writeValueAsString(content)))
    sendRequest(req)
  }

  def postRequestWithMultipart(uri:String, name: String, content: String)(implicit token: AuthToken): String = {
    // val cont = ContentType.parse("text/tab-separated-values")
    //val ranges = coalesceRanges(iRanges).sortBy(_.start)
    //val formD = Multipart.ByteRanges(HttpEntity(ByteString("...")))
    val formData = Multipart.FormData {
      Source {
        List (
          Multipart.FormData.BodyPart.apply(name, HttpEntity(ByteString(content)))
        )
      }
    }
    //val formData = Multipart.FormData.fromPath("participants", ContentType.parse("text/tab-separated-values"), "")
    val requestEntity = formData.toEntity()
    val req = HttpRequest(POST, uri, List(makeAuthHeader(token)), requestEntity)
    sendRequest(req)
  }

  private def requestBasic(method: HttpMethod, uri:String)(implicit token: AuthToken): String = {
    val req = HttpRequest(method, uri, List(makeAuthHeader(token)))
    sendRequest(req)
  }

  def patchRequest(uri: String, content: Any)(implicit token: AuthToken): String = {
    requestWithJsonContent(PATCH, uri, content)
  }

  def postRequest(uri: String, content: Any = None)(implicit token: AuthToken): String = {
    requestWithJsonContent(POST, uri, content)
  }

  def putRequest(uri: String, content: Any = None)(implicit token: AuthToken): String = {
    requestWithJsonContent(PUT, uri, content)
  }

  def deleteRequest(uri: String)(implicit token: AuthToken): String = {
    requestBasic(DELETE, uri)
  }

  def getRequest(uri: String)(implicit token: AuthToken): String = {
    requestBasic(GET, uri)
  }
}
