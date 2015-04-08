#!/usr/bin/env bash

echo "Getting Cassandra cluster health..."
$CASSANDRA_HOME/bin/nodetool status
