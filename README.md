# mmpmod: *m*ulti*mo*dal *p*rocess *m*onitoring with *o*n-demand *d*isambiguation

## Overview
This repository contains the prototype implementation of the multimodal process-monitoring architecture presented in the paper *Multimodal Process Monitoring with On-Demand Scene-Based Disambiguation* (authors: Marco Franceschetti, Sejma Sijaric, Raúl Jiménez Cruz, Olaf Zimmermann, César Torres-Huitzil, and Barbara Weber), submitted to CAiSE'26.

The architecture addresses ambiguity that arises during event abstraction in IoT-augmented environments by combining IoT-based process event streams with on-demand disambiguation through computer vision. When ambiguities are detected in the incoming process events, the system triggers visual context acquisition and performs scene understanding to determine the most likely interpretation.

The prototype implements the architecture in the context of a phlebotomy training scenario.

The goal of this repository is to promote replication, experimentation, and extension of the architecture.


## ‍💻Architecture and Vision Model
The architecture consists of four main components:

* Ambiguity Detector
* Process Event Router
* On-demand Ambiguity Resolver
* Process Event Emitter

The architecture is implemented in Java as a Spring Boot application using Spring Modulith. Each component is implemented in a separate package and can be easily replaced. The sub-components of the On-demand Ambiguity Resolver handling camera activation for frame capture and ML inference using the vision model are implemented in Python and invoked by the Java application via a ProcessBuilder object.

The vision model uses a YOLOv11-based segmentation and scene-understanding pipeline, trained on a [phlebotomy dataset](https://zenodo.org/records/16924786) developed within the [ProAmbitIon](https://data.snf.ch/grants/grant/208497) research project, to interpret visual context relevant for disambiguation. The model is found at `ambiguityresolver/ml/YOLO-model/best.pt`.


## Getting started
### Requirements
* Java 21
* Python 3.13 with cv2, pandas, ultralytics, yaml
* A camera connected to the machine running the code
* An MQTT topic to listen to for process events in input
* An MQTT topic to publish the process events in output

### Setup
**1. Clone the repository**
```
git clone https://github.com/ics-unisg/mmpmod.git
```

**2. Configure the application**

* Fill out the missing values in the `application.properties` file for the input and output channels, specifying the URL for the MQTT broker, the client id, the topic where the process events in input are published, the username, and password for the connection to the broker. Note that you have to specify these values for both input and output MQTT brokers.
* Fill out the missing values in the `application.properties` file for the path to the Python executable, specifying the location in your machine.
* [Optional] Change the value for the keys `cameraControl.numberOfFrames` and `cameraControl.waitingTime` to specify how many frames must be captured and with which delay from each other when disambiguation is triggered.

**3. Run the application**