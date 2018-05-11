#! /bin/bash

filename="id_rsa"
path="$HOME/.ssh"
username="$USER"
thishost=$(hostname)

if [ $1 ]
then
	servername=$1
    if [ $2 ]
    then
    	remotehost=$2
        if [ $3 ]
    	then
    		port=$3
    	else
    		echo "Invalid number of arguments."
    	fi
    else
    	echo "Invalid number of arguments."
    	exit 0
    fi
else
    echo "Invalid number of arguments."
    exit 0
fi

SOURCE="${BASH_SOURCE[0]}"
while [ -h "$SOURCE" ]; do # resolve $SOURCE until the file is no longer a symlink
  DIR="$( cd -P "$( dirname "$SOURCE" )" && pwd )"
  SOURCE="$(readlink "$SOURCE")"
  [[ $SOURCE != /* ]] && SOURCE="$DIR/$SOURCE" # if $SOURCE was a relative symlink, we need to resolve it relative to the path where the symlink file was located
done
DIR="$( cd -P "$( dirname "$SOURCE" )" && pwd )"

echo "Set up complete, try to ssh to $remotehost now"
ssh -n "$remotehost" nohup java -jar "$DIR"/m2-server.jar "$servername" "$thishost" "$port" &
exit 0

