package com.yoppworks.mmwas.devices

import scala.concurrent.ExecutionContext
import scala.concurrent.duration._

import akka.actor.ActorRefFactory
import akka.stream.Materializer
import com.pi4j.io.gpio.GpioController
import com.pi4j.io.gpio.GpioFactory
import com.pi4j.io.gpio.Pin
import com.pi4j.io.gpio.RaspiPin
import com.yoppworks.mmwas.MakingMusicWithAkkaStreams.MakingMusicConfig

case class RaspiDeviceManager(config: MakingMusicConfig)(implicit
                                                         val exc: ExecutionContext, val arf: ActorRefFactory,
                                                         val mat: Materializer) extends DeviceManager {
  val gpio: GpioController = GpioFactory.getInstance
  val Trig_USM1_Pin:    Pin = RaspiPin.GPIO_00    // BCM T = 17
  val Echo_USM1_Pin:    Pin = RaspiPin.GPIO_01    // BCM T = 18
  val ThermistorPin:    Pin = RaspiPin.GPIO_02    // BCM T = 27
  val Trig_USM2_Pin:    Pin = RaspiPin.GPIO_03    // BCM T = 22
  val Echo_USM2_Pin:    Pin = RaspiPin.GPIO_04    // BCM T = 23
  val PotentiometerPin: Pin = RaspiPin.GPIO_05    // BCM T = 24
  val LEDPin:           Pin = RaspiPin.GPIO_06    // BCM T = 25
  val IRRemoteOutPin:   Pin = RaspiPin.GPIO_21    // BCM T = 05
  val IRRemoteInPin:    Pin = RaspiPin.GPIO_22    // BCM T = 06
  
  val potentiometer: PotentiometerDevice =
    GPIOPotentiometerDevice("Potentiometer",gpio, PotentiometerPin)

  val thermistor: ThermistorDevice =
    GPIOThermistorDevice("Thermistor", gpio,ThermistorPin)

  val pitchUSM: UltraSonicMeasurementDevice =
    GPIOUltraSonicMeasurementDevice("USM1", gpio, Echo_USM1_Pin,
      Trig_USM1_Pin, 2.seconds, temperatures)
  
  val volumeUSM: UltraSonicMeasurementDevice =
    GPIOUltraSonicMeasurementDevice("USM2", gpio, Echo_USM2_Pin,
      Trig_USM2_Pin, 2.seconds, temperatures)
  
  def printDeviceInfo(): Unit = {
    gpio.printSystemInfo()
    
  }
  def terminate(): Unit = {
    gpio.shutdown()
  }
}
