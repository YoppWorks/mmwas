package com.yoppworks.mmwas.devices

import scala.concurrent.{Await, ExecutionContext}
import scala.concurrent.duration._
import akka.NotUsed
import akka.actor.{ActorRef, ActorRefFactory}
import akka.pattern.ask
import akka.stream.Materializer
import akka.stream.scaladsl.{BroadcastHub, Keep, Source}
import akka.util.Timeout
import com.yoppworks.mmwas.MusicMaker
import com.yoppworks.mmwas.MakingMusicWithAkkaStreams.MakingMusicConfig
import com.yoppworks.mmwas.MusicMaker.PlayNote
import com.yoppworks.mmwas.streams.InputsToSampleGraph

/** Base Class For The Device Managers*/

case class Sample(pitch: Long = 0L, volume: Long = 0L,
  temperature: Double = 0L, potential: Long = 0L)


trait DeviceManager {
  implicit val exc: ExecutionContext
  implicit val arf: ActorRefFactory
  implicit val mat: Materializer
  implicit def to: Timeout = Timeout(5.seconds)
  
  def config: MakingMusicConfig
  def printDeviceInfo(): Unit
  def terminate(): Unit

  private val sampleRate: FiniteDuration = config.sampleRate
  private val pitchRate: FiniteDuration = sampleRate
  private val volumeRate: FiniteDuration = pitchRate
  def potentiometerRate: FiniteDuration = 100.millis
  def thermistorRate: FiniteDuration = 5.seconds
  
  private val musicMakerActor: ActorRef =
    MusicMaker.create(config.soundFont)
  
  protected def potentiometer: PotentiometerDevice
  protected val potSource: ActorBasedDataSource[Long] =
    ActorBasedDataSource[Long](name="potential")
  val potActor: ActorRef = Potentiometer.create(potentiometer,
    potentiometerRate, potSource.dataActor)
  val potentials: Source[Long,NotUsed] = potSource.dataSource

  protected def thermistor: ThermistorDevice
  protected val tempSource: ActorBasedDataSource[Double] =
    ActorBasedDataSource[Double](name="temperature")
  val tempActor: ActorRef = Thermistor.create(thermistor,thermistorRate,
    tempSource.dataActor)
  val temperatures: Source[Double,NotUsed] =
    tempSource.dataSource
    
  protected def pitchUSM: UltraSonicMeasurementDevice
  protected val pitchSource: ActorBasedDataSource[Long] =
    ActorBasedDataSource[Long](name="pitch")
  val pitchActor: ActorRef =
    UltrasonicMeasurement.create(pitchUSM, pitchRate, pitchSource.dataActor)
  val pitches: Source[Long,NotUsed] =
    pitchSource.dataSource.filter( pitch ⇒ pitch >= 5 && pitch <= 1005)
  
  
  protected def volumeUSM: UltraSonicMeasurementDevice
  protected val volumeSource: ActorBasedDataSource[Long] =
    ActorBasedDataSource[Long](name="volume")

  val volumeActor: ActorRef = UltrasonicMeasurement.create(volumeUSM,
    volumeRate, volumeSource.dataActor)

  val volumes: Source[Long,NotUsed] =
    volumeSource.dataSource.filter( vol ⇒ vol >= 5 && vol <= 1005)
  
  val samples: Source[Sample, NotUsed]= {
    pitches
      .via[Sample,NotUsed](
        InputsToSampleGraph(volumes, temperatures, potentials)
      )
      .toMat(BroadcastHub.sink)(Keep.right).run()
  }
  
  val notes: Source[PlayNote,NotUsed] = {
    samples.map { s: Sample ⇒
      PlayNote(0,
        (40 + s.pitch % 60).toInt,
        (s.volume % 96).toInt + 31)
    }
  }
  
  def startDevices(): Unit = {
    pitchActor ! UltrasonicMeasurement.StartMeasuring
    volumeActor ! UltrasonicMeasurement.StartMeasuring
    tempActor ! Thermistor.StartMeasuring
    potActor ! Potentiometer.StartMeasuring
    val future = musicMakerActor ? MusicMaker.StartPlaying
    Await.result(future, to.duration)
    val _ = notes.runForeach{ note: PlayNote ⇒ musicMakerActor ! note }
  }
  
  def stopDevices(): Unit = {
    pitchActor ! UltrasonicMeasurement.StopMeasuring
    volumeActor ! UltrasonicMeasurement.StopMeasuring
    tempActor ! Thermistor.StopMeasuring
    potActor ! Potentiometer.StopMeasuring
    pitchActor ! UltrasonicMeasurement.Unprovision
    volumeActor ! UltrasonicMeasurement.Unprovision
    tempActor ! Thermistor.Unprovision
    potActor ! Potentiometer.Unprovision
    musicMakerActor ! MusicMaker.StopPlaying
  }
}




