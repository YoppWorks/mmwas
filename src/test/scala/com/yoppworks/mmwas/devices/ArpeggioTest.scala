package com.yoppworks.mmwas.devices

import scala.concurrent.{Await, ExecutionContext}
import scala.concurrent.duration._
import akka.actor.{ActorRef, ActorSystem}
import akka.pattern.ask
import akka.util.Timeout
import com.yoppworks.mmwas.MusicMaker
import com.yoppworks.mmwas.MakingMusicWithAkkaStreams.MakingMusicConfig
import com.yoppworks.mmwas.MusicMaker.{ChangeInstrument, PlayNote}
import org.scalatest.WordSpec

/** Unit Tests For ArpeggioTest */
class ArpeggioTest extends WordSpec {

  implicit val system: ActorSystem = ActorSystem("MusicMakerTest")
  implicit val ec: ExecutionContext = system.dispatcher
  implicit val timeout: Timeout = Timeout(5.second)

  "Arpeggio" should {
    val config: MakingMusicConfig = MakingMusicConfig(soundFont = "julie_M42_full.sf2")
    val channel = 0
    val mm: ActorRef = MusicMaker.create(config.soundFont)
    "play arpeggios" in {
      val future = mm ? MusicMaker.StartPlaying
      val result = Await.result(future, 5.seconds)
      assert(result == "MusicMaker Started")
      Thread.sleep(1.seconds.toMillis)
      val s1 = for (note ← 60 to 71) yield {
        PlayNote(channel, note, 100)
      }
      val s2 = for (note ← 71.to(60, -1)) yield {
        PlayNote(channel, note, 100)
      }
      mm ! ChangeInstrument(channel, 80)
      (s1 ++ s2).foreach { note ⇒
        println(s"Playing: $note ")
        mm ! note
        Thread.sleep(250)
      }
    }
  }
}
