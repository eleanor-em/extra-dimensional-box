# dsys-project1
Distributed Systems project 1: team nutella

To facilitate running several peers at once in IntelliJ, there are three folders named `config1`, `config2`, and `config3`. Each of these are the working directory for a different run configuration, so that the `configuration.properties` file can be set differently for each instance of the program. There are also three different `share` folders, one within each of the `config` folders, so that the distributed file system can be tested properly.

The program currently supports all DIRECTORY_* messages, as well as INVALID_PROTOCOL.
