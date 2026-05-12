#!/usr/bin/env bash

set -e

SCRIPT_DIR=$( cd -- "$( dirname -- "${BASH_SOURCE[0]}" )" &> /dev/null && pwd )

# Set working directory
cd ${SCRIPT_DIR}

Help()
{
   # Display Help
   echo "Run an instance of the ATALA bulding-block stack locally"
   echo
   echo "Syntax: run.sh [-d/--destroy-volumes|-h/--help]"
   echo "options:"
   echo "-d/--destroy-volumes   Instruct docker-compose to tear down volumes."
   echo "-h/--help              Print this help text."
   echo
}

POSITIONAL_ARGS=()

while [[ $# -gt 0 ]]; do
  case $1 in
    -d|--destroy-volumes)
      # Note: In this script we set this to -d to pass to other scripts
      # In local/dev - this is set to -v as it's passed directly to docker and
      # the flag is different
      VOLUMES="-d"
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

if [ -z ${VOLUMES+x} ];
then
    VOLUMES=""
fi

# set a default port as required to ensure docker-compose is valid if not set in env
export PORT="80"

echo "NAME            = ${NAME}"

echo "--------------------------------------"
echo "Stopping stack using docker compose"
echo "--------------------------------------"

../local/stop.sh -n issuer ${VOLUMES}
../local/stop.sh -n holder ${VOLUMES}
../local/stop.sh -n verifier ${VOLUMES}
