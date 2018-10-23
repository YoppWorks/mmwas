package com.yoppworks.mmwas.devices

import java.awt.event._

import scala.concurrent.Await
import scala.concurrent.duration._
import scala.reflect.classTag
import akka.NotUsed
import akka.actor.{ActorRef, ActorSystem, Cancellable}
import akka.event.Logging
import akka.stream.{ActorMaterializer, Attributes, Materializer, OverflowStrategy}
import akka.stream.scaladsl.{BroadcastHub, Keep, RunnableGraph, Sink, Source}
import com.yoppworks.mmwas.MakingMusicWithAkkaStreams.MakingMusicConfig
import javax.swing.JFrame


sealed trait ScreenInputEvent
case class MouseWheelRotated(rotation: Int) extends ScreenInputEvent
case class MouseRelocated(x: Int, y: Int) extends ScreenInputEvent
case class KeyTyped(ch: Char, code: Int) extends ScreenInputEvent
case class MouseClicked(x: Int, y:Int) extends ScreenInputEvent
sealed trait MouseInputEvent extends ScreenInputEvent
sealed trait LeftMouseInputEvent extends MouseInputEvent
case class LeftMouseDown(when: Long) extends LeftMouseInputEvent
case class LeftMouseUp(when: Long) extends LeftMouseInputEvent
sealed trait RightMouseInputEvent extends MouseInputEvent
case class RightMouseDown(when: Long) extends RightMouseInputEvent
case class RightMouseUp(when: Long) extends RightMouseInputEvent
sealed trait MouseWindowEvent extends ScreenInputEvent
case object MouseEntered extends MouseWindowEvent
case object MouseExited extends MouseWindowEvent

case class ScreenInput(frame: JFrame, config: MakingMusicConfig)(implicit mat: Materializer)
  extends KeyListener with MouseListener with
  MouseWheelListener {

  private final val stream: Source[ScreenInputEvent,ActorRef] =
    Source.actorRef(100, OverflowStrategy.dropNew)

  private final val (eventActor, theSource) = stream.preMaterialize()

  private final val source = {
    if (config.eventLogging) {
      theSource
      .log("event source")
      .withAttributes(
        Attributes.logLevels(
          onElement = Logging.InfoLevel,
          onFinish = Logging.InfoLevel,
          onFailure = Logging.ErrorLevel
        )
      )
    } else {
      theSource
    }
  }

  frame.addKeyListener(this)
  frame.addMouseWheelListener(this)
  frame.addMouseListener(this)

  def allEventsSource: Source[ScreenInputEvent,NotUsed] = {
    source
  }

  private val keyTypedHub: RunnableGraph[Source[KeyEvent, NotUsed]] = {
    source
      .collectType[KeyEvent](classTag[KeyEvent])
      .toMat(BroadcastHub.sink)(Keep.right)
  }

  def keyTypedSource: Source[KeyEvent, NotUsed] = {
    keyTypedHub.run()
  }
  
  val mouseClickedSource: Source[MouseClicked,NotUsed] = {
    source.collectType[MouseClicked](classTag[MouseClicked])
  }
  
  val mouseWheelRotatedSource: Source[MouseWheelRotated,NotUsed] = {
    source.collectType[MouseWheelRotated](classTag[MouseWheelRotated])
  }

  val mouseInputSource: Source[MouseInputEvent, NotUsed] = {
    source.collectType[MouseInputEvent](classTag[MouseInputEvent])
  }

  val mouseWindowSource: Source[MouseWindowEvent, NotUsed] = {
    source.collectType[MouseWindowEvent](classTag[MouseWindowEvent])
  }

  val mousePositionSource: Source[MouseRelocated, Cancellable] = {
    Source
      .tick(0.millis, config.sampleRate, ())
      .map { _: Unit =>
        import java.awt.MouseInfo
        import java.awt.Point
        val p: Point = MouseInfo.getPointerInfo.getLocation
        MouseRelocated(p.x, p.y)
      }
  }

  protected def keyPressed(e : KeyEvent) : Unit = {}

  protected def keyReleased(e : KeyEvent) : Unit = {}

  protected def keyTyped(e : KeyEvent) : Unit = {
    eventActor ! e
  }
  
  protected def mouseClicked(e : MouseEvent) : Unit = {
    eventActor ! MouseClicked(e.getX, e.getY)
  }

  protected def mouseWheelMoved(e : MouseWheelEvent) : Unit = {
    eventActor ! MouseWheelRotated(e.getWheelRotation)
  }

  protected def mouseEntered(e : MouseEvent) : Unit = {
    eventActor ! MouseEntered
  }

  protected def mouseExited(e : MouseEvent) : Unit = {
    eventActor ! MouseExited
  }

  protected def mousePressed(e : MouseEvent) : Unit = {
    e.getButton match {
      case MouseEvent.BUTTON1 ⇒
        eventActor ! LeftMouseDown(e.getWhen)
      case MouseEvent.BUTTON2 ⇒
        eventActor ! RightMouseDown(e.getWhen)
      case MouseEvent.BUTTON3 ⇒
        eventActor ! RightMouseDown(e.getWhen)
      case _  =>
        ()
    }
  }
  
  protected def mouseReleased(e : MouseEvent) : Unit = {
    e.getButton match {
      case MouseEvent.BUTTON1 ⇒
        eventActor ! LeftMouseUp(e.getWhen)
      case MouseEvent.BUTTON2 ⇒
        eventActor ! RightMouseUp(e.getWhen)
      case MouseEvent.BUTTON3 ⇒
        eventActor ! RightMouseUp(e.getWhen)
      case _ =>
        ()
    }
  }
  
}

object ScreenInput {
  def main(args : Array[String]) : Unit = {
    implicit val sys: ActorSystem = ActorSystem("ScreenInput")
    implicit val mat: Materializer = ActorMaterializer()
    val config = MakingMusicConfig()
    val screenOut = new ScreenOutput(config)
    val screen = new ScreenInput(screenOut, config)
    val f = screen.allEventsSource.runWith(Sink.foreach { event ⇒
      System.out.println(event.toString)
    })
    Await.result(f, 5.minutes)
    System.in.read()
    ()
  }
}
