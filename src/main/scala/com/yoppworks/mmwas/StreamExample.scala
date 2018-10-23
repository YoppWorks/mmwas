package com.yoppworks.mmwas

import akka.{Done, NotUsed}
import akka.stream._
import akka.stream.scaladsl._
import akka.actor.ActorSystem

import scala.concurrent.Future

object StreamExample {

  implicit val system: ActorSystem = ActorSystem("foo")
  implicit val materializer: Materializer = ActorMaterializer()

  def main(args: Array[String]): Unit = {
    val source: Source[Int, NotUsed] = Source(1 to 100)
    val flow: Flow[Int, Int, NotUsed] = Flow.fromFunction(x => x / 2)
    val sink: Sink[Int, Future[Done]] = Sink.foreach(println(_))
    val _ = source.via(flow).to(sink).run()
  }
}