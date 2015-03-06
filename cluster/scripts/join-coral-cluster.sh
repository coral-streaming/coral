#!/usr/bin/env bash

# A script to connect a VirtualBox Ubuntu 14.x instance to
# the Coral cluster.

if [[ $# -lt 1 ]]; then
    echo "A unique hostname is needed"
    exit 1
fi

echo "Making sure previous instances are not running..."
./killcluster.sh
	
localdir=$(pwd)
ip=$(ifconfig | sed -n '2 p' | awk '{print $2}' | cut -d':' -f2)
hostname=$1
# seed variable is "seed" when this is the seed node,
# else the ip address of the seed node
seed=$2
seedip=

if [[ $seed == "seed" ]]; then
    echo "Using this node ($ip) as the seed node"
    seedip=$ip
    export SPARK_MASTER_IP=$ip
else
    seedip=$seed
    export SPARK_MASTER_IP=$seed
fi

echo "### seed=$seedip,ip=$ip,hostname=$hostname,SPARK_MASTER_IP=$SPARK_MASTER_IP"

sudo hostname $hostname
sudo sed -i 's/^\(.*\)vm.*$/\1'$hostname'/g' /etc/hosts

echo "Joining Coral cluster with IP address $ip and seed $seedip"

# 1) Join Cassandra
# 1a) Adapt Cassandra configuration file
cassandraconffile=$localdir/cassandra.yaml
cassandratemplate=$cassandraconffile.template

# Clear log files
echo "Clearing log files..."
cassandralog=/var/log/cassandra/cassandra.log
sudo rm -rf $cassandralog
sudo touch $cassandralog
sudo chmod -R a+w $cassandralog

systemlog=/var/log/cassandra/system.log
sudo rm -rf $systemlog
sudo touch $systemlog
sudo chmod -R a+w $systemlog

# Clear database
sudo rm -rf $CASSANDRA_HOME/data/data/*
sudo rm -rf $CASSANDRA_HOME/data/commitlog/*
sudo rm -rf $CASSANDRA_HOME/data/saved_caches/*

echo "Setting up Cassandra configuration file $cassandraconffile..."
sed -e "s/<CLUSTER_NAME>/'CoralCluster'/g" \
    -e 's/<SEEDS>/"'$seedip'"/g' \
    -e 's/<LISTEN_ADDRESS>/'$ip'/g' \
    -e 's/<RPC_ADDRESS>/'$ip'/g' $cassandratemplate > $cassandraconffile

echo "Used cassandra configuration:"
echo "=========="
cat $cassandraconffile | grep -v "^#.*$" | grep -v "^$"
echo "=========="

sudo cp $cassandraconffile $CASSANDRA_HOME/conf/cassandra.yaml

# 1b) Start up cassandra
sudo $CASSANDRA_HOME/bin/cassandra &

# 2) Join Spark
if [[ $seed == "seed" ]]; then
    echo "Starting Spark master with $SPARK_HOME/sbin/start-master.sh"
    $SPARK_HOME/sbin/start-master.sh &
else
    echo "Starting Spark worker with master $hostname on port 7077"
    $SPARK_HOME/bin/spark-class \
 	org.apache.spark.deploy.worker.Worker spark://$SPARK_MASTER_IP:7077 &
fi

# 3) Join Akka

akkaconffile=$localdir/akka-cluster.conf
akkatemplate=$akkaconffile.template

echo "Setting up Akka configuration file $akkaconffile..."
sed -e 's/<NETTY_TCP_HOSTNAME>/"'$ip'"/g' \
    -e 's/<NETTY_TCP_PORT>/2551/g' \
    -e 's/<SEED_IP>/'$seedip'/g' \
    -e 's/<SEED_PORT>/2551/g' $akkatemplate > $akkaconffile

echo "Used Akka Cluster configuration:"
echo "=========="
cat $akkaconffile
echo "=========="

echo "Joining Akka Cluster with configuration file $akkaconffile..."

sbt "run-main io.coral.cluster.Boot $akkaconffile" &
