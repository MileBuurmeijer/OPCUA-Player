# OPC-UA Player
![Smiles OPC-UA Player Control Center Banner](files/Smiles%20OPC%20UA%20Player%20Web%20UI%20banner.png)

A Java based OPC UA Player that supports replaying OPC UA data from a data file.
Its a nice tool to test OPC UA clients, OPC UA data recorders, OPC UA capable IoT platforms, 
distributed control systems (DCS) and the like with streaming OPC UA data from this player. 
An initial version of this OPC UA player was used in a tender procedure to test the capabilities 
of a solution from a potential supplier. This so-called tender award test was successful for this 
player and for the supplier, because they where rewarded with the contract. Currently we will
use it to test OPC UA based integration of train tunnels into the SCADA platform.

# OPC UA Recorder
It is also an OPC UA Recorder. It supports recording data from a remote or local OPC UA server. You can set the duration of 
the recording and the publishing and sampling intervals. It also supports the
creation of a configuration file based on nodes it finds (browses) in the exposed information
model of the targeted OPC UA Server. For that, you can specify from which starting point in 
the information model of the targeted OPC UA server you want to have the nodes in the
configuration file. See the readme.txt for more info on this great new feature.
See the product-backlog for commandline examples.

# New: OPC-UA Web Control Center
![Smiles OPC-UA Player Web Control Center](files/Smiles%20OPC%20UA%20Player%20Web%20UI.png)

A modern, web-based control center dashboard is now included in the application to manage and monitor multiple Player and Recorder instances concurrently. 

Features include:
- **Dynamic Instance Creation:** Spin up player or recorder servers dynamically through simple web forms.
- **Process Lifecycle Control:** Start, stop, and remove instances cleanly in the dashboard header.
- **Playback & Recording Operations:** Send Play, Pause/Resume, and Stop commands to player instances, or Record and Pause/Resume commands to recorders.
- **OPC-UA Namespace Browser:** Interactive address space tree view to explore the target OPC UA server nodes live on the left column.
- **Console Log Streamer:** Real-time log console rendering stderr, stdout, warnings, errors, and system events on the right column.

version 1.0.1

# Build the code
Linux build steps (e.g raspberry pi):

1) Install prerequisites
```
sudo apt install git mvn
```
2) Clone git repository on local device
```
git clone https://github.com/MileBuurmeijer/OPCUA-Player.git
```
3) Go to folder with project files
```
cd OPCUA-Player
```
4) Build the package
```
mvn package
```
Done building the executable jar file.


# Player usage: 
   
For CSV-based configuration files:
```
mvn exec:java -Dexec.mainClass="name.buurmeijermile.opcuaservices.controllableplayer.main.MainController" -Dexec.args="-configfile 'filename2' -datafile 'filename1'"
```
For JSON-based information model configuration files:
```
mvn exec:java -Dexec.mainClass="name.buurmeijermile.opcuaservices.controllableplayer.main.MainController" -Dexec.args="-mode player -configfile 'json-config-filename' -datafile 'data-filename' -autostart -port 12800"
```
  - both data files are CSV based and an example configuration file and data set can be found under resources.
  - connect with security settings that are offered, use security policy="none" and message security mode="none" at first

# Web UI Usage:
To launch the Web UI control center dashboard (which defaults to port `12000`):
```
mvn exec:java -Dexec.mainClass="name.buurmeijermile.opcuaservices.controllableplayer.main.MainController" -Dexec.args="-mode webui"
```
Or to run the Web UI dashboard on a custom port:
```
mvn exec:java -Dexec.mainClass="name.buurmeijermile.opcuaservices.controllableplayer.main.MainController" -Dexec.args="-mode webui -port 8080"
```
Once the Web UI server starts up, open your web browser and navigate to `http://localhost:12000` (or your custom port) to access the control center dashboard.

# Player feature description:

Try the OPC UA player at first with the supplied example configuration and data file:
```
mvn exec:java -Dexec.mainClass="name.buurmeijermile.opcuaservices.controllableplayer.server.OPCUAPlayerServer" -Dexec.args="-configfile src/main/resources/AssetConfiguration-datatypes.csv -datafile src/main/resources/PlayerDemoData.csv"
```
Connect to this OPC UA Player server for example with the free UA Expert client tool: https://www.unified-automation.com/products/development-tools/uaexpert.html 

Features:

- plays a data file with timestamped measurements (or whatever data points there are in the file) 
- give this tool an input data file with chronologically ordered timestamped measurements and 
  it will stream these measurements through OPC UA to subscribed OPC UA clients with 
  true delay time between the samples: e.g. if subsequent timestamps in the data file are 2018-08-06 12:01:670 
  and 2018-08-06 12:01:980 then these are streamed 310 milliseconds after each other in with current timestamps
