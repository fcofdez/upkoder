FROM jdauphant/sbt

RUN apt-get update && apt-get -y install python-software-properties software-properties-common

RUN add-apt-repository ppa:kirillshkrogalev/ffmpeg-next

RUN apt-get update && apt-get -y --no-install-recommends install ffmpeg

CMD ["/host/run"]
