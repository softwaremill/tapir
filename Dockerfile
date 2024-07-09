# Use Ubuntu 22.04 as the base image
FROM ghcr.io/actions/actions-runner

# Avoid prompts from apt
ENV DEBIAN_FRONTEND=noninteractive

RUN sudo apt update -y && sudo apt-upgrade -y
RUN sudo apt install -y --no-install-recommends curl 
RUN sudo /home/runner/bin/installdependencies.sh

# Set the working directory in the container
WORKDIR /__w/tapir/tapir

# Copy everything from the current directory to the working directory in the container
COPY . .

# The image can be used as a base for running tests or further build steps
