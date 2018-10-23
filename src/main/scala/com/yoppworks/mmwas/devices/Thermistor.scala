package com.yoppworks.mmwas.devices

import scala.concurrent.ExecutionContext
import scala.concurrent.duration._
import scala.util.Random

import akka.actor.{ActorRef, ActorRefFactory, Cancellable, PoisonPill, Props}
import com.pi4j.io.gpio._

object Thermistor {

  sealed trait ThermistorMessages
  case object StartMeasuring extends ThermistorMessages
  case object StopMeasuring extends ThermistorMessages
  case object TakeAMeasurement extends ThermistorMessages
  case object Unprovision extends ThermistorMessages

  /** Create an Thermistor Actor
   * @param device the ThermistorDevice to use
   * @param sampleRate the delay between temperature samples for the stream
   * @param factory Implicit ActorRefFactory for creating actors
   * @return
   */
  def create(
    device: ThermistorDevice,
    sampleRate: FiniteDuration,
    sendTo: ActorRef = ActorRef.noSender
  )(implicit factory: ActorRefFactory
  ): ActorRef = {
    val props = Props(new Thermistor(device, sampleRate, sendTo))
    factory.actorOf(props, device.deviceName + "Actor")
  }
}

trait ThermistorDevice extends InputDevice[Double]

case class RandomThermistorDevice(deviceName: String) extends ThermistorDevice {

  def readOneValue: Double = {
    Random.nextDouble()*20 + 125
  }

  def unprovision(): Unit = {
    ()
  }
}

case class GPIOThermistorDevice(
  deviceName: String,
  gpio: GpioController,
  thermistorPin: Pin)
    extends ThermistorDevice {

  val analogPin: GpioPinAnalogInput =
    gpio.provisionAnalogInputPin(thermistorPin, "Thermistor")

  analogPin.setShutdownOptions(true)

  def readOneValue: Double = {
    analogPin.getValue
  }

  def unprovision(): Unit = {
    gpio.unprovisionPin(analogPin)
  }
}

/** A class for interacting with the HC-SR04 Ultrasonic Distance Device*/
case class Thermistor(
  device: ThermistorDevice,
  sampleRate: FiniteDuration,
  sendTo: ActorRef)
    extends DeviceActor {

  import Thermistor._
  final implicit val executionContext: ExecutionContext =
    context.system.dispatcher

  var timerCancellation: Cancellable = Cancellable.alreadyCancelled

  def receive: Receive = {
    case StartMeasuring ⇒
      timerCancellation = context.system.scheduler
        .schedule(0.millis, sampleRate, self, TakeAMeasurement)
    case StopMeasuring ⇒
      if (!timerCancellation.isCancelled) {
        val _ = timerCancellation.cancel()
      }
    case TakeAMeasurement ⇒
      val analogVal = device.readOneValue
      val Vr = 5D * analogVal / 255.0D
      val Rt = 10000D * Vr / (5D - Vr)
      val tempInK = 1 / ((Math.log(Rt / 10000D) / 3950D) + (1D / (273.15 + 25)))
      val tempInC = tempInK - 273.15D
      debug(msg =
        f"Thermistor Value = $analogVal%5.3f, tempInC=$tempInC%3.1f")
      sendTo ! tempInC
    case Unprovision ⇒
      device.unprovision()
      self ! PoisonPill
  }
}
