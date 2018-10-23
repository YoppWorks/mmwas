package com.yoppworks.mmwas.devices

import scala.collection.immutable.Seq
import scala.concurrent.ExecutionContext
import scala.concurrent.duration._

import akka.actor.ActorSystem
import akka.stream.{ActorMaterializer, Materializer}
import akka.testkit.{ImplicitSender, TestKit}
import org.scalatest.{BeforeAndAfterAll, Matchers, WordSpecLike}

import com.yoppworks.mmwas.devices.Potentiometer._


/** Unit Tests For PotentiometerTest */
class PotentiometerTest extends TestKit(ActorSystem("PotentiometerTest"))
  with ImplicitSender with WordSpecLike with Matchers with BeforeAndAfterAll {
  
  override def afterAll : Unit = {
    TestKit.shutdownActorSystem(system)
  }
  
  implicit val ec : ExecutionContext = system.dispatcher
  implicit val mat : Materializer = ActorMaterializer()
  
  "Potentiometer" should {
    "provide potentiometer samples at fixed rate" in {
      val device = RandomPotentiometerDevice("TestRandomPotentiometer")
      val potentiometer = Potentiometer.create(device, 100.millis, testActor)
      potentiometer ! StartMeasuring
    
      val values : Seq[ Long ] = receiveWhile[ Long ](2.seconds, messages = 10) {
        case temp : AnyRef ⇒ temp.asInstanceOf[ Long ]
      }
      potentiometer ! StopMeasuring
    
      for (i ← 0 until 10) {
        assert(values(i) > 0, "Value too small")
        assert(values(i) < 100, "Value too big")
      }
    }
  }
}
