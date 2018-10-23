package com.yoppworks.mmwas.devices

import scala.concurrent.ExecutionContext
import scala.concurrent.duration._
import scala.util.Random

import akka.actor.{ActorRef, ActorRefFactory, Cancellable, PoisonPill, Props}
import com.pi4j.io.gpio._

object Potentiometer {

  sealed trait PotentiometerMessages
  case object StartMeasuring extends PotentiometerMessages
  case object StopMeasuring extends PotentiometerMessages
  case object TakeAMeasurement extends PotentiometerMessages
  case object Unprovision extends PotentiometerMessages

  /** Create an LED Actor.
   * @param device the PotentiometerDevice to use
   * @param sampleRate the delay between temperature samples for the stream
   * @param factory Implicit ActorRefFactory for creating actors
   * @return
   */
  def create(
    device: PotentiometerDevice,
    sampleRate: FiniteDuration,
    sendTo: ActorRef = ActorRef.noSender
  )(implicit factory: ActorRefFactory
  ): ActorRef = {
    val props = Props(new Potentiometer(device, sampleRate, sendTo))
    factory.actorOf(props, device.deviceName + "Actor")
  }
}

trait PotentiometerDevice extends InputDevice[Long]

case class RandomPotentiometerDevice(deviceName: String) extends PotentiometerDevice {

  def readOneValue: Long = {
    (Random.nextDouble() * 100).toLong
  }

  def unprovision(): Unit = {
    ()
  }
}

case class GraduatingPotentiometerDevice(deviceName: String) extends
  PotentiometerDevice {
  
  final private val limit: Long = 30
  private var countDown: Long = limit
  private var currVal: Long = 0
  final private def next : Long = {
    countDown -= 1
    if (countDown == 0) {
      countDown = limit
      currVal = currVal +  1
    }
    currVal
  }
  
  def readOneValue: Long = {
    val value = next
    value
  }
  
  def unprovision(): Unit = {
    ()
  }
}


case class GPIOPotentiometerDevice(
  deviceName: String,
  gpio: GpioController,
  PotentiometerPin: Pin)
    extends PotentiometerDevice {

  val analogPin: GpioPinAnalogInput =
    gpio.provisionAnalogInputPin(PotentiometerPin, "Potentiometer")

  analogPin.setShutdownOptions(true)

  def readOneValue: Long = {
    analogPin.getValue.toLong
  }

  def unprovision(): Unit = {
    gpio.unprovisionPin(analogPin)
  }
}

/** A class for interacting with the HC-SR04 Ultrasonic Distance Device*/
case class Potentiometer(
  device: PotentiometerDevice,
  sampleRate: FiniteDuration,
  sendTo: ActorRef)
    extends DeviceActor {

  import Potentiometer._
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
      val potentiometerValue = device.readOneValue
      debug(msg = f"Potentiometer Value = $potentiometerValue%d")
      sendTo ! potentiometerValue

    case Unprovision ⇒
      device.unprovision()
      self ! PoisonPill
  
  }
}
