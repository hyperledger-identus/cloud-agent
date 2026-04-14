# Developing locally

This folder contains scripts and a docker-compose file to run the Atala `building-block` stack for development purposes.

All components are built at the current repository version and published locally.

> If you want to run a local instance without compilation - for end-user use - please see the `infrastructure/local` folder.

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

## Scripts

| Name     | Purpose                                                                                              | Notes                                                                                                                                                                                                                                  |
| -------- | ---------------------------------------------------------------------------------------------------- | -------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| build.sh | Compile and Publish libraries and docker images locally                                              | Requires sbt and Docker installed                                                                                                                                                                                                      |
| run.sh   | Retrieves versions from local components and brings up a docker-compose stack using built components | Does not build images or libraries. Can be used to run multiple instances with command line parameters [see below]                                                                                                                     |
| stop.sh  | Stops a running instance                                                                             | Used to stop a running instance if you've executed `run.sh` with the `-b/--background` option. Please note - you must supply the same `-n/--name` parameter to this script if you have used a non-default value in the `run.sh` script |
| clean.sh | Clean local build cache                                                                              | Runs sbt clean;cleanFiles to clear local cache / state from build directories                                                                                                                                                          |
| full.sh  | Runs `build.sh` followed by `run.sh`                                                                 | Use this to build and run the stack. Sometimes quite slow if not rebuilding all components                                                                                                                                             |

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
