# Running locally

This folder contains scripts to run the Atala `building-block` stack for end-user usage - without building any local components.

All images will be pulled from a remote repository and the `.env` file controls the versions of these images.

> If you want to run the `building-block` stack for development purposes - please see the `infrastructure/dev`  folder.

**Running using the scripts in this directory does not create a production-ready or secure environment. It is designed to allow easy development and should not be used to run a production instance**

Please ensure you have set the `GITHUB_TOKEN` environment variable.

The value of this variable must be a Github token generated with the  `read:packages` permission set on the `building-block` repository.

> Replace `YOUR_TOKEN_HERE` with your Github token

```
export GITHUB_TOKEN=YOUR_TOKEN_HERE
```

Please ensure you have logged into the ATALA IOHK Docker Registry using the following command [once the `GITHUB_TOKEN` is set]

> Replace `YOUR_USERNAME_HERE` with your Github username

```
echo $GITHUB_TOKEN | docker login ghcr.io -u YOUR_USERNAME_HERE} --password-stdin
```

## Example usage

To run the Atala `building-block` stack - execute the `run.sh` script. This can be run from within the directory or at the root of the source code project:

`./infrastructure/local/run.sh`

## Scripts

| Name | Purpose | Notes |
| --- | --- | --- |
| run.sh | Run the Atala `building-block` stack | Runs using docker-compose and versions are controlled by the `.env` file. Can be used to run multiple instances with command line parameters [see below] |
| stop.sh | Stops a running instance | Used to stop a running instance if you've executed `run.sh` with the `-b/--background` option. Please note - you must supply the same `-n/--name` parameter to this script if you have used a non-default value in the `run.sh` script |

## run.sh

The `run.sh` script allows you to run multiple `building-block` stacks locally. Please run `run.sh --help` for command line options.

Example - Run an instance named `inviter` on port 8080 and an instance named `invitee` on port 8090

> These examples show running in `background mode` using the `-b` flag. This means that docker-compose is passed the `daemon -d` flag.
> If you wish to run them in the foreground to view logs - please make sure each line is executed in a different terminal and the `-b` flag is removed.
> After running in either mode (foreground or background) - you can remove the local state of volumes by using the `stop.sh` script with the `-d` argument.

Starting the instances:

```
./run.sh -n inviter -p 8080 -b
./run.sh -n invitee -p 8090 -b
```

Stopping the instances:

```
./stop.sh -n inviter 
./stop.sh -n invitee
```

OR

Specify the `-d` argument when stopping the instances to remove state:

```
./stop.sh -n inviter -d
./stop.sh -n invitee -d
```
