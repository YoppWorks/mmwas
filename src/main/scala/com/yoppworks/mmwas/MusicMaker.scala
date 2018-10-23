package com.yoppworks.mmwas

import java.io.File
import java.io.FileInputStream
import java.io.InputStream

import akka.actor.{ActorRef, ActorRefFactory, Props}
import com.sun.media.sound.SF2SoundbankReader
import com.yoppworks.mmwas.devices.DeviceActor
import javax.sound.midi.MidiChannel
import javax.sound.midi.MidiSystem
import javax.sound.midi.Soundbank
import javax.sound.midi.spi.SoundbankReader

object MusicMaker {
  
  sealed trait MusicMakerMessage
  case object InertMusicMakerMessage extends MusicMakerMessage
  case object StartPlaying extends MusicMakerMessage
  case object StopPlaying extends MusicMakerMessage
  case class ChangeInstrument(channel: Int, instrument: Int) extends MusicMakerMessage {
    require(instrument >= -1, "Instrument < -1")
    require(instrument <= 127, "Instrument > 127")
  }
  case class PlayNote(channel: Int, note: Int, velocity: Int, sleep: Long = 0)
    extends MusicMakerMessage {
    require(channel >= 0, "Channel < 0")
    require(channel < 16, "Channel >= 16")
    require(note >= 0, "Note < 0")
    require(note < 128, "Note > 127")
    require(velocity >= -1, "Velocity < 0")
    require(velocity <= 127, "Velocity > 127")
  }
  case class StopNote(channel: Int, note: Int, velocity: Int) extends MusicMakerMessage
  case class StopLastNote(channel:Int) extends MusicMakerMessage
  case class SetTempo(microsecondsPerBeat: Int) extends MusicMakerMessage
  case object IgnoreNotes extends MusicMakerMessage
  case object ResumeNotes extends MusicMakerMessage

  case class SetPitchBend(channel: Int, pitchBend: Int) extends MusicMakerMessage {
    require(channel >= 0, "Channel < 0")
    require(channel < 16, "Channel >= 16")
    require(pitchBend >= 0, "Pitch Bend > 0")
    require(pitchBend <= 16383, "Pitch Bend > 16383" )
  }

  case class SetPolyPressure(channel: Int, note: Int, pressure: Int) extends MusicMakerMessage {
    require(channel >= 0, "Channel < 0")
    require(channel < 16, "Channel >= 16")
    require(note >= 0, "Note < 0")
    require(note < 128, "Note > 127")
    require(pressure >= 0, "Poly Pressure < 0")
    require(pressure <= 127, "Poly Pressure > 127" )
  }

  def create(soundFont: String)(implicit
    factory: ActorRefFactory)
  : ActorRef = {
    val props = Props(new MusicMaker(soundFont))
    factory.actorOf(props, "Music-Maker")
  }
}

