#!/usr/bin/env bash

# A script to kill a node in the cluster.

killcluster() {
	# Kill spark if it is running already
	sparkProcess=$(ps aux | grep "org\.apache\.spark" | awk '{print $2}' | head -n1)
	if [[ $sparkProcess -ne "" ]]; then
		echo "Spark already running. Killing Spark..."
		sudo kill $sparkProcess
	fi

	# Kill Cassandra if it is running already
	cassandraProcess=$(ps aux | grep "org\.apache\.cassandra\.service\.CassandraDaemon" | awk '{print $2}' | head -n1)
	if [[ $cassandraProcess -ne "" ]]; then
		echo "Cassandra already running. Killing Cassandra..."
		sudo kill $cassandraProcess
	fi

	# Kill Akka Cluster if it is running already
	akkaProcess=$(ps aux | grep "io\.coral\.cluster\.Boot" | awk '{print $2}' | head -n1)
	if [[ $akkaProcess -ne "" ]]; then
		echo "Akka already running. Killing Akka..."
		sudo kill $akkaProcess
	fi
}

# Try multiple times to make sure everything is dead
killcluster
killcluster
