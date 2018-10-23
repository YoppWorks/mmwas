package com.yoppworks.mmwas.devices

import scala.concurrent.duration._
import scala.concurrent.{Future, Promise}
import scala.util.Success

import akka.actor.{ActorRef, ActorRefFactory, PoisonPill, Props}
import akka.stream.{Materializer, OverflowStrategy}
import akka.{Done, NotUsed}
import akka.stream.scaladsl.{Sink, Source}
import com.pi4j.io.gpio._

/** An Actor for interacting with the HC-SR04 Ultrasonic Distance Device via
  * an Akka Stream Source. LEDMessages are sent to the Actor.
  *
  */
object LightEmittingDiode {

  sealed trait LEDMessages

  /** Turn the LED on for a set duration */
  case class LEDOn(duration: Long) extends LEDMessages

  /** Turn the LED off for a set duration */
  case class LEDOff(duration: Long) extends LEDMessages

  /** Turn the lED off and terminate the actor */
  case object EndOfInput extends LEDMessages

  /** Create an LED Actor.
    * @param gpio the GpioController to use
    * @param pin The RaspiPin to use
    * @param maxDutyCycle The maximum time the LED should be on
    * @param factory Implicit ActorRefFactory for creating actors
    * @return
    */
  def create(gpio: GpioController,
             pin: Pin,
             maxDutyCycle: FiniteDuration = 60.seconds)(
      implicit factory: ActorRefFactory): (ActorRef,
    Future[Done]) = {
    val promise = Promise[Done]
    val props = Props(
      new LightEmittingDiode(gpio, pin, maxDutyCycle, promise))
    factory.actorOf(props, "LED") → promise.future
  }

  def run(ledActor: ActorRef, messageSource: Source[LEDMessages, NotUsed])(
      implicit mat: Materializer): Unit = {
    val sink = Sink.actorRef[LEDMessages](ledActor, EndOfInput)
    val _ = messageSource.buffer(1000,OverflowStrategy.dropHead).runWith(sink)
  }
}

/** Implementation of the LED Actor
  *
  * @param gpio the GpioController to use
  * @param pin The RaspiPin to use
  * @param maxDutyCycle The maximum time the LED should be on
  * @param promise Promise to complete upon termination of this actor
  */
case class LightEmittingDiode(gpio: GpioController,
                                   pin: Pin,
                                   maxDutyCycle: FiniteDuration,
                                   promise: Promise[Done])
    extends DeviceActor {
  import LightEmittingDiode._

  private val outPin: GpioPinDigitalOutput =
    gpio.provisionDigitalOutputPin(pin, "LED", PinState.LOW)

  outPin.setShutdownOptions(true, PinState.LOW)

  override def postStop(): Unit = {
    outPin.low()
    gpio.unprovisionPin(outPin)
    promise.complete(Success(Done))
    super.postStop()
  }

  def receive: Receive = {
    case LEDOn(duration) ⇒
      val d = Math.min(duration, maxDutyCycle.toMillis)
      log.debug(s"LED On : $d")
      outPin.high()
      if (d > 0)
        Thread.sleep(d)

    case LEDOff(duration) ⇒
      val d = Math.min(duration, maxDutyCycle.toMillis)
      log.debug(s"LED Off: $d")
      outPin.low()
      if (d > 0)
        Thread.sleep(d)
    case EndOfInput ⇒
      outPin.low()
      self ! PoisonPill
    case akka.actor.Status.Failure ⇒
    
  }
}