class MusicMaker(soundFont: String) extends
  DeviceActor {

  import MusicMaker._

  private val midiSynth = MidiSystem.getSynthesizer
  private val deviceInfo = midiSynth.getDeviceInfo
  log.info("\n" +
    s"""Synth Name = ${deviceInfo.getName}
       |Synth Vendor = ${deviceInfo.getVendor}
       |Synth Version = ${deviceInfo.getVersion}
       |Synth Description = ${deviceInfo.getDescription}
       |Maximum Polyphony = ${midiSynth.getMaxPolyphony}
       |Latency = ${midiSynth.getLatency}
       |Num Instruments = ${midiSynth.getAvailableInstruments.length}
       |Num Channels = ${midiSynth.getChannels.length}
       |""".stripMargin
  )

  midiSynth.open()
  private val defaultSoundBank = midiSynth.getDefaultSoundbank
  midiSynth.unloadAllInstruments(defaultSoundBank)

  val soundBank: Soundbank = {
    if (soundFont.isEmpty) {
      midiSynth.getDefaultSoundbank
    } else {
      val stream: InputStream =Option(getClass.getClassLoader.getResourceAsStream(soundFont)) match {
        case Some(inputStream) =>
          inputStream
        case None =>
          val soundFile: File = new File(soundFont)
          if (soundFile.exists()) {
            new FileInputStream(soundFile)
          } else {
            val homeSoundFile: File = new File(System.getProperty("user.home"), soundFont)
            if (homeSoundFile.exists()) {
              new FileInputStream(homeSoundFile)
            } else {
              throw new IllegalArgumentException(
                s"$soundFont file does not exist in cwd or home folders.")
            }
          }
      }
      val reader: SoundbankReader = new SF2SoundbankReader
      val soundBank: Option[Soundbank] = Option(reader.getSoundbank(stream))
      require(soundBank.nonEmpty, "Soundbank not loaded")
      soundBank.get
    }
  }

  require(midiSynth.isSoundbankSupported(soundBank), "Soundbank not supported")
  log.info(
    s"""\nSoundBank Name:  ${soundBank.getName}
       |SoundBank Vendor = ${soundBank.getVendor}
       |Synth Version = ${soundBank.getVersion}
       |Synth Description = ${soundBank.getDescription}
      """.stripMargin
  )

  private val instrumentsLoaded = midiSynth.loadAllInstruments(soundBank)
  require(instrumentsLoaded,
    s"No instruments loaded from soundbank ${soundBank.getName}")
  private val instruments = midiSynth.getLoadedInstruments
  private val mChannels: Array[MidiChannel] = midiSynth.getChannels

  log.info("Instruments loaded:\n" + instruments
    .map(_.getName)
    .zipWithIndex
    .map{
      case (s:String,i:Int)⇒ i.toString + ":" + s
    }
    .grouped(8)
    .map(_.mkString(", "))
    .mkString("\n") + "\n")

  private var ignore: Boolean = false
  val lastNotes = scala.collection.mutable.ArrayBuffer[Int](
    0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0
  )
  val pauses = scala.collection.mutable.ArrayBuffer[Int](
    0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0
  )

  override def receive : Receive = {
    case StartPlaying ⇒
      log.info("Music Maker: Starting to play")
      for (chan <-0 until 15) {
        val midiChannel = mChannels(chan)
        midiChannel.programChange(0)
        midiChannel.noteOn(60, 127)
        lastNotes(chan) = 60
        Thread.sleep(50)
        midiChannel.noteOff(60, 127)
      }
      sender() ! "MusicMaker Started"

    case ChangeInstrument(channel, instrument: Int) =>
      if (instrument >=  0 && instrument < instruments.length) {
        val theChannel = mChannels(channel)
        theChannel.programChange(instrument)
        if (theChannel.getProgram != instrument) {
          log.error(s"Channel does not support program #$instrument")
        }
      }

    case PlayNote(channel: Int, note: Int, velocity: Int, sleep: Long) ⇒
      if (!ignore) {
        // log.info(s"Note: $x")
        if (sleep > 0) {
          Thread.sleep(sleep)
        }
        val theChannel = mChannels(channel)
        theChannel.noteOff(lastNotes(channel))
        theChannel.noteOn(note, velocity)
        lastNotes(channel) = note
      }

    case StopLastNote(channel:Int) =>
      val theChannel = mChannels(channel)
      val note = lastNotes(channel)
      theChannel.noteOff(note)

    case StopNote(channel: Int, note: Int, _: Int) =>
      if (!ignore) {
        val theChannel = mChannels(channel)
        theChannel.noteOff(note)
      }

    case IgnoreNotes =>
      for ( chan <- mChannels) { chan.allSoundOff() }
      ignore = true

    case ResumeNotes =>
      ignore = false

    case SetPitchBend(channel, pitchBend) ⇒
      mChannels(channel).setPitchBend(pitchBend)

    case SetPolyPressure(channel, note, pressure) ⇒
      mChannels(channel).setPolyPressure(note, pressure)

    case StopPlaying ⇒
      log.info("Music Maker: closing synthesizer")
      midiSynth.close()

    case InertMusicMakerMessage =>
      // ignore

  }
}
