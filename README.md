# OPC-UA Player
A Java based OPC UA Player that supports replaying OPC UA data from a data file.
Its a nice tool to test OPC UA clients, OPC UA data recorders, OPC UA capable IoT platforms, 
distributed control systems (DCS) and the like with streaming OPC UA data from this player. 
An initial version of this OPC UA player was used in a tender procedure to test the capabilities 
of a solution from a potential supplier. This so-called tender award test was successful for this 
player and for the supplier, because they where rewarded with the contract. Currently we will
use it to test OPC UA based integration of train tunnels into the SCADA platform.

# New new new: its now an OPC UA Recorder as well
It now supports recording data from an remote or local OPC UA server. You can set the duration of 
the recording and the publishing and sampling intervals. It also supports the
creation of an configuration file based on nodes it finds (browses) in the exposed information
model of the targeted OPC UA Server. For that you can specify from wich starting point in 
the information model of the targeted OPC UA server you want to have the nodes in the
configuration file. See the readme.txt for more info on this great new feature.
See the product-backlog for example commandlines.

version 0.7.0

# Player usage: 
   
```
mvn exec:java -Dexec.mainClass="name.buurmeijermile.opcuaservices.controllableplayer.main.MainController" -Dexec.args="-configfile 'filename2' -datafile 'filename1'"
```
  - replace filename1 and filename2 with references to your files without the 'quotes' around them.
  - both data files are CSV based and an example configuration file and data set can be found under resources.
  - connect with security settings that are offered, use security policy="none" and message security mode="none" at first

# Player feature description:
- plays a data file with timestamped measurements (or whatever data points there are in the file) 
- give this tool an input data file with chronologically ordered timestamped measurements and 
  it will stream these measurements through OPC UA to subscribed OPC UA clients with 
  true delay time between the samples: e.g. if subsequent timestamps in the data file are 2018-08-06 12:01:670 
  and 2018-08-06 12:01:980 then these are streamed 310 milliseconds after each other in with current timestamps
- it also exposes an remote control method in the OPC UA server name space, this allows to start, 
  stop, pause,... this OPC UA player by a connected OPC UA client. It also has an autostart option (-autosatart)
  to start immediately.
- the configuration of assets (the objects that the measurement belong to and their measurement points 
  is read from a CSV based configuration file containing assets and measurement points, for the latter 
  attributes can be set like unit ofgit measure or access rights
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
  the two variables / values of the two refering measurement points
- in simulations a internal variable t for time can always be used with needing a measurement point with that name 
  and this t is the time fraction of seconds so tailored for time based signal with frequencies above 1Hz.
- this implementation is based on OPC UA server Milo version 0.3.1, Milo is a great 
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
  - "-duration" of recording (in hh:mm:ss format)
  - "-publishinginterval xxx.y" as subscription settings 
  - "-samplinginterval zzz.q" as monitored item settings 
  - TODO: monitoring mode (disabled, sampling, reporting)
- the configuration file shall hold the nodes of interest and are based on the node-id 
    ( format ns=<some namespace of the node>;s=<some string based identifier> or
      ns=<some namespace of the node>;i=<some integer based identifier> )
- it stores the recorded values in the output file
- it supports capturing browsing results in the form of an configuration file
  - use "-captureinformationmodel" command line option
  - use "-startnode ns=<some namespace of the node>;s=<some string based identifier>" to select where to
    start capturering the informationmodel of the targeted server

# Recorder usage:

To capture OPC UA nodes from an servers information model into a configuration file use the following maven command:

```
mvn exec:java -Dexec.mainClass="name.buurmeijermile.opcuaservices.controllableplayer.main.MainController" -Dexec.args="-captureinformationmodel -startnode ns=<xxx>;s=<some node identifier> -configfile <some filename>"
```

To record data from an OPC UA server use the following maven command:

```
mvn exec:java -Dexec.mainClass="name.buurmeijermile.opcuaservices.controllableplayer.main.MainController" -Dexec.args="-duration <some hh:mm:ss time period> -publishinginterval xxx.y -samplinginterval zzz.q -configfile <input configuration filename> -datafile <output data filename>"
```
