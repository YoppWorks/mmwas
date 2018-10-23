package com.yoppworks.mmwas.devices

import scala.concurrent.ExecutionContext

import akka.actor.ActorRefFactory
import akka.stream.Materializer
import com.yoppworks.mmwas.MakingMusicWithAkkaStreams.MakingMusicConfig

case class MockDeviceManager(config: MakingMusicConfig)(implicit
                                                        val exc: ExecutionContext,
                                                        val arf: ActorRefFactory,
                                                        val mat: Materializer
) extends DeviceManager {
  def terminate(): Unit = {
    ()
  }
  
  def thermistor: ThermistorDevice =
    RandomThermistorDevice("Thermistor")

  def potentiometer: PotentiometerDevice =
    GraduatingPotentiometerDevice("Potentiometer")
  
  def pitchUSM: UltraSonicMeasurementDevice =
    GraduatingUltraSonicMeasurementDevice("PitchUSM")
  
  def volumeUSM: UltraSonicMeasurementDevice =
    ConstantUltrasonicMeasurementDevice("FrequencyUSM")
  
  override def printDeviceInfo() : Unit = {
    println("MockDeviceInfo")
  }
}
