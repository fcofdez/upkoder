Upcoder
=======

Upcoder is an Akka based application to encode, generate gifs and thumbnails
based on webhooks.

Architecture is based on a replicated set of masters (that nowadays is not
deployed in that way), and a set of workers that actually perform the encoding
itself.

There is a protocol defined on `MasterWorkerProtocol.scala` file that is clear
enough.

To deploy the application.

Master:
```
git clone this_repository.git
./deploy.sh # It Builds the container
vim src/main/resources/application.conf and edit seed-nodes to point to master ip
./master.sh
```

Workers:
```
git clone this_repository.git ./deploy.sh # It Builds the container
./deploy.sh # It Builds the container
vim src/main/resources/worker.conf and edit contact-points to point to master ip
./worker.sh
```

Configuration
--------------

On `src/main/resources` there are files to configure every aspect that is
configurable, from S3 buckets to API credentials.

The environment of the application as well as Amazon credentials are configured
using environment variables. On `worker.sh` and `master.sh` there are the
values.


Endpoints
---------

`GET master.upcoder.upclose.me:9000/jobs` shows some statistics

```
HTTP/1.1 200 OK
Content-Length: 66
Content-Type: application/json; charset=UTF-8
Date: Tue, 07 Jul 2015 14:16:22 GMT
Server: spray-can/1.3.3

{
    "doneWork": 1105, 
    "inProgressWork": 14, 
    "pendingWork": 0
}
```

`POST master.upcoder.upclose.me:9000/jobs` expecting tokbox json
content to enqueue a job.


Some jobs are rejected due to tokbox don't encode correctly the videos.


Logs
====

To look into logs just refer to
`tail -f log/app.log`

Logs are also configurable in file
`src/main/resources/logback.xml`

System High level diagram
=========================

![Diagram](diagram.png)


Known Problems
==============

There is a bug on deleting files after processing them that has something to do
with docker FileSystem driver, I didn't have time to research it. A workaround
is just stop and delete containers and start again. It's a strange bug.

SharedJournal ip is hardcoded on `Main.scala:62` that ip is basically the master ip.

Take into account that in AWS EC2 machines you cannot bind tcp sockets to public ip
(DNS value for example) and you have to bind private aws ip.


