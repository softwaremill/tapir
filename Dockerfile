# Use Ubuntu 22.04 as the base image
FROM node:20

# Avoid prompts from apt
ENV DEBIAN_FRONTEND=noninteractive

# Set the working directory in the container
WORKDIR /__w/tapir/tapir

# Copy everything from the current directory to the working directory in the container
COPY . .

# The image can be used as a base for running tests or further build steps
