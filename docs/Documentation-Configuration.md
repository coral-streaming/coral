---
title: Configuration Reference
layout: default
topic: Documentation
order: 4
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

## Configuration Reference

### Configuration options



### Default configuration file

The contents of the default configuration file present in `application.conf` in the `resources` directory in the Coral runtime JAR is shown below.
If these options are not overriden by manually specifying a configuration file on the command line or overriding options on the command line, these defaults are used.

{% highlight json %}
// This is the default configuration file. Settings can be
// overidden by specifying them on a .conf file that is provided
// on the command line, or with command line parameters. This
// file is automatically included with the JAR and contains
// fallback settings. It should not be modified since it contains
// default values.

akka {
  loggers = [akka.event.slf4j.Slf4jLogger]
  loglevel = info
  log-dead-letters-during-shutdown = false

  persistence {
    journal.plugin = "cassandra-journal"
    snapshot-store.plugin = "cassandra-snapshot-store"
  }

  actor {
    provider = "akka.cluster.ClusterActorRefProvider"
  }

  remote {
    log-remote-lifecycle-events = off
    netty.tcp {
      hostname = ${coral.akka.hostname}
      port = ${coral.akka.port}
    }
  }

  cluster {
    seed-nodes = ${coral.cluster.seed-nodes}

    auto-down-unreachable-after = 10s
    metrics {
      enabled = off
    }
  }
}

spray.servlet.boot-class = "io.coral.api.Boot"

cassandra-journal {
  keyspace = ${coral.cassandra.persistence.journal-keyspace}
  table = "journal"
  contact-points = ${coral.cassandra.contact-points}
  port = ${coral.cassandra.port}
  keyspace-autocreate = ${coral.cassandra.persistence.keyspace-autocreate}

  authentication {
    username = ${coral.cassandra.persistence.user}
    password = ${coral.cassandra.persistence.password}
  }
}

cassandra-snapshot-store {
  keyspace = ${coral.cassandra.persistence.snapshot-store-keyspace}
  table = "snapshots"
  contact-points = ${coral.cassandra.contact-points}
  port = ${coral.cassandra.port}
  keyspace-autocreate = ${coral.cassandra.persistence.keyspace-autocreate}

  authentication {
    username = ${coral.cassandra.persistence.user}
    password = ${coral.cassandra.persistence.password}
  }
}

kafka {
  consumer {
    consumer.timeout.ms = 500
    auto.commit.enable = false
  }

  producer {
    producer.type = async
  }
}

injections.actorPropFactories = [
  "io.coral.actors.DefaultActorPropFactory"
]

coral {
  log-level = INFO

  api {
    interface = "0.0.0.0"
    port = 8000
  }

  akka {
    hostname: "127.0.0.1",
    port: 2555
  }

  distributor {
    mode = "round-robin"
  }

  authentication {
    mode = "coral"
  }

  cassandra {
    persistence {
      journal-keyspace = ${coral.cassandra.keyspace}
      snapshot-store-keyspace = ${coral.cassandra.keyspace}
      journal-table = ${cassandra-journal.table}
      snapshot-table = ${cassandra-snapshot-store.table}
      user = ${coral.cassandra.user}
      password = ${coral.cassandra.password}
      keyspace-autocreate = ${coral.cassandra.keyspace-autocreate}
    }

    contact-points = ["127.0.0.1"]
    port = 9042
    keyspace = "coral"
    user-table = "users"
    runtime-table = "runtimes"
    authorize-table = "auth"
    max-result-size = 50000
    user = "neo"
    password = "thematrix"
    keyspace-autocreate = true
  }

  cluster {
    enable = true
    seed-nodes = ["akka.tcp://coral@"${akka.remote.netty.tcp.hostname}":"${akka.remote.netty.tcp.port}]
  }
}
{% endhighlight %}