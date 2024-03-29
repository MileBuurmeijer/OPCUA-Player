Smiles - Remote Controllable OPC UA Player & Recorder
-------------------------------------------==========

version 0.7.0

- major feature upgrade: it now supports recording of OPC UA server data!
- the configuration file shall hold the nodes of interest and are based on the node-id 
    ( format ns=<some namespace of the node>;s=<some string based identifier> or
      ns=<some namespace of the node>;i=<some integer based identifier> )
- inline with the Player functionality the Recording functionality is configured through the command line:
  - "-mode {player|recorder}"
  - "-duration" of recording (in hh:mm:ss format)
  - "-publishinginterval xxx.y" as subscription settings 
  - "-samplinginterval xxx.y" as monitored item settings 
  - [TODO: monitoring mode (disabled, sampling, reporting)]
- it stores the recorded values in the output file
- it supports capturing browsing results in the form of an configuration file
  - use "-captureinformationmodel" command line option
  - use "-startnode ns=<some namespace of the node>;s=<some string based identifier>" to select where to
    start capturering the informationmodel of the targeted server

version 0.6.1

description:
- plays a data file with timestamped measurements (or whatever data points there are in the file) 
- give it an input data file with chronologically ordered timestamped measurements and 
  it will stream these measurements through OPC UA to subcribed OPC UA clients with 
  true delay time between the samples: e.g. if subsequent timestamps in the data file are 2018-08-06 12:01:670 
  and 2018-08-06 12:01:980 then these are streamed 310 milliseconds after each other
- it also exposes an remote control method in the OPC UA server name space, this allows to start, 
  stop, pause,... this OPC UA player by a connected OPC UA client
- the configuration of assets and their measurement points is read from a configuration file 
  containing assets, sensor-identifiers and sensor names
- the semicolon separated input data file contains rows with asset-identifier, 
  sensor-identifier, timestamp and values (the header row is skipped)
- both data file and configuration file are set by command line parameters
- when player jar is executed it exposes the its namespace but does not start playing 
  yet, it waits for a remote play command, this can be overriden with the -autostart commandline option
- after staring the player with the remote control the data is read and played back at the 
  actual speed of occurrences of the contained timestamps (actual duration between samples)
- the historical timestamps are transposed to the current time based on the time 
  difference between the first timestamp read and the current time (now!)
- it can mimic other OPC UA servers pretty well through the command line options
  -port, -servicename, -uri, and -namespace
- it supports hierarchical asset structures like "part-x.part-y.part-z" in the config file
- simulation of measurement points are supported through the config file, e.g. 
  #simFunc1(SimulatedAsset.simFunc2,SimulatedAsset.simFunc3)[100]:SimulatedAsset.simFunc2*SimulatedAsset.simFunc3
  creates a measurement point named simFunc1 that has a refresh rate of 100 times per second (samples per second), 
  uses two variables that refer to two other measurement points, and its value is multiple of 
  the two variables / values of the two refering measurement points
- this implementation is based on OPC UA server Milo version 0.3.1, Milo is an 
  Open source OPC UA implementation from Eclipse Foundation
    - the SDK is at the right level, so that the player back end code remains 
      relatively free from OPC UA complexities
- some minor features:
    - it adds 1 millisecond to a measurement timestamp that is read from the data file if two subsequently 
      read measurements of the same measurement point have identical timestamps
    - a runstate variable is exposed as UA variable node reflecting Initialized, PlayForward, Pause, ...
    - loop endlessly over the input data file
    - start with zero values for all defined variable nodes and after a loop from 
      the end of data file to the begin when in 'endless loop'-mode (default mode)

