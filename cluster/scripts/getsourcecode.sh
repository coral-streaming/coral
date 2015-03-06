#!/usr/bin/env bash


# A helper script to get source code created on a host machine
# into a virtual machine. Run this script inside the VirtualBox
# guest operating system (Ubuntu 14.x). The $hostdir folder
# must exist as a shared folder in VirtualBox.

if [[ $# -ne 2 ]]; then
    echo "Usage: ./getsourcecode.sh <hostdir> <guestdir>"
    exit 1
fi

hostdir=$1
guestdir=$2

if [ ! -d ~/$hostdir ]; then
    sudo mkdir ~/$hostdir
fi

sudo mount -t vboxsf -o uid=$UID,gid=$(id -g) $hostdir ~/$hostdir
cd $guestdir
cp -r ~/$hostdir/* $guestdir