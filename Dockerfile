# For the Docker image of the video processor container

FROM nvidia/cuda:13.0.1-devel-ubuntu24.04

# The directory where you have saved the GGML model for Whisper.cpp transcription
ENV MODEL_FILENAME=ggml-large-v3-turbo.bin
ENV JAR_FILE_NAME=videobackend-0.0.1-SNAPSHOT.jar

# Change the shell to Bash
SHELL ["/bin/bash", "-c"]

RUN mkdir -p /home/SynapticLearn
RUN mkdir -p /home/isolated
COPY ./cached_model/$MODEL_FILENAME /home/isolated/models/$MODEL_FILENAME
COPY ./build/libs/$JAR_FILE_NAME /home/isolated/$JAR_FILE_NAME

# Get the dependencies ready
RUN ["apt", "update", "-y"]
RUN ["apt", "upgrade", "-y"]
RUN ["apt", "install", "-y", "git", "build-essential", "cmake", "openjdk-21-jdk", "ffmpeg", "libchromaprint-tools"]

# Clone and build Whisper.cpp
WORKDIR /home/isolated
RUN ["git", "clone", "--depth=1", "https://github.com/ggml-org/whisper.cpp.git"]

WORKDIR /home/isolated/whisper.cpp
CMD cmake -B build -DGGML_CUDA=1 && \
cmake --build build -j --config Release && \
./build/bin/whisper-cli -m /home/isolated/models/$MODEL_FILENAME -f /home/isolated/whisper.cpp/samples/jfk.wav && \
java -jar /home/isolated/$JAR_FILE_NAME

