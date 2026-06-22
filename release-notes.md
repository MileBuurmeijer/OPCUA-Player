# Release Notes - OPC-UA Player and Recorder

## Version 0.8.0

This release introduces support for structured JSON configuration capture and address space reconstruction, along with improved type handling, robust replay routing, and background metrics reporting.

### Key Features and Improvements

- **JSON Address Space Reconstruction**: 
  - Added support for capturing a complete OPC UA server information model recursively (including variable/folder attributes, datatypes, structures like `Range` and `EUInformation`, and references) into a structured JSON configuration file.
  - Implemented two-pass address space reconstruction in the player server:
    - **Pass 1 (Instantiation)**: Instantiates missing nodes or updates existing nodes in the address space with correct DisplayNames, BrowseNames, and initial values.
    - **Pass 2 (Reference Linking)**: Recursively links nodes with appropriate hierarchical and non-hierarchical references to reconstruct the original server structure.
  - Linked top-level nodes back to Milo's standard `ObjectsFolder` (for namespace index 2) to ensure the reconstructed address space is fully browsable by standard client utilities.
  
- **Dynamic Node & Asset Binding**:
  - Automatically matches and maps dynamically reconstructed JSON variable nodes to backend `MeasurementPoint` structures, ensuring playback values are propagated to the Milo server namespace.
  
- **Remapped CSV Replay & Property Routing**:
  - Remapped historical CSV demo data sets to map tag references using JSON-configured NodeIds.
  - Added smart property routing to dynamically match property tags (e.g. `EURange`, `Definition`) by parent references and BrowseNames during data replay.
  
- **Correct Boolean Type Parsing**:
  - Added robust parsing for Boolean values during data stream playbacks, mapping numeric string values (`"1"`, `"1.0"`, `"0"`, `"0.0"`) to standard OPC UA Boolean states.
  
- **Asynchronous Metrics Logging**:
  - Replaced synchronous logs with a background scheduled progress logger that outputs processed lines and rates at 10-second intervals for both recording and playback streams.
  
- **Programmatic Control Node Isolation**:
  - Isolated player control variables (`Player-Control` folder and methods) to prevent conflicts during JSON configuration capture and reconstruction.
