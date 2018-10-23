package com.yoppworks.mmwas.devices

import scala.concurrent.ExecutionContext
import scala.concurrent.duration._
import scala.util.Random

import akka.NotUsed
import akka.actor.{ActorRef, ActorRefFactory, Cancellable, PoisonPill, Props}
import akka.stream.scaladsl.Source
import akka.stream.{Materializer, ThrottleMode}
import com.pi4j.io.gpio._
import com.pi4j.io.gpio.event.GpioPinDigitalStateChangeEvent
import com.pi4j.io.gpio.event.GpioPinListenerDigital
import com.yoppworks.mmwas.devices.UltrasonicMeasurement.ReceiveMeasurement

object UltrasonicMeasurement {

  sealed trait UMDMessages
  case object StartMeasuring extends UMDMessages
  case object StopMeasuring extends UMDMessages
  case object TakeAMeasurement extends UMDMessages
  case class ReceiveMeasurement(value: Long) extends UMDMessages
  case object Unprovision extends UMDMessages

  /** Create an LED Actor.
   * @param device The device to use for this actor.
   * @param temperatures A source of real time temperature data
   * @param sendTo Where to send the results to
   * @return
   */
  def create(
    device: UltraSonicMeasurementDevice,
    measureRate: FiniteDuration,
    sendTo: ActorRef
  )(implicit factory: ActorRefFactory
  ): ActorRef = {

    val props = Props(new UltrasonicMeasurement(device, measureRate, sendTo))
    factory.actorOf(props, device.deviceName)
  }
}

sealed trait UltraSonicMeasurementDevice extends Device {
  def triggerMeasurement(replyTo: ActorRef): Unit
}

case class RandomUltraSonicMeasurementDevice(deviceName: String)
    extends UltraSonicMeasurementDevice {

  def triggerMeasurement(respondTo: ActorRef): Unit = {
    val value = Math.abs(Random.nextLong()) % 1000 + 5
    respondTo ! ReceiveMeasurement(value)
  }

  override def unprovision(): Unit = {
    ()
  }
}

case class GraduatingUltraSonicMeasurementDevice(deviceName: String)
  extends UltraSonicMeasurementDevice {
  
  val limit: Long = 1005L
  val start: Long = 5L
  var current: Long = start;
  
  def triggerMeasurement(respondTo: ActorRef): Unit = {
    current += 1
    if (current > limit) {
      current = start
    }
    respondTo ! ReceiveMeasurement(current)
  }
  
  override def unprovision(): Unit = {
    ()
  }
}

case class ConstantUltrasonicMeasurementDevice(deviceName: String,
  value: Long = 750)
extends UltraSonicMeasurementDevice {
  
  def triggerMeasurement(respondTo: ActorRef): Unit = {
    respondTo ! ReceiveMeasurement(value)
  }
  
  override def unprovision(): Unit = {
    ()
  }
}


case class GPIOUltraSonicMeasurementDevice(
  deviceName: String,
  gpio: GpioController,
  echoPin: Pin,
  trigPin: Pin,
  howLongToWait: FiniteDuration,
  temperatures: Source[Double, NotUsed]
)(implicit
  materializer: Materializer)
    extends UltraSonicMeasurementDevice {

  val echo: GpioPinDigitalInput =
    gpio.provisionDigitalInputPin(echoPin, "Echo", PinPullResistance.PULL_DOWN)

  val trig: GpioPinDigitalOutput =
    gpio.provisionDigitalOutputPin(trigPin, "Trig", PinState.LOW)

  trig.setShutdownOptions(true, PinState.LOW)
  echo.setShutdownOptions(true)

  val listener = EchoListener(echo, temperatures)
  echo.addListener(listener)

  def triggerMeasurement(respondTo: ActorRef): Unit = {
    // Pulse the transmitter for 10 microseconds
    listener.setSender(respondTo)
    trig.low()
    trig.high()
    Thread.sleep(0, 10000)
    trig.low()
  }

  override def unprovision(): Unit = {
    echo.removeListener(listener)
    gpio.unprovisionPin(trig)
    gpio.unprovisionPin(echo)
  }
}

case class EchoListener(
  echoPin: GpioPin,
  temperatures: Source[Double, NotUsed],
)(implicit materializer: Materializer)
    extends GpioPinListenerDigital {

  private var startTime: Long = 0L // microseconds
  private var endTime: Long = 0L
  private var sendTo: ActorRef = ActorRef.noSender
  private var msgCount: Long = 0L
  private var temperatureInCelcius: Double = 0L
  final val MilliMetersPerMillisecond: Long = 331 // speed of sound in mm/msec

  temperatures
    .throttle(
      elements = 1,
      2.seconds,
      maximumBurst = 1000,
      ThrottleMode.Shaping
    )
    .runForeach { temp: Double ⇒
      temperatureInCelcius = temp
    }

  def setSender(actorRef: ActorRef): Unit = sendTo = actorRef

  override def handleGpioPinDigitalStateChangeEvent(
    event: GpioPinDigitalStateChangeEvent
  ): Unit = {
    val pin = event.getPin
    if (pin == echoPin) {
      val pinState = event.getState
      val time = Math.round(System.nanoTime() / 1000D)
      if (pinState == PinState.HIGH) {
        startTime = time
      } else {
        endTime = time
        val duration = endTime - startTime
        val tempIncrement = temperatureInCelcius * 6 / 10
        val speedOfSound: Double = MilliMetersPerMillisecond + tempIncrement
        val distance: Long = Math.round(duration * speedOfSound / 2000D)
        if (msgCount > 0) {
          sendTo ! ReceiveMeasurement(distance)
        }
        msgCount += 1
      }
    } else {
      throw new Exception("Wrong pin for UMD Echo")
    }
  }
}

/** A class for interacting with the HC-SR04 Ultrasonic Distance Device*/
case class UltrasonicMeasurement(
  device: UltraSonicMeasurementDevice,
  measureRate: FiniteDuration,
  sendTo: ActorRef)
    extends DeviceActor {

  import UltrasonicMeasurement._
  final implicit val executionContext: ExecutionContext =
    context.system.dispatcher

  var timerCancellation: Cancellable = Cancellable.alreadyCancelled

  def receive: Receive = {
    case StartMeasuring ⇒
      debug(msg=s"${device.deviceName} starting measurements")
      timerCancellation = context.system.scheduler
        .schedule(0.millis, measureRate, self, TakeAMeasurement)
    case StopMeasuring ⇒
      debug(msg=s"${device.deviceName} stopping measurements")
      if (!timerCancellation.isCancelled) {
        val _ = timerCancellation.cancel()
      }
    case TakeAMeasurement ⇒
      device.triggerMeasurement(self)

    case ReceiveMeasurement(value) ⇒
      sendTo ! value

    case Unprovision ⇒
      device.unprovision()
      self ! PoisonPill
  }
}
