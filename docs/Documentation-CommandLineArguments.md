---
title: Command line parameters
layout: default
topic: Documentation
order: 3
---
<!--
   Licensed to the Apache Software Foundation (ASF) under one or more
   contributor license agreements.  See the NOTICE file distributed with
   this work for additional information regarding copyright ownership.
   The ASF licenses this file to You under the Apache License, Version 2.0
   (the "License"); you may not use this file except in compliance with
   the License.  You may obtain a copy of the License at

       http://www.apache.org/licenses/LICENSE-2.0

   Unless required by applicable law or agreed to in writing, software
   distributed under the License is distributed on an "AS IS" BASIS,
   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
   See the License for the specific language governing permissions and
   limitations under the License.
-->

## Command line arguments

Coral is started through the `coral` script which passes on any arguments
it has and runs the Coral jar file. 

The following options can be specified on the command line: 

command  | explanation
-----: | :------------ | :------------ | :------------
`help` | Prints a help message and quits.
`version` | Prints version information and quits.
`start` | Starts the Coral platform.
`user` | Adds and removes users from the platform.

<br>

The `start` command has the following options. These are not required, one can simply start the platform with `coral start`. When specified, they override options specified in configuration files.

option | abbr. | explanation | example
---: | :--- | :--- | :---
`configfile` | `c` | The .conf configuration file to use. <br>Command line parameters override <br>configuration settings, which override the <br>default application.conf in turn. | `--config "my.conf"`
`apiinterface` | `ai` | The interface that the HTTP API listens to. | `-ai "127.0.0.1"`
`apiport` | `p` | The port that the HTTP API listens to. | `-ap 8000`
`akkahostname` | `ah` | The interface that akka listens on. | `-ah "127.0.0.1"`
`akkaport` | `ap` | The port that akka uses for actor communication. | `-ap 2551`
`authenticationmode` | `am` | The authentication mode to use. Can be "accept-all", "deny-all", "coral" or "ldap". | `-am 'accept-all'`
`contactpoints` | `ccp` | A list of ip addresses of cassandra seed nodes separated by a comma. | `-ccp "192.168.0.1, 192.168.0.2"`
`cassandraport` | `cp` | The cassandra port to connect to. | `-cp 9042`
`keyspace` | `k` | The Cassandra keyspace to connect with. | `-k "coral"`
`nocluster` | `nc` | Run the node without attaching it to a cluster.<br>If specified, the `seednodes` option is ignored.
`seednodes` | `sn` | A list of Akka seed nodes to connect to. These must have the format "akka.tcp://coral@ip:port" where "port" is the IP address of the akka seed node and "port" the port that the seed node listens to. | `-sn "akka.tcp://`<br>`coral@192.168.`<br>`0.1:2551"`

--------------------------

### User command

The `user` command has two sub-commands:

command | explanation
---: | :---
`add` | Adds a user to the platform.
`remove` | Remove a user from the platform.

<br>

#### Add user sub-command

The `add` sub-command has the following options:

option | abbr. | explanation | example
---: | :--- | :--- | :---
`contactpoints` | `ccp` | A list of ip addresses of cassandra seed nodes separated by a comma. | `-ccp "192.168.0.1, 192.168.0.2"`
`cassandraport` | `cp` | The cassandra port to connect to. | `-cp 9042`
`keyspace` | `k` | The Cassandra keyspace to connect with. | `-k "coral"`
`uniquename` | `n` | The unique name of the user. This is the login name of the user. | `-n "ab12cd"`
`fullname` | `f` | The friendly, full name (first and last) of the user. | `-f "John Doe"`
`email` | `e` | The e-mail address of the user. | `-e "john@doe.com"`
`mobile` | `m` | The mobile phone number of the user | `-m "+31612345678"`
`department` | `d` | The department of the user. | `-d "Department of business"`

<br>

#### Remove user sub-command

The `remove` sub-command has the following options:

option | abbr. | explanation | example
---: | :--- | :--- | :---
`contactpoints` | `ccp` | A list of ip addresses of cassandra seed nodes separated by a comma. | `-ccp "192.168.0.1, 192.168.0.2"`
`cassandraport` | `cp` | The cassandra port to connect to. | `-cp 9042`
`keyspace` | `k` | The Cassandra keyspace to connect with. | `-k "coral"`
`uniquename` | `n` | The unique name of the user. This is the login name of the user. | `-n "ab12cd"`