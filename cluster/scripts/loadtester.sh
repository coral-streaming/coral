#!/usr/bin/env bash

# Load tests the system
ab -n 10000 -c 3 -k http://localhost:80/coral/api/in