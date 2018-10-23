package com.yoppworks.mmwas.devices

import com.pi4j.io.i2c._
import java.io.IOException

import com.pi4j.util.Console


case class PCF8591(console: Console) {
  
  private val PCF8591Address: Byte = 0x48
  private val IN0_potentiometer: Byte = 0x00
  private val IN1_thermistor: Byte = 0x01
  private val IN2_photoresistor: Byte = 0x02
  private val IN3_unused: Byte = 0x03
  private val AUTO_INCREMENT_FLAG: Int = 0x04
  private val ENABLE_OUTPUT_FLAG: Int = 0x40
  private val FOUR_SINGLE_ENDED_INPUTS:Int = 0x00
  private val bus: I2CBus = I2CFactory.getInstance(I2CBus.BUS_1)
  private val device: I2CDevice = bus.getDevice(PCF8591Address.toInt)
  private val buffers = Array.ofDim[Byte](4,2) // statically allocate buffers
  
  Thread.sleep(3000)
  
  private def readChannel(
    channel: Byte,
    autoIncrement: Boolean = false,
    enableOutput: Boolean = false
  ): Int = {
  
    try {
      val controlByte: Byte = {
        ((if (autoIncrement) AUTO_INCREMENT_FLAG else 0) |
        (if (enableOutput) ENABLE_OUTPUT_FLAG else 0) |
        (FOUR_SINGLE_ENDED_INPUTS | channel)).toByte
      }
      device.write(controlByte)
      val readBuffer: Array[Byte] =  buffers(channel.toInt)
      device.read(readBuffer, 0, 2)
      readBuffer(1) & 0x0FF
    } catch {
      case xcptn : IOException ⇒
        console.println("PCF8591 I/O Error: " + xcptn.getClass.getName +
        ": " + xcptn.getMessage)
        0
    }
  }
  
  def read_potentiometer: Int = readChannel(IN0_potentiometer)
  def read_thermistor: Int = readChannel(IN1_thermistor)
  def read_photoresistor: Int = readChannel(IN2_photoresistor)
  def read_channel3: Int = readChannel(IN3_unused)
}

object PCF8591 {
  def main(args : Array[String]) : Unit = {
    val console = new Console
    
    val adc = PCF8591(console)
    console.println("Potentiometer: " + adc.read_potentiometer)
    console.println("Thermistor   : " + adc.read_thermistor)
    console.println("Photoresistor:" + adc.read_photoresistor)
    ()
  }
}
