package com.yoppworks.mmwas.devices

import scala.concurrent.{Await, ExecutionContext}
import scala.concurrent.duration._
import akka.actor.{ActorRef, ActorSystem}
import akka.pattern.ask
import akka.util.Timeout
import com.yoppworks.mmwas.MusicMaker
import com.yoppworks.mmwas.MakingMusicWithAkkaStreams.MakingMusicConfig
import com.yoppworks.mmwas.MusicMaker.{ChangeInstrument, PlayNote}
import org.scalatest.{BeforeAndAfterAll, Matchers, WordSpecLike}

/** Unit Tests For MusicMakerTest */
class MusicMakerTest extends WordSpecLike with Matchers with BeforeAndAfterAll {
  
  implicit val system: ActorSystem = ActorSystem("MusicMakerTest")
  implicit val ec: ExecutionContext = system.dispatcher
  implicit val timeout: Timeout = Timeout(5.second)
  
  "MusicMakerTest" should {
    val config: MakingMusicConfig = MakingMusicConfig()
    val channel = 0
    val mm: ActorRef = MusicMaker.create(config.soundFont)
    "start playing" in {
      val future = mm ? MusicMaker.StartPlaying
      val result = Await.result(future, 5.seconds)
      assert(result == "MusicMaker Started")
      Thread.sleep(2.seconds.toMillis)
    }
    "play a sequence of notes" in {
      for ( instrument ← 0 to 127) {
        mm ! ChangeInstrument(channel, instrument)
        mm ! PlayNote(channel, 60, 100)
        println(s"Playing: $instrument")
        Thread.sleep(250L)
      }
    }
    "stop playing" in {
      mm ! MusicMaker.StopPlaying
    }
    
  }
}
