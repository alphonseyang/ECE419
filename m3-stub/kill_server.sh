#! /bin/bash

if [ $1 ]
then
	port=$1
else
	echo "Invalid number of arguments."
    exit 0
fi

lsof -i :"$port" | grep LISTEN | awk '{ print $2 }' | uniq | xargs kill -9 &