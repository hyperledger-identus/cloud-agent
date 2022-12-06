#!/usr/bin/env bash

set -e

SCRIPT_DIR=$( cd -- "$( dirname -- "${BASH_SOURCE[0]}" )" &> /dev/null && pwd )

Help()
{
   # Display Help
   echo "Run an instance of the ATALA bulding-block stack locally"
   echo
   echo "Syntax: run.sh [-n/--name NAME|-p/--port PORT|-b/--background|-h/--help]"
   echo "options:"
   echo "-n/--name              Name of this instance - defaults to dev."
   echo "-p/--port              Port to run this instance on - defaults to 80."
   echo "-b/--background        Run in docker-compose daemon mode in the background."
   echo "-w/--wait              Wait until all containers are healthy (only in the background)."
   echo "-h/--help              Print this help text."
   echo
}

POSITIONAL_ARGS=()

while [[ $# -gt 0 ]]; do
  case $1 in
    -n|--name)
      NAME="$2"
      shift # past argument
      shift # past value
      ;;
    -p|--port)
      PORT="$2"
      shift # past argument
      shift # past value
      ;;
    -b|--background)
      BACKGROUND="-d"
      shift # past argument
      ;;
    -w|--wait)
      WAIT="--wait"
      shift # past argument
      ;;
    -h|--help)
      Help
      exit
      ;;
    -*|--*)
      echo "Unknown option $1"
      Help
      exit 1
      ;;
    *)
      POSITIONAL_ARGS+=("$1") # save positional arg
      shift # past argument
      ;;
  esac
done

set -- "${POSITIONAL_ARGS[@]}" # restore positional parameters

if [[ -n $1 ]]; then
    echo "Last line of file specified as non-opt/last argument:"
    tail -1 "$1"
fi

NAME="${NAME:=local}"
PORT="${PORT:=80}"

echo "NAME            = ${NAME}"
echo "PORT            = ${PORT}"

echo "--------------------------------------"
echo "Bringing up stack using docker-compose"
echo "--------------------------------------"

PORT=${PORT} docker-compose \
  -p ${NAME} \
  -f ${SCRIPT_DIR}/../shared/docker-compose.yml \
  --env-file ${SCRIPT_DIR}/.env up ${BACKGROUND} ${WAIT}
