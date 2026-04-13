# Setting up PGP

Every commit must be signed with a PGP key associated with your company email address, that is `firstname.lastname@iohk.io`. For IOHK employees it **must** be the same key you use to encrypt IOHK emails, it can not be your personal PGP key or any other one.

## Install gnupg

### Linux

```bash
sudo apt-get install gnupg
```

### Mac

```bash
brew install gnupg
```

### Windows

Download and install gnupg for windows from [GnuPG website](https://gnupg.org/download/index.html)

## Generating a key

In case you've already generated your company PGP key pair before, you need to import a private key

```bash
gpg --import private.key
```

If you have not generated your company PGP key pair yet

```bash
gpg --full-generate-key
```

 and follow the instructions, make sure to associate this key with your company email address. For key size, choose 4096

## Using the key

### Local setup

Set up git to automatically sign every commit with your company PGP key, first get your GPG key id

```bash
gpg --list-keys
```

will list all the keys you have available. copy the id of the key associated with your company email address.

configure git to use this key to sign every commit automatically

```bash
git config user.signingkey <your key id here> && 
git config commit.gpgsign true
# in case you prefer to use another tool for pgp, like gpg2, you need to specify it here, otherwise ignore it.
git config gpg.program gpg2
```

Check the [Using the key](#using-the-key) section for simple ways to set this up and forget about it.

### Remote setup

You need to add the public key to Github, so that it can verify commits signed by the associated private key.

export your public key

```bash
gpg --armor --export firstname.lastname@iohk.io
```

This will output the key into your terminal. Copy the whole key (including -----BEGIN PGP PUBLIC KEY BLOCK----- and -----END PGP PUBLIC KEY BLOCK----- part) and add it [into your account](https://github.com/settings/keys)

*NOTE:* Make sure to add your company email address into [your github account emails](https://github.com/settings/emails) and confirm it. Github will allow you to add public keys associated with any email, but if this email is not added into your emails, it assumes that you are not the owner of this email address, and even if commits are signed with a proper private key, they will not be verified.

### Troubleshooting

in case commiting a change fails with the message

```bash
error: gpg failed to sign the data
fatal: failed to write commit object
```

try the following

```bash
gpgconf --kill gpg-agent
export GPG_TTY=$(tty)
echo "test" | gpg --clearsign
```

if this problem keeps happening, try adding `export GPG_TTY=$(tty)` into your `~/.bashrc` or `~/.zshrc` file.
