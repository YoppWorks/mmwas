package com.yoppworks.mmwas

import scala.collection.JavaConverters._
import com.pi4j.io.gpio.GpioController

package object devices {
  
  implicit class GPIOUtilities(gpio: GpioController) {
  
    def showProvisionedPins() : Unit = {
      println("GPIO is Shutdown: " + safeB(gpio.isShutdown))
      val pins = gpio.getProvisionedPins.asScala
      println(s"# Provisioned Pins = ${pins.size}")
      pins.foreach {pin ⇒
        println(s"${pin.getName} -> ${pin.getPin} is ${pin.getMode} (ohm=${
          pin
            .getPullResistance
        })")
        pin.getProperties.asScala.foreach {props ⇒
          println(s"  ${props._1} = ${props._2}")
        }
      }
    }
  
    private def safeS(f : ⇒ String) : String = {
      try {
        f
      } catch {
        case _ : UnsupportedOperationException ⇒ "Unsupported"
        case t : Throwable ⇒ s"Error: ${t.getClass.getName}: ${t.getMessage}"
      }
    }
  
    private def safeL(f : ⇒ Long) : String = {
      try {
        f.toString
      } catch {
        case _ : UnsupportedOperationException ⇒ "Unsupported"
        case t : Throwable ⇒ s"Error: ${t.getClass.getName}: ${t.getMessage}"
      }
    }
  
    private def safeF(f : ⇒ Float) : String = {
      try {
        f.toString
      } catch {
        case _ : UnsupportedOperationException ⇒ "Unsupported"
        case t : Throwable ⇒ s"Error: ${t.getClass.getName}: ${t.getMessage}"
      }
    }
  
    private def safeB(f : ⇒ Boolean) : String = {
      try {
        f.toString
      } catch {
        case _ : UnsupportedOperationException ⇒ "Unsupported"
        case t : Throwable ⇒ s"Error: ${t.getClass.getName}: ${t.getMessage}"
      }
    }
  
    def printSystemInfo() : Unit = {
      import com.pi4j.platform.PlatformManager
      import com.pi4j.system.NetworkInfo
      import com.pi4j.system.SystemInfo
      println(
        "Platform Name     :  " + safeS(PlatformManager.getPlatform.getLabel))
      println(
        "Platform ID       :  " + safeS(PlatformManager.getPlatform.getId))
    
      println("----------------------------------------------------")
      println("HARDWARE INFO")
      println("----------------------------------------------------")
      println("Serial Number     :  " + safeS(SystemInfo.getSerial))
      println("CPU Revision      :  " + safeS(SystemInfo.getCpuRevision))
      println("CPU Architecture  :  " + safeS(SystemInfo.getCpuArchitecture))
      println("CPU Part          :  " + safeS(SystemInfo.getCpuPart))
      println("CPU Temperature   :  " + safeF(SystemInfo.getCpuTemperature))
      println("CPU Core Voltage  :  " + safeF(SystemInfo.getCpuVoltage))
      println("CPU Model Name    :  " + safeS(SystemInfo.getModelName))
      println("Processor         :  " + safeS(SystemInfo.getProcessor))
      println("Hardware          :  " + safeS(SystemInfo.getHardware))
      println("Hardware Revision :  " + safeS(SystemInfo.getRevision))
      println("Is Hard Float ABI :  " + safeB(SystemInfo.isHardFloatAbi))
      println("Board Type        :  " + safeS(SystemInfo.getBoardType.name))
    
      println("----------------------------------------------------")
      println("MEMORY INFO")
      println("----------------------------------------------------")
      println("Total Memory      :  " + safeL(SystemInfo.getMemoryTotal))
      println("Used Memory       :  " + safeL(SystemInfo.getMemoryUsed))
      println("Free Memory       :  " + safeL(SystemInfo.getMemoryFree))
      println("Shared Memory     :  " + safeL(SystemInfo.getMemoryShared))
      println("Memory Buffers    :  " + safeL(SystemInfo.getMemoryBuffers))
      println("Cached Memory     :  " + safeL(SystemInfo.getMemoryCached))
      println(
        "SDRAM_C Voltage   :  " + safeF(SystemInfo.getMemoryVoltageSDRam_C))
      println(
        "SDRAM_I Voltage   :  " + safeF(SystemInfo.getMemoryVoltageSDRam_I))
      println(
        "SDRAM_P Voltage   :  " + safeF(SystemInfo.getMemoryVoltageSDRam_P))
    
      println("----------------------------------------------------")
      println("OPERATING SYSTEM INFO")
      println("----------------------------------------------------")
      println("OS Name           :  " + safeS(SystemInfo.getOsName))
      println("OS Version        :  " + safeS(SystemInfo.getOsVersion))
      println("OS Architecture   :  " + safeS(SystemInfo.getOsArch))
      println("OS Firmware Build :  " + safeS(SystemInfo.getOsFirmwareBuild))
      println("OS Firmware Date  :  " + safeS(SystemInfo.getOsFirmwareDate))
    
      println("----------------------------------------------------")
      println("JAVA ENVIRONMENT INFO")
      println("----------------------------------------------------")
      println("Java Vendor       :  " + safeS(SystemInfo.getJavaVendor))
      println("Java Vendor URL   :  " + safeS(SystemInfo.getJavaVendorUrl))
      println("Java Version      :  " + safeS(SystemInfo.getJavaVersion))
      println("Java VM           :  " + safeS(SystemInfo.getJavaVirtualMachine))
      println("Java Runtime      :  " + safeS(SystemInfo.getJavaRuntime))
    
      println("----------------------------------------------------")
      println("NETWORK INFO")
      println("----------------------------------------------------")
    
      // display some of the network information
      println("Hostname          :  " + safeS(NetworkInfo.getHostname))
      for (ipAddress <- NetworkInfo.getIPAddresses) {
        println("IP Addresses      :  " + ipAddress)
      }
      for (fqdn <- NetworkInfo.getFQDNs) {
        println("FQDN              :  " + fqdn)
      }
      for (nameserver <- NetworkInfo.getNameservers) {
        println("Nameserver        :  " + nameserver)
      }
    
      println("----------------------------------------------------")
      println("CODEC INFO")
      println("----------------------------------------------------")
      println("H264 Codec Enabled:  " + safeB(SystemInfo.getCodecH264Enabled))
      println("MPG2 Codec Enabled:  " + safeB(SystemInfo.getCodecMPG2Enabled))
      println("WVC1 Codec Enabled:  " + safeB(SystemInfo.getCodecWVC1Enabled))
    
      println("----------------------------------------------------")
      println("CLOCK INFO")
      println("----------------------------------------------------")
      println("ARM Frequency     :  " + safeL(SystemInfo.getClockFrequencyArm))
      println("CORE Frequency    :  " + safeL(SystemInfo.getClockFrequencyCore))
      println("H264 Frequency    :  " + safeL(SystemInfo.getClockFrequencyH264))
      println("ISP Frequency     :  " + safeL(SystemInfo.getClockFrequencyISP))
      println("V3D Frequency     :  " + safeL(SystemInfo.getClockFrequencyV3D))
      println("UART Frequency    :  " + safeL(SystemInfo.getClockFrequencyUART))
      println("PWM Frequency     :  " + safeL(SystemInfo.getClockFrequencyPWM))
      println("EMMC Frequency    :  " + safeL(SystemInfo.getClockFrequencyEMMC))
      println(
        "Pixel Frequency   :  " + safeL(SystemInfo.getClockFrequencyPixel))
      println("VEC Frequency     :  " + safeL(SystemInfo.getClockFrequencyVEC))
      println("HDMI Frequency    :  " + safeL(SystemInfo.getClockFrequencyHDMI))
      println("DPI Frequency     :  " + safeL(SystemInfo.getClockFrequencyDPI))
      println()
    }
  }
}
