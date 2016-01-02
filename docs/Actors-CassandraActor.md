---
layout: default
title: CassandraActor
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

# CassandraActor
The `CassandraActor` is a [Coral Actor](/actors/overview/) that enables communication with a Cassandra database.  The actor receives queries in the trigger field and emits the results.
All valid [CQL](https://docs.datastax.com/en/cql/3.1/cql/cql_using/about_cql_c.html) queries can be executed, including creating keyspaces and inserting, deleting and updating values.

<div class="alert alert-warning" role="alert">
  <span class="glyphicon glyphicon-exclamation-sign" aria-hidden="true"></span>
  <span class="sr-only">Warning</span>
  <strong>NOTE</strong>:&nbsp;Currently, the CassandraActor cannot be included directly in your runtime. It is meant as a supporting actor to be used by the Coral platform itself. In the future, it might be possible to use the Cassandra Actor to perform queries through a <i>collect</i> procedure. 
</div>


## Creating a CassandraActor

The `params` value is a JSON object with the following fields:

field  | type | required | description
:----- | :---- | :--- | :------------
`seeds` | List[String] | yes| A JSON list of the IP addresses of seed nodes.
`keyspace` | String | yes| The name of the keyspace to connect to initially.
`port` | Int | no | The port to connect to, when omitted default Cassandra port 9042 is used.
`user` | String | no | The user name to connect to Cassandra.
`password` | String | no | The password to connect to Cassandra.

<br>

The CassandraActor can process any valid CQL query. The `keyspace` will be connected to initially, but it can later be changed by sending the CassandraActor a "use keyspace" statement:

{% highlight json %}
{
  "query": "use keyspace otherKeyspace;"
}
{% endhighlight %}

<br>

#### Creation example
{% highlight json %}
{
  "type": "cassandra",
  "params": {
    "seeds": ["192.168.0.2", "192.168.0.3"],
    "keyspace": "coral",
    "port": 9042,
    "user": "admin",
    "password": "admin"
  }
}
{% endhighlight %}

This will create a CassandraActor connecting to seed nodes 192.168.0.2 and 192.168.0.3 on port 9042, to keyspace "coral", using user "admin" and password "admin".

<br>

## Trigger
The `CassandraActor` receives the CQL statement in the query field of the trigger JSON.

<br>

#### Example
{% highlight json %}
{
    "query":"select * from testkeyspace.test1"
}
{% endhighlight %}


<br>

## Emit

The CassandraActor does not emit its result to other actors, but instead should return the answer only to the caller. Thus, the CassandraActor can only be used with a <i>collect</i> procedure.

The answer that the CassandraActor returns is JSON, and looks as follows:

{% highlight json %}
{
  "seeds" : [ "192.168.0.1" ],
  "query" : "select * from keyspace.table",
  "success" : true,
  "data" : [ {
    "field1" : "66d61c3e-a1a0-46e4-b567-a84a8a052a2d",
    "field2" : true,
    "field3" : 42,
    "field4": 26.73
  }]
}
{% endhighlight %}

The CassandraActor automatically converts the result of the query into a JSON object. It converts each Cassandra data type to a JSON type as in the following table:

Cassandra type | JSON type
---: | :--- | :---
`ascii` | JString
`bigint` | JInt
`boolean` | JBool
`int` | JInt
`decimal` | JDecimal
`double` | JDouble
`float` | JDouble
`text` | JString
`varchar` | JString
`varint` | JInt
`timestamp` | JInt
`uuid` | JString

<br>

An example of each of these data types is given below:

{% highlight json %}
{
  "seeds" : [ "192.168.0.1" ],
  "query" : "select * from keyspace.datatypes",
  "success": true,
  "data": [{
    "ascii": "some text",
    "bigint": 156395834234098,
    "boolean": true,
    "int": 42,
    "decimal": 5634.34,
    "double": 5634.34,
    "float": 5634.34,
    "text": "some text",
    "varchar": "some text",
    "varint": 42,
    "timestamp": 1451746530,
    "uuid": "f0223bb6-dfed-489d-8923-54a250aafe21"
  }]
}
{% endhighlight %}

<br>

## State
The `CassandraActor` returns its state as follows:

field |type| description
:--- | :--- | :---
`connected` | boolean | Whether there is still an active connection to the Cassandra database at the time of collecting the state.
`keyspace` | String | The name of the keyspace the actor is currently connected to.
`schema` | Array | A JSON representation of the schema of the keyspace currently connected to.

An example of a schema is as follows:
{% highlight json %}
{
  "connected": true,
  "keyspace": "keyspacename",
  "schema": [{
    "table1": [
     { "column1": "text" },
     { "column2": "int" },
     { "column3": "float" }], 
    "table2": [
     { "col1": "float" },
     { "col2": "int" }]
  }]
}
{% endhighlight %}

<br>

## Collect
The `CassandraActor` does not collect state from other actors.

<br>

## Timer
The `CassandraActor` does not provide timer actions.
