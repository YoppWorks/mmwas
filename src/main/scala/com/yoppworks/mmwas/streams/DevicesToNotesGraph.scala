package com.yoppworks.mmwas.streams

import akka.NotUsed
import akka.stream.Attributes
import akka.stream.FlowShape
import akka.stream.Inlet
import akka.stream.Materializer
import akka.stream.Outlet
import akka.stream.scaladsl.Flow
import akka.stream.scaladsl.Source
import akka.stream.stage.GraphStage
import akka.stream.stage.GraphStageLogic
import akka.stream.stage.InHandler
import akka.stream.stage.OutHandler
import com.yoppworks.mmwas.MusicMaker.PlayNote

object DevicesToNotesGraph {
  def apply(channel: Int,
    volumes: Source[Int,NotUsed]
  )(implicit mat: Materializer): Flow[Int, PlayNote, NotUsed] = {
    Flow.fromGraph[Int,PlayNote,NotUsed](
      new DevicesToNotesGraph(channel, volumes)
    )
  }
}

class DevicesToNotesGraph(
  channel: Int, volumes: Source[Int,NotUsed]
)(implicit mat: Materializer) extends GraphStage[FlowShape[Int, PlayNote]] {
  
  val pitchesPort: Inlet[Int] = Inlet[Int]("pitches.in")
  val musicMakerMessagesPort: Outlet[PlayNote] =
    Outlet[PlayNote]("musicMakerMessages.out")
  
  override val shape: FlowShape[Int, PlayNote] =
    FlowShape(pitchesPort, musicMakerMessagesPort)

  def createLogic(inheritedAttributes: Attributes): GraphStageLogic = {
    new GraphStageLogic(shape) {
      var lastVolume: Int = 0
      volumes.runForeach( v ⇒ lastVolume = v)
      setHandler(pitchesPort, new InHandler {
        def onPush(): Unit = {
          val pitch = grab(pitchesPort)
          push(musicMakerMessagesPort, PlayNote(channel, pitch, lastVolume))
      }})
      setHandler(musicMakerMessagesPort, new OutHandler {
        override def onPull() : Unit = {
          pull(pitchesPort)
        }
      })
    }
  }
  override def toString = "DevicesToMusicMakerMessagesGraph"
}
