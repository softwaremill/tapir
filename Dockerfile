# Use Ubuntu 22.04 as the base image
FROM ghcr.io/actions/actions-runner

# Avoid prompts from apt
ENV DEBIAN_FRONTEND=noninteractive

# Set the working directory in the container
WORKDIR /tapir

# Copy everything from the current directory to the working directory in the container
COPY /home/runner/work/tapir/tapir /tapir

RUN sudo chown -R runner:runner /tapir
RUN sudo apt install -y --no-install-recommends curl unzip 
