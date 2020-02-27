---
title: Class and Resource Deployment
id: classloading
---

Intro: explain why it's necessary to add classes to a job when sending it
to a cluster, and how it works.

## Submit as a JAR

How to build an uber JAR and submit it as a job.

## Adding to Classpath

How to add things directly to class path.

Describe what must be on classpath (i.e. serializers, map loader etc)

## Attaching Classes

How to attach classes manually and send them using Jet client.

## Attaching additional files

Describe to how to attach files, and how to access them inside a job.

## User Code Deployment

Describe how this feature can be used to deploy additional classes (but maybe
it's best to avoid for now as it's WIP)