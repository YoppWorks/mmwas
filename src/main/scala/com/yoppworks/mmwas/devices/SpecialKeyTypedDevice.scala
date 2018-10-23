package com.yoppworks.mmwas.devices

import java.awt.event.{InputEvent, KeyEvent}
import java.io.InputStream

import akka.stream.scaladsl.{Concat, Source}
import akka.NotUsed
import com.sun.media.sound.MidiUtils
import com.yoppworks.mmwas.MusicMaker._
import javax.sound.midi._

import scala.util.Random
import scala.concurrent.duration._
import scala.collection.immutable

case class SpecialKeyTypedDevice(input: ScreenInput, channel: Int) extends DeviceSource[MusicMakerMessage] {
  val deviceName: String = "Special Key Typed"
  val minValue: MusicMakerMessage = StartPlaying
  val maxValue: MusicMakerMessage = StopPlaying

  val runningProgram: Int = 0

  override def dataSource: Source[MusicMakerMessage, NotUsed] = {
    input
      .keyTypedSource
      .async
      .flatMapConcat[MusicMakerMessage, NotUsed] { e: KeyEvent =>
      val ctrl_mask = e.getModifiersEx & InputEvent.CTRL_DOWN_MASK
      if (ctrl_mask != 0) {
        e.getKeyChar match {
          case '0' => stopRunningProgram()
          case '1' => runProgram(1)
          case '2' => runProgram(2)
          case '3' => runProgram(3)
          case '4' => runProgram(4)
          case '5' => runProgram(5)
          case '6' => runProgram(6)
          case '7' => runProgram(7)
          case '8' => runProgram(8)
          case '9' => runProgram(9)
          case '\u0011' => // Ctrl-Q
            Source.empty[MusicMakerMessage]

          case '\u0012' => // Ctrl-R
            val instrument = Random.nextInt(127)
            val setInstrument = Source
              .single[MusicMakerMessage](
                ChangeInstrument(channel, instrument)
              )
            val notes = Source
              .tick(0.millis, 10.millis, ())
              .limit(1000)
              .map { _ : Any =>
                val note = Random.nextInt(78) + 32
                val velocity = Random.nextInt(50) + 25
                PlayNote(channel, note, velocity)
              }
            val stopChannel = Source
              .single[MusicMakerMessage](StopLastNote(channel))
            Source
              .combine(setInstrument,notes,stopChannel)(Concat(_))
          case _ =>
            Source.empty[MusicMakerMessage]
        }
      } else {
        Source.empty[MusicMakerMessage]
      }
    }
  }

  final val microsPerMinute = 60000000

  def runProgram(i: Int): Source[MusicMakerMessage, NotUsed] = {
    val midi_file = i match {
      case 1 => "row_row_row_4fl.mid"
      case 2 => "SomewhereOverTheRainbow.mid"
      case 3 => "overrain.mid"
      case 4 => "Dingdong.mid"
      case _ => ""
    }
    if (midi_file.length > 0) {
      val sequencer: Sequencer = MidiSystem.getSequencer()
      sequencer.open()
      val is: InputStream =
        Thread.currentThread()
          .getContextClassLoader
          .getResourceAsStream(midi_file)
      sequencer.setSequence(is)
      val sequence = sequencer.getSequence
      sequencer.start()
      var tempo = 500000
      var timeSigNumerator = 4
      var timeSigDenominator = 4
      val ticksPerQuarterNote = sequence.getResolution

      def sleepTicks(lastTick: Long, nextTick: Long): Long = {
        val deltaTime = nextTick - lastTick
        val kSecondsPerTick: Float = deltaTime / (ticksPerQuarterNote * 1000000.0f )
        (deltaTime * kSecondsPerTick * 1000.0F).toLong
      }

      val events: Seq[MidiEvent] = {
        val builder = Seq.newBuilder[MidiEvent]
        val tracks = sequence.getTracks.toSeq
        println(s"Tracks to process: ${tracks.size}: ${
          tracks.map(_.size().toString).mkString(", ")}")
        for {
          track <- tracks
          index <- 0 until track.size()
        } {
          // println(s"event = $track($index)")
          builder += track.get(index)
        }
        builder.result
      }.sortBy(_.getTick)

      case class Trigger()

      val eventSource = Source
        .apply[MidiEvent]( events.to[immutable.Seq])

      val tickSleeper = eventSource.scan((0L,0L)) {
        case x: ((Long,Long),MidiEvent) =>
          val tick = x._1._1
          val event = x._2
          val eventTick = event.getTick
          val len = sleepTicks(tick, eventTick)
          eventTick -> len
      }

      eventSource
        .zipWith(tickSleeper)((event, tick) => event -> tick._2 )
        .map { case (me: MidiEvent, sleep: Long)  =>
          me.getMessage match {
            case sm: ShortMessage =>
              val chan = channel + sm.getChannel
              sm.getCommand match {
                case ShortMessage.NOTE_ON =>
                  val note = sm.getData1
                  val velocity = sm.getData2
                  if (velocity == 0)
                    StopNote(chan, note, velocity)
                  else if (sleep == 0) {
                    PlayNote(chan, note, velocity)
                  } else {
                    PlayNote(chan, note, velocity, sleep)
                  }
                case ShortMessage.NOTE_OFF =>
                  StopNote(chan, sm.getData1, sm.getData2)
                case ShortMessage.PITCH_BEND =>
                  SetPitchBend(chan, sm.getData1)
                case ShortMessage.POLY_PRESSURE =>
                  SetPolyPressure(chan, sm.getData1, sm.getData2)
                case ShortMessage.PROGRAM_CHANGE =>
                  ChangeInstrument(chan, sm.getData1)
                case _ =>
                  InertMusicMakerMessage
              }
            case mm: MetaMessage =>
              mm.getType match {
                case MidiUtils.META_END_OF_TRACK_TYPE =>
                  InertMusicMakerMessage
                case MidiUtils.META_TEMPO_TYPE =>
                  val data = mm.getData
                  tempo = (data(0).toInt << 16) + (data(1).toInt << 8) + data(2).toInt
                  InertMusicMakerMessage
                case 0x58 => // Time Signature
                  val data = mm.getData
                  timeSigNumerator = data(0).toInt & 0xFF
                  timeSigDenominator = data(1).toInt & 0xFF
                  InertMusicMakerMessage
                case _ =>
                  InertMusicMakerMessage
              }
            case _ =>
              InertMusicMakerMessage
          }
        }
        .filterNot{ _ == InertMusicMakerMessage }
        .map{ msg => println(msg); msg }
    } else {
      Source.empty[MusicMakerMessage]
    }
  }

  def stopRunningProgram() : Source[MusicMakerMessage, NotUsed] = {
    Source.empty[MusicMakerMessage]
  }
}
