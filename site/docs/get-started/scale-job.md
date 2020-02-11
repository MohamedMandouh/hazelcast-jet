---
title: Scale your job
id: scale-job
---

Now that we have managed to submit our first job to the cluster successfuly,
let's make things a little more interesting.

One of the main features of Jet is that it allows you to dynamically
scale a job up or down. Jet keeps processing data without loss even when
a node fails, and you can add more nodes that immediately start sharing
the computation load.

First, let's make sure that the job that you submitted earlier in the previous section 
is still running:

```bash
$ bin/jet list-jobs
ID                  STATUS             SUBMISSION TIME         NAME
03de-e38d-3480-0001 RUNNING            2020-02-09T16:30:26.843 N/A
```

## Start a second Jet node

Now, let's open another terminal window in the Jet installation folder and
start another Jet node, and see what happens:

```bash
bin/jet-start
```

After a little time, you should some output similar to below:

```bash
Members {size:2, ver:2} [
    Member [192.168.0.2]:5701 - 7717160d-98fd-48cf-95c8-1cd2063763ff
    Member [192.168.0.2]:5702 - 5635b256-b6d5-4c88-bf45-200f6bf32104 this
]
```

Congratulations, now you have created a cluster of two nodes! 

>If for some reason your nodes didn't find each other, this is likely
>because multicast is turned off or not working correctly in your
>environment. In this case, please see the
>[Configuration](../operations/configuration) section as this will
>require you to use another cluster discovery mechanism than the
>default, which is multicast.

You will now see that the job is running both nodes automatically,
although you will still only see output on one of the nodes. This is
because the test data source is non-distributed, and we don't have any
steps in the pipeline which require data re-partionining. We will
explain this later in detail in the
[distributed computing](../concepts/distributed-computing) section.

Another thing you'll notice here is that the sequence numbers that are
outputted will have reset, this is because the test source we are using
is not fault-tolerant. This is explained in detail in the [fault
tolerance](concepts/fault-tolerance) section.

## Terminate one of the nodes

We can also the reverse, just terminate one of the nodes by kill the
processes and the job will restart automatically and keep running on the
remaining nodes.

## Next Steps

You've now successfully deployed and scaled your first distributed data
pipeline. The next step is getting a deeper understanding of the
[Pipeline API](../reference) which will allow you to write more complex
data transforms.
