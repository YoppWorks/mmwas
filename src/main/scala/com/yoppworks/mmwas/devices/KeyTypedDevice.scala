package com.yoppworks.mmwas.devices

import java.awt.event.KeyEvent

import akka.NotUsed
import akka.stream.scaladsl.Source
import com.yoppworks.mmwas.MusicMaker._

import scala.util.Random

case class KeyTypedDevice(input: ScreenInput, channel: Int) extends DeviceSource[MusicMakerMessage] {
  val deviceName: String = "Key Typed"
  val minValue: MusicMakerMessage = StartPlaying
  val maxValue: MusicMakerMessage = StopPlaying

  var octave: Int = 0
  var volume: Int = 64
  var pitchBend: Int = 8192
  val pitchBendQuanta = 82

  override def dataSource: Source[MusicMakerMessage, NotUsed] = {
    input.keyTypedSource.map[MusicMakerMessage] { event: KeyEvent =>
      event.getKeyChar match {
        case '+' => octave += 12; InertMusicMakerMessage
        case '-' => octave -= 12; InertMusicMakerMessage
        case '1' => ChangeInstrument(channel, 0)
        case '2' => ChangeInstrument(channel, 1)
        case '3' => ChangeInstrument(channel, 2)
        case '4' => ChangeInstrument(channel, 3)
        case '5' => ChangeInstrument(channel, 4)
        case '6' => ChangeInstrument(channel, 5)
        case '7' => ChangeInstrument(channel, 6)
        case '8' => ChangeInstrument(channel, 7)
        case '9' => ChangeInstrument(channel, 8)
        case '0' => ChangeInstrument(channel, 9)
        case '\u0001' => PlayNote(channel, 56 + octave, volume) // Ab
        case 'a' => PlayNote(channel, 57 + octave, volume) // A
        case 'A' => PlayNote(channel, 58 + octave, volume) // A#
        case '\u0002' => PlayNote(channel, 58 + octave, volume) // Bb
        case 'b' => PlayNote(channel,  59 + octave, volume) // B
        case 'B' => PlayNote(channel, 60 + octave, volume) // B#
        case '\u0003' => PlayNote(channel, 59 + octave, volume) // Cb
        case 'c' => PlayNote(channel, 60 + octave, volume) // C
        case 'C' => PlayNote(channel, 61 + octave, volume) // C#
        case '\u0004' => PlayNote(channel, 61 + octave, volume) // Db
        case 'd' => PlayNote(channel, 62 + octave, volume) // D
        case 'D' => PlayNote(channel, 63 + octave, volume) // D#
        case '\u0005' => PlayNote(channel, 63 + octave, volume) // Eb
        case 'e' => PlayNote(channel, 64 + octave, volume) // E
        case 'E' => PlayNote(channel, 65 + octave, volume) // E#
        case '\u0006' => PlayNote(channel, 64 + octave, volume) // Fb
        case 'f' => PlayNote(channel, 65 + octave, volume) // F
        case 'F' => PlayNote(channel, 66 + octave, volume) // F#
        case '\u0007' => PlayNote(channel, 66 + octave, volume) // Gb
        case 'g' => PlayNote(channel, 67 + octave, volume) // G
        case 'G' => PlayNote(channel, 68 + octave, volume) // G#
        case '\u0008' => PlayNote(channel, 68 + octave, volume) // Ab
        case 'h' => PlayNote(channel, 69 + octave, volume) // A
        case 'H' => PlayNote(channel, 70 + octave, volume) // A#
        case '\u0009' => PlayNote(channel, 70 + octave, volume) // Bb
        case 'i' => PlayNote(channel, 71 + octave, volume) // B
        case 'I' => PlayNote(channel, 72 + octave, volume) // B#
        case '\u000A' => PlayNote(channel, 71 + octave, volume) // Cb
        case 'j' => PlayNote(channel, 72 + octave, volume) // C
        case 'J' => PlayNote(channel, 73 + octave, volume) // C#
        case '\u000B' => PlayNote(channel, 73 + octave, volume) // Db
        case 'k' => PlayNote(channel, 74 + octave, volume) // D
        case 'K' => PlayNote(channel, 75 + octave, volume) // D#
        case '\u000C' => PlayNote(channel, 75 + octave, volume) // Eb
        case 'l' => PlayNote(channel, 76 + octave, volume) // E
        case 'L' => PlayNote(channel, 77 + octave, volume) // E#
        case '\u000D' => PlayNote(channel, 76 + octave, volume) // Fb
        case 'm' => PlayNote(channel, 77 + octave, volume) // F
        case 'M' => PlayNote(channel, 78 + octave, volume) // F#
        case '\u000E' => PlayNote(channel, 78 + octave, volume) // Gb
        case 'n' => PlayNote(channel, 79 + octave, volume) // G
        case 'N' => PlayNote(channel, 80 + octave, volume) // G#
        case '\u000F' => PlayNote(channel, 80 + octave, volume) // Ab
        case 'o' => PlayNote(channel, 81 + octave, volume) // A
        case 'O' => PlayNote(channel, 82 + octave, volume) // A#
        case '\u001B' =>
          StopLastNote(channel)
        case '\u007F' =>
          pitchBend = 8192
          SetPitchBend(channel, pitchBend)
        case _ =>
          event.getKeyCode match {
            case KeyEvent.VK_UP | KeyEvent.VK_KP_UP =>
              pitchBend += Math.min(pitchBend + pitchBendQuanta, 16384)
              SetPitchBend(channel, pitchBend)
            case KeyEvent.VK_DOWN | KeyEvent.VK_KP_DOWN =>
              pitchBend = Math.max(pitchBend - pitchBendQuanta, 0)
              SetPitchBend(channel, pitchBend)
            case KeyEvent.VK_LEFT =>
              volume -= 16
              InertMusicMakerMessage
            case KeyEvent.VK_KP_LEFT =>
              volume -= 16
              InertMusicMakerMessage
            case KeyEvent.VK_RIGHT =>
              volume += 16
              InertMusicMakerMessage
            case KeyEvent.VK_KP_RIGHT =>
              volume += 16
              InertMusicMakerMessage
            case KeyEvent.VK_F1 =>
              PlayNote(0, Random.nextInt(78) + 32, volume)
            case _ =>
              InertMusicMakerMessage
          }
        }
    }.filterNot(_ == InertMusicMakerMessage)
  }
}
