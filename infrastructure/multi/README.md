# Running multiple locally

This folder contains scripts to run multiple copies of the Atala `building-block` stack for end-user usage - without building any local components.

This allows you to run an issuer, holder and verifier with a single run command - but - these are locked to fixed ports and cannot be customised at run time.

The issuer will be accessible on port 8080

The holder will be accessible on port 8090

The verifier will be accessible on port 9000

The scripts depend on the implementation in the `../local` folder

All images will be pulled from a remote repository and the `.env` file controls the versions of these images.

> If you want to run the `building-block` stack for development purposes - please see the `infrastructure/dev` folder.
> If you want to run multiple instances of the `building-block` stack and control the names and ports - please see the `infrastructure/local` folder.

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

`./infrastructure/multi/run.sh`

> This script always executes in background mode - you can remove the local state of volumes by using the `stop.sh` script with the `-d` argument.

## Scripts

| Name | Purpose | Notes |
| --- | --- | --- |
| run.sh | Run the multiple instances of the `building-block` stack | Runs using docker-compose and versions are controlled by the `.env` file. |
| stop.sh | Stops a running instance | Used to stop a running instance if you've executed `run.sh` with the `-b/--background` option. |
