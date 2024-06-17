#!/bin/bash

# e.g. ./broadcast test hello

curl -X POST -H "x-api-token: $1" -d "$2" "http://localhost:8080/broadcast"
