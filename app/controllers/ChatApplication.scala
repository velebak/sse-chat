package controllers

import play.api.mvc._
import play.api.libs.json.JsValue
import play.api.libs.iteratee.{Enumeratee, Concurrent}
import play.api.libs.EventSource

object ChatApplication extends Controller {

  case class Message(json: JsValue, remoteAddress: String)
  
  /** Central hub for distributing chat messages */
  val (chatOut, chatChannel) = Concurrent.broadcast[Message]

  /** Controller action serving chat page */
  def index = Action { Ok(views.html.index("Chat using Server Sent Events")) }

  /** Controller action for POSTing chat messages */
  def postMessage = Action(parse.json) { req => chatChannel.push(Message(req.body, req.remoteAddress)); Ok }

  /** Enumeratee for filtering messages based on room */
  def roomFilter(room: String) = Enumeratee.filter[Message] { msg => (msg.json \ "room").as[String] == room }

  /** Enumeratee for filtering messages based on IP address (in demo messages only delivered to sender) */
  def ipFilter(remoteAddress: String) = Enumeratee.filter[Message] { 
    msg => msg.remoteAddress == remoteAddress || msg.remoteAddress == "actors"
  }

  /** map Message case class to JsValue */
  def toJsValue: Enumeratee[Message, JsValue]  = Enumeratee.map[Message] { msg: Message => msg.json }

  /** Controller action serving activity based on room */
  def chatFeed(room: String) = Action { 
    req => Ok.stream(chatOut &> roomFilter(room) 
      &> ipFilter(req.remoteAddress)
      &> toJsValue
      &> EventSource()).as("text/event-stream") }

}