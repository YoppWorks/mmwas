package com.yoppworks.mmwas.devices

import scala.collection.immutable.Seq
import scala.concurrent.ExecutionContext
import scala.concurrent.duration._

import akka.actor.ActorSystem
import akka.stream.{ActorMaterializer, Materializer}
import akka.testkit.{ImplicitSender, TestKit}
import com.yoppworks.mmwas.devices.Thermistor.{StartMeasuring, StopMeasuring}
import org.scalatest.{BeforeAndAfterAll, Matchers, WordSpecLike}

/** Unit Tests For Theremin */
class ThermistorTest extends TestKit(ActorSystem("ThermistorTest"))
  with ImplicitSender with WordSpecLike with Matchers with BeforeAndAfterAll {
  
  override def afterAll: Unit = {
    TestKit.shutdownActorSystem(system)
  }
  
  implicit val ec: ExecutionContext = system.dispatcher
  implicit val mat: Materializer = ActorMaterializer()
  
  
  "Thermistor" should {
    "provide temperature samples at fixed rate" in {
      val device = RandomThermistorDevice("TestRandomThermistor")
      val thermistor = Thermistor.create(device, 100.millis, testActor)
      thermistor ! StartMeasuring
      
      val values: Seq[Double] = receiveWhile[Double](2.seconds, messages=10) {
          case temp: AnyRef ⇒ temp.asInstanceOf[Double]
      }
      thermistor ! StopMeasuring
      
      for (i ← 0 until 10) {
        assert( values(i) > 5, "Value too small")
        assert( values(i) < 50, "Value too big")
      }
    }
  }
}
