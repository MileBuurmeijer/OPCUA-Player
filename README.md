# OPC-UA Player
A Java based OPC UA Player that supports replaying OPC UA data from a data file.
Its a nice tool to test OPC UA clients, OPC UA data recorders, OPC UA capable IoT platforms, distributed control systems (DCS) and the like with streaming OPC UA data from this player. An initial version of this OPC UA player was used in a tender procedure to test the capabilities of a solution from a potential supplier. This so-called tender award test was successful for this player and for the supplier, because they where rewarded with the contract.

version 0.5.11

usage: 
   
```
mvn exec:java -Dexec.mainClass="name.buurmeijermile.opcuaservices.controllableplayer.server.OPCUAPlayerServer" -Dexec.args="-datafile 'filename1' -configfile 'filename2'"
```
  - replace filename1 and filename2 with references to your files without the 'quotes' around them.
  - both data files are CSV based and an example configuration file and data set can be found under resources.
  - connect with security settings that are offered, use security policy="none" and message security mode="none" at first
  - set athentication settings to username = "user" and password="8h5%32@!~" to be able to subscribe to the available nodes

description:
- plays a data file with timestamped measurements (or whatever data points there are in the file) 
- give this tool an input data file with chronologically ordered timestamped measurements and 
  it will stream these measurements through OPC UA to subcribed OPC UA clients with 
  true delay time between the samples: e.g. if subsequent timestamps in the data file are 2018-08-06 12:01:670 
  and 2018-08-06 12:01:980 then these are streamed 310 milliseconds after each other
- it also exposes an remote control method in the OPC UA server name space, this allows to start, 
  stop, pause,... this OPC UA player by a connected OPC UA client
- the configuration of assets (the objects that the measurement belong to and their measurement points 
  is read from a CSV based configuration file containing assets and measurement points, for the latter 
  attributes can be set like unit of measure or access rights
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
