package models

import akka.actor._

object MyWebSocketActor {
  def props(out: ActorRef, message: String) = Props(new MyWebSocketActor(out, message))
}

class MyWebSocketActor(out: ActorRef, message: String) extends Actor {
  def receive = {
    case msg: String =>
      out ! (message)
  }
}