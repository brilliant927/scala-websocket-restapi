package controllers

import play.api._
import play.api.mvc._
import javax.inject._
import play.api.libs.ws._
import scala.concurrent._
import play.api.libs.json._
import akka.stream.scaladsl._
import models.{HostConnectDto}
import scala.collection.mutable
import play.api.libs.streams.ActorFlow
import scala.util.{ Failure, Success }
import scala.concurrent.duration.Duration
import ExecutionContext.Implicits.global
/**
 * This controller creates an `Action` to handle HTTP requests to the
 * application's home page.
 */
@Singleton
class HomeController @Inject()(val controllerComponents: ControllerComponents, ws: WSClient) extends BaseController {
  
  implicit val hostConnectImp = Json.format[HostConnectDto]
  var hostUrl : String = ""
  var isValidHostUrl: Boolean = false

  def hostConnect() = Action { implicit request => 
    val content = request.body 
    val jsonObject = content.asJson

    val hostUrlDto: Option[HostConnectDto] = jsonObject.flatMap(Json.fromJson[HostConnectDto](_).asOpt)

    hostUrlDto match {
      case Some(newHostUrl) =>
        hostUrl = (Json.toJson(newHostUrl) \ "hosturl").as[String]
      case None =>
        isValidHostUrl = false
        BadRequest(Json.obj("message" -> "Invalid URL"))
    }
      
    if(hostUrl == "") {
      isValidHostUrl = false
      BadRequest(Json.obj("message" -> "Invalid URL"))
    } else {
      try {
        val request: WSRequest = ws.url(hostUrl)
        val res: Future[WSResponse] = request.get()
        val connectionStatus  = Await.result(res, Duration.Inf).status;
        //URL validator
        if (connectionStatus == 200) { 
          isValidHostUrl = true
          Ok("Connection successed")
        } else {
          isValidHostUrl = false
          BadRequest(Json.obj("message" -> "Connection failed"))
        }
      } catch {
        case  _: Throwable => BadRequest(Json.obj("message" -> "Invalid URL"))
      }
    }
  }

  def getPosts() = Action { implicit request => 
    try {
      val request: WSRequest = ws.url(hostUrl)
      val res: Future[WSResponse] = request.get()
      val connectionStatus  = Await.result(res, Duration.Inf).status;
      //URL validator
      if (connectionStatus == 200) { 
        val posts: List[JsValue] = Json.parse(Await.result(res, Duration.Inf).body).as[List[JsValue]]
        Ok(Json.stringify(Json.toJson(posts)))
      } else {
        isValidHostUrl = false
        BadRequest(Json.obj("message" -> "Connection failed"))
      }
    } catch {
      case  _: Throwable => BadRequest(Json.obj("message" -> "Invalid URL"))
    }
   
  }

  def socket = WebSocket.accept[String, String] { request =>
    var returnString: String = ""
    if(hostUrl!="" && isValidHostUrl) {
      val request: WSRequest = ws.url(hostUrl)
      val res: Future[WSResponse] = request.get()
      // the block of code that will be executed
      val posts: List[JsValue] = Json.parse(Await.result(res, Duration.Inf).body).as[List[JsValue]]

      //Combine posts as string
      var combinedPostsContents: String = "";
      for( post <- posts) {
        combinedPostsContents += (post \ "content" \ "rendered").as[String];
      }
      //Remove unnecessary symbols
      combinedPostsContents = combinedPostsContents.replaceAll("""/[^\w\s]/g""", "");
      // Get work frequency
      val counts = mutable.Map.empty[String, Int].withDefaultValue(0)
      for(rawWord <- combinedPostsContents.split(" ")) {
        val word = rawWord.toLowerCase
        counts(word) +=1
      }
      returnString = counts.toString
    }
    if(returnString == "") returnString == "Please set Host Url"
    Console.printf("socket...")
    var in = Sink.foreach[String](println)
    var out = Source.single(returnString).concat(Source.maybe)
    Flow.fromSinkAndSource(in, out)
    
  }
}
