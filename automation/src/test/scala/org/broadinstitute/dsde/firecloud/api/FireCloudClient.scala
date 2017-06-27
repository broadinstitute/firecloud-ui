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
import scala.concurrent.{Await, ExecutionContext, ExecutionContextExecutor, Future}

class FireCloudClient {
  implicit val system = ActorSystem()
  implicit val materializer = ActorMaterializer()
  implicit val ec: ExecutionContextExecutor = ExecutionContext.global

  val mapper = new ObjectMapper()
  mapper.registerModule(DefaultScalaModule)

  // TODO: Is this okay here or should it be in a separate object and explicitly imported?
  implicit class JsonStringUtil(s: String) {
    def fromJsonMapAs[A](key: String): Option[A] =
      parseJsonAsMap.get(key)

    def parseJsonAsMap[A]: Map[String, A] = mapper.readValue(s, classOf[Map[String, A]])
  }

  private def makeAuthHeader(token: AuthToken): Authorization = {
    headers.Authorization(OAuth2BearerToken(token.value))
  }

  private def sendRequest(httpRequest: HttpRequest) : String = {
    val response = Await.result(Http().singleRequest(httpRequest), 5.minutes)
    response.status.isSuccess() match {
      case true =>
        val byteStringSink: Sink[ByteString, Future[ByteString]] = Sink.fold(ByteString("")) { (z, i) => z.concat(i) }
        val entityFuture = response.entity.dataBytes.runWith(byteStringSink)
        Await.result(entityFuture, 50.millis).decodeString("UTF-8")
      case _ =>
        val byteStringSink: Sink[ByteString, Future[ByteString]] = Sink.fold(ByteString("")) { (z, i) => z.concat(i) }
        val entityFuture = response.entity.dataBytes.runWith(byteStringSink)
        throw new APIException(Await.result(entityFuture, 50.millis).decodeString("UTF-8"))
    }
  }

  private def requestWithJsonContent(method: HttpMethod, uri: String, content: Any, httpHeaders: List[HttpHeader] = List())(implicit token: AuthToken): String = {
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

  private def requestBasic(method: HttpMethod, uri: String, httpHeaders: List[HttpHeader] = List())(implicit token: AuthToken): String = {
    val req = HttpRequest(method, uri, List(makeAuthHeader(token)) ++ httpHeaders)
    sendRequest(req)
  }

  def patchRequest(uri: String, content: Any, httpHeaders: List[HttpHeader] = List())(implicit token: AuthToken): String = {
    requestWithJsonContent(PATCH, uri, content, httpHeaders)
  }

  def postRequest(uri: String, content: Any = None, httpHeaders: List[HttpHeader] = List())(implicit token: AuthToken): String = {
    requestWithJsonContent(POST, uri, content, httpHeaders)
  }

  def putRequest(uri: String, content: Any = None, httpHeaders: List[HttpHeader] = List())(implicit token: AuthToken): String = {
    requestWithJsonContent(PUT, uri, content, httpHeaders)
  }

  def deleteRequest(uri: String, httpHeaders: List[HttpHeader] = List())(implicit token: AuthToken): String = {
    requestBasic(DELETE, uri, httpHeaders)
  }

  def getRequest(uri: String, httpHeaders: List[HttpHeader] = List())(implicit token: AuthToken): String = {
    requestBasic(GET, uri, httpHeaders)
  }
}
