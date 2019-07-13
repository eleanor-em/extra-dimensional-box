# Extra-Dimensional Box
This began as a university project (Distributed Systems project 2: team nutella).
I've gone above and beyond the requirements, focusing on stability and massive parallelisation.
Client functionality has been relegated to a separate branch. There is a work-in-progress permission group branch.

### Pull requests welcome and encouraged!

## Credit
The repository contains some code by Benjamin (Jingyi) Li, Andrea Law, Aaron Harwood, and Andrew Wang.


## Building instructions
A simple `mvn package` should do the trick.

The main class is located at `unimelb.bitbox.app.PeerApp`.

Some sample config files are provided. Once the peer is tracking a folder, updates are sent and retrieved from all other connected peers.
There are no command-line arguments.

## Working with the code
To facilitate running several peers at once in IntelliJ, there are folders named `config1`, `config2`, etc. Each of these are the working directory for a different run configuration, so that the `configuration.properties` file can be set differently for each instance of the program. There are also different `share` folders, one within each of the `config` folders, so that the distributed file system can be tested properly.

## Future work
TODOs include making it more useful to a non-developer (i.e. less config file-y), and probably eventually some kind of GUI.
My imagined use case is essentially sharing your own files between several of your computers with your own server, but I'm very open to other thoughts on this.
