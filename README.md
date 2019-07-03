# RFLink binding for OpenHAB 2.0

[![Build Status](https://travis-ci.org/cyrilcc/org.openhab.binding.rflink.svg?branch=master)](https://travis-ci.org/cyrilcc/org.openhab.binding.rflink)

This repository contains a binding for [OpenHAB 2.0](https://github.com/openhab/openhab-distro) Home Automation solution, that adds support for the [RFLink Gateway](http://www.rflink.nl/).

This binding is inspired by the [Rfxcom binding](https://github.com/openhab/openhab2-addons/tree/master/addons/binding/org.openhab.binding.rfxcom)

## RfLink Gateway
RFLink is a Radio Frequency Gateway, aimed to communicate with objects using RF protocols like :
* Cresta 
* La Crosse
* OWL
* Oregon
* Somfy RTS
* ...

The official supported devices list is available here : [http://www.rflink.nl/blog2/devlist](http://www.rflink.nl/blog2/devlist)

For the hardware requirements, setup and firmware, please check the dedicated page on the official WebSite : [http://www.rflink.nl/blog2/easyha](http://www.rflink.nl/blog2/easyha)

## Supported Things

RFLink binding currently supports following types of devices:

* Temperature (Receive)
* Humidity (Receive) 
* Rain (_to be tested_)
* Wind (_to be tested_)
* Temperature and Humidity (Receive) 

* RTS / Somfy blinds (Send/Receive)

* Energy
* Lighting switch
* MiLight RGB light (Send/Receive)
* Philips Living Color, Ikea Ansluta (_to be tested_)
* X10 Switch (Send/Receive)
* AB400D Elro Switch (Send)
* X10Secure Contact (Receive)
* Other simple RFLink switches (Send/Receive)

# Features

## Discovery

A first version of discovery is supported, currently depending on the type of device a triggered brand/channel/button will appear in the inbox

## Sending messages

Sending of triggers from openhab -> rflink -> device works for some devices :
Lights, Switches, Somfy curtains

## Somfy Curtain position tracking

The RFLink binding provides some features on the Somfy RTS protocol.
It can listen to events transmitted from a physical remote to a curtain (UP/DOWN/STOP).
It can send events to a curtain (UP/DOWN/STOP).

But it can also "guess" the curtain position by simulating its moves.
The behavior and configuration is described in the dedicated [enhancement page](https://github.com/cyrilcc/org.openhab.binding.rflink/issues/48)

# Configuration

## Dependencies

This binding depends on the following plugins to enable communication with the RfLink bridge :

* org.openhab.io.transport.serial

From the openHAB shell, just type 

```
feature:install openhab-transport-serial
```

If you are developing your plugin with Eclipse IDE :
select Run / Run Configurations... then select openHAB_Runtime click on the plug-ins tab, and check the following in the target platform section :
* org.eclipse.smarthome.io.transport.serial
* org.eclipse.smarthome.io.transport.serialrxtx

The error message "Unresolved requirement: Import-Package: gnu.io" is a good indicator to know if you miss this dependency.

## PaperUI dynamic configuration support
Plugin configuration and Object setup (Bridge, Thing, Channel) can be performed from OpenHab 2.0 built in [Paper UI](https://www.openhab.org/docs/configuration/paperui.html) Web Administration service.

## Bridge and Thing configuration
Bridge config:

| Thing Config | Type    | Description  | Required | Example |
|------------|--------------|--------------|--------------|
| serialPort | String | Path to Device | Y | "/dev/tty.wchusbserial1410" |
| baudRate | Integer | baudRate of the Gateway | N : Default=57600 | 57600 |
| keepAlivePeriod | Integer | Send "PING" command to the bridge at the specified period (in second). Only enabled if > 0 | N : Default=0 | 55 |
| disableDiscovery | Boolean | Enable or disable device Discovery | N : Default=false | true |

Thing config:

| Eligible Things | Thing Config | Type    | Description  | Required | Example |
|------------|------------|------------|--------------|--------------|--------------|
| ALL | deviceId | String | Device Id including protocol and switch number | Y | "X10-01001a-2" |
| Switch/RTS | isCommandReversed | Boolean | transmit 'opposite' command to the Thing if enabled | N : Default=false | true |
| Switch/Light | repeats | Integer | number of times to transmit RF messages | N : Default=1 | 3 |
| RTS | shutterDuration | Integer | Time (in seconds) for the RollerShutter to move from full OPEN to full CLOSE. REQUIRED for [RTS position tracking](https://github.com/cyrilcc/org.openhab.binding.rflink/issues/48)  | N : Default=Disabled | 18 |
| RTS | echoPattern | String | Pattern to transform an incoming message into another. Used for [RTS position tracking](https://github.com/cyrilcc/org.openhab.binding.rflink/issues/48) to handle several remotes on a single curtain. Format : KEY1=VALUE1;KEY2=VALUE2... | N : Default=1 | ID=12345;SWITCH=0 |

A manual configuration looks like

_.things file_

```
Bridge rflink:bridge:usb0 [ serialPort="COM19", baudRate=57600 ] {
    energy myEnergy [ deviceId="OregonCM119-0004" ]
}
```

most of the time on a raspberry

```
Bridge rflink:bridge:usb0 [ serialPort="/dev/ttyACM0", baudRate=57600, disableDiscovery=true ] {
    energy myEnergy [ deviceId="OregonCM119-0004" ]
}
```

or

```
Bridge rflink:bridge:usb0 [ serialPort="/dev/ttyUSB0", baudRate=57600 ] {
    temperature myTemperature [ deviceId="OregonTemp-0123" ]
    switch      myContact     [ deviceId="X10Secure-12ab-00" ]
    rts         rts-0F0FF2-0    [ deviceId="RTS-0F0FF2-0" shutterDuration=18 ]
    rts         rts-1a602a-1    [ deviceId="RTS-1a602a-1" echoPattern="ID=0F0FF2;SWITCH=0"]
    switch      x10-01001a-2  [ deviceId="X10-01001a-2" ]
    switch      AB400D-52-2   [ deviceId="AB400D-52-2" ]
    humidity    myHumidity    [ deviceId="AuriolV3-A901" ]
    OregonTempHygro myOregon  [ deviceId="OregonTempHygro-2D60" ]
}
```

All receiving devices must have the protocol as part of the device name (rts, x10 and AB400D).


_.items file_

```
Number myInstantPower "Instant Power [%d]"  <chart> (GroupA) {channel="rflink:energy:usb0:myEnergy:instantPower"}
Number myTotalPower   "Total Power [%d]"    <chart> (GroupA) {channel="rflink:energy:usb0:myEnergy:totalUsage"}
Number oregonTemp     "Oregon Temp [%.2f °C]"                {channel="rflink:temperature:usb0:myTemperature:temperature"}
Number auriolHumidity "Humidity [%d %%]"                     {channel="rflink:humidity:usb0:myHumidity:humidity"}
Rollershutter myBlind "Blind [%s]"                           {channel="rflink:rts:usb0:rts-123abc:command"}
Switch myContact      "Contact [%s]"                         {channel="rflink:switch:usb0:myContact:contact"}
Switch mySwitch       "X10Switch [%s]"                       {channel="rflink:switch:usb0:x10-01001a-2:command"}
Switch myElroSwitch   "AB400DSwitch [%s]"                    {channel="rflink:switch:usb0:AB400D-52-2:command"}
Number temp_outdoor   "Temperature [%.1f °C]"		     {channel="rflink:OregonTempHygro:usb0:myOregon:temperature"}
Number hum_out        "Humidity [%d %%]"		     {channel="rflink:OregonTempHygro:usb0:myOregon:humidity"}
String hstatus_out    "Humidity status [%s]"                 {channel="rflink:OregonTempHygro:usb0:myOregon:humidityStatus" }
Switch low_bat_out    "Low battery [%s]"                     {channel="rflink:OregonTempHygro:usb0:myOregon:lowBattery" }
DateTime obstime_out  "Time of observation [%1$td/%1$tm/%1$tY - %1$tH:%1$tM:%1$tS]"    {channel="rflink:OregonTempHygro:usb0:myOregon:observationTime" }
Color myRGBLight [ deviceId="MiLightv1-C63D-01", repeats=3 ]


```

## Supported Channels

### Energy


| Channel ID | Item Type    | Description  |
|------------|--------------|--------------|
| instantPower | Number | Instant power consumption in Watts. |
| totalUsage | Number | Used energy in Watt hours. |
| instantAmp | Number | Instant current in Amperes. |
| totalAmpHours | Number | Used "energy" in ampere-hours. |


### Wind


| Channel ID | Item Type    | Description  |
|------------|--------------|--------------|
| windSpeed | Number | Wind speed in km per hour. |
| windDirection | Number | Wind direction in degrees. |
| averageWindSpeed | Number | Average wind speed in km per hour. |
| windGust | Number | Wind gust speed in km per hour. |
| windChill | Number | Wind temperature in celcius degrees. |


### Rain


| Channel ID | Item Type    | Description  |
|------------|--------------|--------------|
| rainTotal  | Number       | Total rain in millimeters. |
| rainRate   | Number       | Rain fall rate in millimeters per hour. |


### Temperature


| Channel ID  | Item Type    | Description  |
|-------------|--------------|--------------|
| temperature | Number       | Temperature  |


### Humidity


| Channel ID  | Item Type    | Description  |
|-------------|--------------|--------------|
|   humidity  |   Number     |   Humidity   |


### OregonTempHygro


| Channel ID  | Item Type    | Description  |
|----------------|--------------|--------------|
| temperature    | Number       | Temperature  |
| humidity       | Number       |   Humidity   |
| humidityStatus | String       | Humidity status  |
| lowBattery     | Switch       |   Low battery status   |
| observationTime     | DateTime    |   Last time of observation  (to implement watchdog) |

Humidity status: 

```
Normal (0)
Comfort (1)
Dry (2)
Wet (3)
```

### Switch


| Channel ID  | Item Type    | Description  |
|-------------|--------------|--------------|
| command     | Switch       | Command      |
| contact     | Contact      | Contact state|


### RTS / Somfy


| Channel ID  | Item Type    | Description  |
|-------------|--------------|--------------|
| rts         | Rollershutter| Command / Position |

### Color


| Channel ID  | Item Type    | Description  |
|-------------|--------------|--------------|
| color       | Color        | Command      |



# Contribute
Your RF device may not be handled by the current binding version.
You can contribute and/or ask for it to be implemented in future versions.

RFLink message are very simple ';' separated strings.

## Packet structure
The binding communicates with the RfLink bridge using simple Text messages.
These messages are formated in a special way. 
The full protocol reference is available on the [the RfLink website](http://www.rflink.nl/blog2/protref)


Input message structure :

```
20;FF;Protocol;ID=9999;LABEL1=data1;LABEL2=data2;
```


* 20          => Node number 20 means from the RFLink Gateway to the master, 10 means from the master to the RFLink Gateway
* ;           => field separator
* FF          => packet sequence number
* Protocol    => Protocol
* ID          => ID to use in Things file
* LABEL=data  => contains the field type and data for that field, can be present multiple times per device


Examples :

```
20;6A;UPM/Esic;ID=1002;WINSP=0041;WINDIR=5A;BAT=OK;
20;47;Cresta;ID=8001;WINDIR=0002;WINSP=0060;WINGS=0088;WINCHL=b0;
20;0B;Oregon Temp;ID=0710;TEMP=00a8;BAT=LOW;
20;EB;Oregon TempHygro;ID=2D50;TEMP=0013;HUM=77;HSTATUS=3;BAT=LOW;
```

## Log 

To get sample messages of your Thing, you can enable the DEBUG mode for this binding. 
Add this line to your org.ops4j.pax.logging.cfg (Linux?) file 

 ```
 log4j.logger.org.openhab.binding.rflink = DEBUG
 ```

or add this line to your logback_debug.xml (Windows?) file

 ```
 <logger name="org.openhab.binding.rflink" level="DEBUG" />
 ```

or execute the following command in your Karaf Shell for temporary debug log

 ```
 log:set DEBUG org.openhab.binding.rflink
 ```
 
From OH2.3 the file format has changed and the following two lines must be added:

 ```
 log4j2.logger.org_openhab_binding_rflink.name = org.openhab.binding.rflink
 log4j2.logger.org_openhab_binding_rflink.level = DEBUG
 ```


Or you can use the Serial Monitor of your arduino IDE.

Or you can use the RFLinkLoader application. [See how](http://www.rflink.nl/blog2/development).

For Transmission messages, you can use the **RAW Command** Item on the Bridge Thing to try messages until your physical thing reacts as expected.


## Development Concepts
The RfLink binding is structured around some simple concepts :  
* Most of the process (receiving & transmitting data, data processing, etc.) is fully Generic
* All device specific behavior must be handled in a specific dedicated sub-implem.

On the Home Automation Software side :

| Concept  | Binding class    |
|-------------|--------------|
| OpenHab Bridge | RfLinkBridgeHandler |
| OpenHab Thing | RfLinkHandler + related RfLinkDevice |
| Message (RAW) | RfLinkPacket |
| Message (Decomposed) | RfLinkMessage |

## Development Workflow
1. An input String message comes to the **RfLinkSerialConnector**
2. The received String message is mapped in a **RfLinkPacket** (raw message as String not processed)
3. The **RfLinkPacket**  is grabbed by the **RfLinkBridgeRxListener**
4. the **RfLinkPacket** is then wrapped and decomposed in a **RfLinkMessage** (protocol, AdressId, Switch, attributesMap, etc.)
5. the **RfLinkBridgeRxListener** then finds out which **RfLinkHandler** instance is responsible for this Message (based on the Protocol, Address, SwitchId, etc.)
6. the processing is delegated to the related **RfLinkHandler** 
7. the **RfLinkHandler** creates the **RfLinkDevice** from the **RfLinkMessage**. The Device :
    1. is a dedicated instance (can be **RfLinkRtsDevice** or **RfLinkWindDevice** or ...)
    2. holds the specialized functional behavior
8. the **RfLinkHandler** triggers the required operations on the message :
    1. echo command if needed (based on the Thing configuration)
    2. update the OpenHab thing states according to the **RfLinkDevice** attributes


## Development Steps
1. Add you thing description XML file in the ESH-INF/thing/ directory
2. Implement your message in the org.openhab.binding.rflink.device package, as an implementation of **RfLinkDevice**
3. Override the *RfLinkDevice.eligibleMessageFunction()* to define which kind of messages are handled.
4. Add your new channels names in the RfLinkBindingConstants class
5. Add a ThingTypeUID constant (same class)
6. Add this new constant in the SUPPORTED\_DEVICE\_THING\_TYPES\_UIDS list (same class)
7. To test your thing, don't forget to add you thing in the .things and .items files. See configuration part of this document.
8. Update this README.md document with the new thing and channels you implemented

### How to package your binding

In Eclipse IDE, right click on the pom.xml file, then "Run As", and "Maven Install"  or execute

```
 mvn package
```
