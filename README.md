![OpenPNP Logo](https://raw.githubusercontent.com/openpnp/openpnp-logo/develop/logo_small.png)

# OpenPnP

Open Source SMT Pick and Place Hardware and Software

## Introduction

OpenPnP is a project to create the plans, prototype and software for a completely Open Source SMT
pick and place machine that anyone can afford. I believe that with the ubiquity of cheap, precise
motion control hardware, some ingenuity and plenty of Open Source software it should be possible
to build and own a fully functional SMT pick and place machine for under $1000.

## Project Status

OpenPnP is stable and in wide use. It is still under heavy development and new features are added continuously. See the [Downloads](http://openpnp.org/downloads) page to get started.

If you would like to keep up with our progress you can
[Watch this project on GitHub](http://github.com/openpnp/openpnp), check out
[our Twitter](http://twitter.com/openpnp), [join the discussion group](http://groups.google.com/group/openpnp),
or come chat with us on [Discord](https://discord.gg/EmsrFVx).

## Contributing

![Build Status](https://github.com/openpnp/openpnp/workflows/Build%20and%20Deploy%20OpenPnP/badge.svg)
[![Help Wanted](https://img.shields.io/github/issues-raw/openpnp/openpnp/help-wanted.svg?label=help-wanted&colorB=5319e7)](https://github.com/openpnp/openpnp/labels/help-wanted)
[![Bugs](https://img.shields.io/github/issues-raw/openpnp/openpnp/bug.svg?label=bugs&colorB=D9472F)](https://github.com/openpnp/openpnp/labels/bug)
[![Feature Requests](https://img.shields.io/github/issues-raw/openpnp/openpnp/feature-request.svg?label=feature-requests&colorB=bfd4f2)](https://github.com/openpnp/openpnp/labels/feature-request)
[![Enhancements](https://img.shields.io/github/issues-raw/openpnp/openpnp/enhancement.svg?label=enhancements&colorB=0052cc)](https://github.com/openpnp/openpnp/labels/enhancement)


Before starting work on a pull request, please read: https://github.com/openpnp/openpnp/wiki/Developers-Guide#contributing

Summary of guidelines:

* One pull request per issue.
* Describe the change.
* Follow the coding style.
* Include tests and documentation.
* Think of the big picture.

# nJobs & Conveyor Support
Welcome to OpenPnP nJobs & Conveyor Support Wiki page.

This page is detailed about the branch named "conveyorSupport". 

The detailed documentation about the OpenPnP software: https://github.com/openpnp/openpnp/wiki

## Files Modified
* JobPanel.java
* JogControlsPanel.java
* MainFrame.java
* GcodeDriver.java
* ReferencePnpJobProcessor.java
* Icons.java

## Objective
To add conveyor/ramp support to the openPnPv2 software and adding the functionality of a batch processes.

## Result/Guide
### Launching the software
OpenPnP requires Java8(not higher) to be installed on the system (available at environment variables). Development and build are processed through JDK 1.8 and Maven.

* `mvn clean` : Cleans the current generated target JARs, located at subdirectory "target".
* `mvn package -DskipTests` : Recompiles and generates new JARs at subdirectory "target" (optional: flag is added to skip tests).
* `java -jar target\openpnp-gui-0.0.1-alpha-SNAPSHOT.jar` : launches the build.

### GUI Changes

The GUI of OpenpPnP is added with a button "n - Jobs Script" in the Job Panel and a button "Ramp Width" in the Jog Controls Panel as shown below:

![image](https://user-images.githubusercontent.com/72141209/184071698-c4be81e0-3ec7-4a2e-9999-cef7c7016a78.png)

![image](https://user-images.githubusercontent.com/72141209/184072970-a37b57f4-8379-438d-97b1-9f3d6eecfe07.png)

Also, there is an extra Tab named "Script Status" as shown below:

![image](https://user-images.githubusercontent.com/72141209/184073134-100d6838-a785-420e-9c64-9b4d4221aea9.png)

The Conveyor is configured on the index(1) GcodeDriver as shown below:

![image](https://user-images.githubusercontent.com/72141209/184073511-2f6573eb-4c1f-4a93-9384-d62c12c23ca7.png)

This GcodeDiver (at index 1) is used in the process threads to communicate externally with the 3 forward motion Conveyor Axes and their Corresponding EndStops. Also, the same GcodeDiver is used to communicate with the 4th Conveyor axis used in adjusting the width of the conveyor.

### Variables
Following variables are hardcoded in the JogControlsPanel.java and can be easily changed in the code. Also, provisionally some of these variables can be accessed from machine.xml file so that for various machines it's easy to change accordingly.

* GcodeDriver index @ Configuration.get().getMachine().getDrivers() : 1
* BoardLocation index @ MainFrame.get().getMachineControls().getJobPanel().getJob().getBoardLocations() : 0
* conveyorhorizontalAxisLetter = "U";
* conveyor1AxisLetter = "X"; 
* conveyor1AxisEndStopString = "x_min";
* conveyor2AxisLetter = "Y";
* conveyor3AxisLetter = "Z";
* conveyor2AxisEndStopString = "y_min";
* actuator = machine.getActuatorByName("Clamping");
* midActuator = machine.getActuatorByName("0Clamping");
* Clamping and 0Clamping actuators are used for clamping process. This can be edited to only one clamping actuator actuated and unactuated, before and after job is performed. Shown Below:

![image](https://user-images.githubusercontent.com/72141209/184109920-62531ce4-c0a2-4c80-a915-3926f5997713.png)

### Extra Requisites and Editable Codes
* Negative Values to be used for forward motion of the 3 conveyor axis. ForEx: G1X-250Y-250
* All Homing Commands for conveyor axes should make each axis approach forward motion to wait for triggered status at corresponding endstop.
* All motions of conveyor axes are supposed to be calibrated according to the values(in mm) send in GCodes. For Ex. G1Y10 should move Y axis exactly by 10mm.
* The EndStop Status for Conveyor 1 Axis is reversed at firmware. "TRIGGERED" normally and "open" for detection.
* Waiting time for each process can be changed at various instances as shown:
![image](https://user-images.githubusercontent.com/72141209/184083171-b100677b-60a9-459d-b3ec-f35e7d748411.png)
* Command String to set PCB at Conveyor 1: "G28" + conveyor1AxisLetter
* Command String to set current position of second conveyor to 0: "G92" + conveyor2AxisLetter + Integer.toString(0)
* Command String to move PCB from Conveyor 1 to 2: "G1" + conveyor1AxisLetter + Integer.toString(-250) + conveyor2AxisLetter + Integer.toString(-250)
  (The Value (-250) should be in accordance to the length of conveyor 1 belt.)
* Command String to move PCB at Conveyor 2 to the position of endstop at Conveyor 2: "G28" + conveyor2AxisLetter
* Command String to move PCB from endstop at Conveyor axis 2 to center of the Clamping Mechanism: "G1" + conveyor2AxisLetter + Integer.toString(-100)
  (The Value (-100) should be in accordance with the length of the PCB and the distance between the clamp and the endstop.) 
* Command String to set current position of third conveyor to 0: "G92" + conveyor3AxisLetter + Integer.toString(0)
* Command String to move PCB from Conveyor 2 to 3: "G1" + conveyor2AxisLetter + Integer.toString(-400) + conveyor3AxisLetter + Integer.toString(-400)
  (The Value (-400) should be in accordance to the length of conveyor 2 belt.)
* Command String to move PCB at Conveyor 3 to the position of endstop at Conveyor 3: "G28" + conveyor3AxisLetter

### Using the Ramp Width and n-Jobs Script button

Ramp Width button will enable the Machine if not already enabled.
A dialog will appear and ask for the width of the PCB and upon entering the same Homing Command to ConveyorWidth Axis will be sent.
After Homing is done, a new dialog will ask for the offset spacing during the homed condition and then width axis will be set according to the width of the PCB.

n-Jobs Script Button

Clicking this button will enable the machine if not already enabled. A new dialog box will appear asking for the number of PCBs to be given for the same Job and same placements assuming same origin as the first PCB. Further, Script Status tab will begin showing the current status of both the threads of a multi threaded process aiding the conveyor movement and along with Job process.


## Methodology

### Working

Loop on ConveyorThread

![image](https://user-images.githubusercontent.com/72141209/184091223-c020960c-4c3b-4bb5-a852-f207efbb4d00.png)

Loop on JobThread

![image](https://user-images.githubusercontent.com/72141209/184110904-b0b20453-4031-49d4-ab51-22dadb2fe045.png)











## Thanks

Many thanks to ej-technologies for providing a complimentary license of install4j. install4j
creates high quality, professional installers for Java applications.

More information at http://www.ej-technologies.com/products/install4j/overview.html.
