package com.yoppworks.mmwas.devices

import scala.concurrent.ExecutionContext
import scala.concurrent.duration._

import akka.actor.ActorSystem
import akka.stream.testkit.TestSubscriber.{OnError, OnNext}
import akka.stream.testkit.scaladsl.TestSink
import akka.stream.{ActorMaterializer, Materializer}
import akka.testkit.{ImplicitSender, TestKit}
import com.yoppworks.mmwas.MakingMusicWithAkkaStreams.MakingMusicConfig
import org.scalatest.{BeforeAndAfterAll, Matchers, WordSpecLike}

/** Unit Tests For PotentiometerTest */
class UltrasonicMeasurementTest extends TestKit(ActorSystem("UltrasonicMeasurementTest"))
  with ImplicitSender with WordSpecLike with Matchers with BeforeAndAfterAll {
  
  override def afterAll : Unit = {
    TestKit.shutdownActorSystem(system)
  }
  
  implicit val ec : ExecutionContext = system.dispatcher
  implicit val mat : Materializer = ActorMaterializer()
  val config: MakingMusicConfig = MakingMusicConfig()
  
  "UltrasonicMeasurement" should {
    "provide distance samples at fixed rate" in {
      val dm = MockDeviceManager(config)
      dm.pitchActor ! UltrasonicMeasurement.StartMeasuring
      
      val values = dm.pitches
        .runWith(TestSink.probe[Long])
        .request(10)
        .receiveWhile[Long](2.seconds, messages = 10) {
          case on: OnNext[ Long ] @unchecked ⇒ on.element
          case oe: OnError ⇒ throw oe.cause
      }
      
      dm.pitchActor  ! UltrasonicMeasurement.StopMeasuring
    
      for (i ← 0 until 10) {
        assert(values(i) >= 5.0, "Value too small")
        assert(values(i) <= 1005.0, "Value too big")
      }
    }
  }
}