- it also exposes an remote control method in the OPC UA server name space, this allows to start, 
  stop, pause,... this OPC UA player by a connected OPC UA client. It also has an autostart option (-autosatart)
  to start immediately.
- the configuration of assets (the objects that the measurements belong to and their measurement points) can be read from either a CSV-based configuration file or a structured JSON information model configuration file (which contains reconstructed variable attributes, complex properties, and hierarchical references) to be used when playing back the data. For the CSV configuration, attributes like unit of measure or access rights can be set.
- the semicolon separated input data file contains per row an asset identifier, 
  measurement point identifier, timestamp and a value (the header row of the file is skipped)
- both data file and configuration file are to be declared through command line parameters
- when this OPC UA player server is executed it exposes the its OPC UA namespace to connecting OPC UA clients,
  but does not start playing the contents of the data file yet: it waits for a remote play command, this can be overriden
  with the -autostart commandline option
- after staring the player with the remote control the data is read and played back at the 
  actual speed of occurrences of the contained timestamps in the data file (actual duration between samples)
- the historical timestamps are transposed to the current time based on the time 
  difference between the first timestamp read and the current time (now!)
- it can mimic other OPC UA servers pretty well through the command line options:
  -port, -servicename, -uri, and -namespace
- simulation of measurement points are supported through the config file, e.g. 
  #simFunc1(SimulatedAsset.simFunc2,SimulatedAsset.simFunc3)[100]:SimulatedAsset.simFunc2*SimulatedAsset.simFunc3
  creates a measurement point named simFunc1 that has a refresh rate of 100 times per second (samples per second), 
  uses two variables that refer to two other measurement points, and its value is multiple of 
  the two variables / values of the two referring measurement points. An example of a configuration
  file, called AssetConfiguration-simulation.csv can be found under resources
- in simulations a internal variable t for time can always be used with needing a measurement point with that name 
  and this t is the time fraction of seconds so tailored for time based signal with frequencies above 1Hz.
- this implementation is based on OPC UA server Milo version 0.5.4, Milo is a great 
  open source OPC UA implementation from the Eclipse Foundation and lead developer Kevin Herron
    - the SDK is at the right level, so that the player back end code remains 
      relatively free from OPC UA complexities
- some minor features:
    - adding 1 millisecond to a measurement read from the data file if two subsequently 
      read measurements from the same measurement point have identical timestamps
    - the runstate variable is exposed as UA variable node so that is clear if the player is initialized, running or paused.
    - loops endlessly over the input data file
    - start with zero values for all defined variable nodes and resets to zero after a loop from 
      the end of data file to the begin when in 'endless loop'-mode

# Recorder feature description:

- inline with the Player functionality the Recording functionality is configured through the command line:
  - "-mode {player|recorder}"
  - "-duration xx:yy:zz" as duration of recording (in hh:mm:ss format)
  - "-publishinginterval xxx.y" as subscription settings 
  - "-samplinginterval zzz.q" as monitored item settings 
  - TODO: monitoring mode (disabled, sampling, reporting)
- the configuration file shall hold the nodes of interest and are based on the node-id 
    ( format ns=<some namespace of the node>;s=<some string based identifier> or
      ns=<some namespace of the node>;i=<some integer based identifier> )
- it stores the recorded values in the output file
- it supports capturing browsing results in the form of a structured JSON configuration file
  - use "-captureinformationmodel" command line option
  - use "-startnode ns=<some namespace of the node>;s=<some string based identifier>" to select where to
    start capturing the informationmodel of the targeted server

# Recorder usage:

To capture OPC UA nodes from a server's information model recursively into a structured JSON configuration file:
```
mvn exec:java -Dexec.mainClass="name.buurmeijermile.opcuaservices.controllableplayer.main.MainController" -Dexec.args="-mode recorder -captureinformationmodel -startnode 'start-node-id' -uri 'server-uri' -configfile 'output-json-config-filename'"
```

To record data from an OPC UA server (using a configuration file to specify subscription tags):
```
mvn exec:java -Dexec.mainClass="name.buurmeijermile.opcuaservices.controllableplayer.main.MainController" -Dexec.args="-mode recorder -duration 'duration' -publishinginterval 'publishing-interval' -samplinginterval 'sampling-interval' -uri 'server-uri' -configfile 'tags-config-filename' -datafile 'output-data-filename'"
```
