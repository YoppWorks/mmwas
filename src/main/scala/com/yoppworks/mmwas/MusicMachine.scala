package com.yoppworks.mmwas

import scala.concurrent.{Await, ExecutionContext}
import scala.concurrent.duration._
import akka.NotUsed
import akka.actor.ActorRef
import akka.actor.ActorRefFactory
import akka.pattern.ask
import akka.stream.Materializer
import akka.stream.scaladsl.{BroadcastHub, Flow, Keep, Merge, RunnableGraph, Sink, Source}
import akka.util.Timeout
import com.yoppworks.mmwas.MusicMaker._
import com.yoppworks.mmwas.MakingMusicWithAkkaStreams.MakingMusicConfig
import com.yoppworks.mmwas.devices.{InstrumentDevice, KeyTypedDevice, MousePositionPitchDevice, MousePositionVolumeDevice, MousePressedPortamentoDevice, MouseWheelRotatedInstrumentDevice, MouseWindowOnOffDevice, OnOffDevice, PaneData, PitchDevice, PortamentoDevice, ScreenInput, ScreenOutput, SpecialKeyTypedDevice, VolumeDevice}
import com.yoppworks.mmwas.streams._

case class MusicMachine(
                         config: MakingMusicConfig,
                         screenInput: ScreenInput,
                         screenOutput: ScreenOutput,
                         channel: Int
)(implicit
  val exc: ExecutionContext,
  val arf: ActorRefFactory,
  val mat: Materializer
) {

  implicit def to: Timeout = Timeout(5.seconds)

  val regularKeyChannel: Int = channel + 1
  val specialKeyChannel: Int = channel + 2

  def printDeviceInfo(): Unit = {
    println("Screen Input")
  }

  private var paneData: PaneData = PaneData(0,0)

  private val pitchDev: PitchDevice =
    MousePositionPitchDevice(screenInput, screenOutput)

  private val volumeDev: VolumeDevice =
    MousePositionVolumeDevice(screenInput, screenOutput)

  private val instrumentDev: InstrumentDevice =
    MouseWheelRotatedInstrumentDevice(screenInput)

  private val portamentoDev: PortamentoDevice =
    MousePressedPortamentoDevice(screenInput)

  private val onOffDev: OnOffDevice =
    MouseWindowOnOffDevice(screenInput)

  private val keyTypedDev: KeyTypedDevice =
    KeyTypedDevice(screenInput, regularKeyChannel)

  private val specialKeyTypedDev: SpecialKeyTypedDevice =
    SpecialKeyTypedDevice(screenInput, specialKeyChannel)

  private val pitchesAndVolumes: Source[MusicMakerMessage, NotUsed]= {
    pitchDev
      .dataSource
      .via(Flow.fromFunction{ pitch: Int =>
        paneData = paneData.copy(note = pitch)
        pitch
      })
      .via[PlayNote, NotUsed](
        DevicesToNotesGraph(channel, volumeDev.dataSource)
      )
  }

  private val onoffs: Source[MusicMakerMessage, NotUsed] = {
    onOffDev.dataSource.map {
      case 0 => IgnoreNotes
      case _ => ResumeNotes
    }
  }

  private val keys: Source[MusicMakerMessage, NotUsed] = {
    keyTypedDev.dataSource
  }

  private val portamentos: Source[MusicMakerMessage, NotUsed] = {
    portamentoDev.dataSource.map { portamento =>
      SetPitchBend(channel, portamento)
    }
  }

  private val instruments: Source[MusicMakerMessage, NotUsed] = {
    instrumentDev
      .dataSource
        .via(Flow.fromFunction{ instrument: Int =>
          paneData = paneData.copy(instrument = instrument)
          instrument
        })
      .flatMapConcat { instrument =>
        println(s"instrument = ${instrument}")
        Source.apply[MusicMakerMessage](
          Vector(
            ChangeInstrument(channel, instrument),
            ChangeInstrument(specialKeyChannel, instrument)
          )
        )
      }
  }

  private val specialKeys: Source[MusicMakerMessage, NotUsed] = {
    specialKeyTypedDev.dataSource
  }

  private val notesHub: RunnableGraph[Source[MusicMakerMessage, NotUsed]] = {
    Source
      .combine(pitchesAndVolumes, onoffs, keys,
        portamentos, instruments, specialKeys
      ){ numSources: Int =>
        Merge(numSources, eagerComplete = false)
      }
      .toMat(BroadcastHub.sink)(Keep.right)
  }

  def combinedMessages: Source[MusicMakerMessage, NotUsed] = {
    notesHub.run()
  }

  private val musicMakerActor: ActorRef =
    MusicMaker.create(config.soundFont)

  def startDevices(): Unit = {
    val future = musicMakerActor ? MusicMaker.StartPlaying
    val _ = Await.result(future, to.duration)
  }
  
  def stopDevices(): Unit = {
    musicMakerActor ! MusicMaker.StopPlaying
  }

  def play(): NotUsed = {
    val _ = Source
      .tick(0.millis, 250.millis, NotUsed)
      .runForeach { _: NotUsed =>
        screenOutput.update(paneData)
      }
    combinedMessages
      .runWith(Sink.actorRef(musicMakerActor, StopPlaying))
  }
}


