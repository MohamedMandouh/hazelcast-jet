---
title: Building Pipelines
id: pipeline
---

The general shape of any data processing pipeline is `readFromSource ->
transform -> writeToSink` and the natural way to build it is from source
to sink. The `Pipeline` API follows this
pattern. For example,

```java
Pipeline p = Pipeline.create();
p.drawFrom(TestSources.items("the", "quick", "brown", "fox"))
 .map(item -> item.toUpperCase())
 .drainTo(Sinks.logger());
```

In each step, such as `readFrom` or `writeTo`, you create a pipeline
_stage_. The stage resulting from a `writeTo` operation is called a
_sink stage_ and you can't attach more stages to it. All others are
called _compute stages_ and expect you to attach further stages to them.

## Batch vs Stream

The API differentiates between batch (bounded) and stream (unbounded)
sources and this is reflected in the naming: there is a `BatchStage` and
a `StreamStage`, each offering the operations appropriate to its kind.
Depending on source, your pipeline will end up starting with a batch or
streaming stage. It's possible to convert a batch stage by adding
timestamps to it, but not the other way around. Bulk of the operators
are available to both stages and Jet internally treats everything as a
stream.

In this section we'll mostly use batch stages, for simplicity,
but the API of operations common to both kinds is identical. We'll
explain later on how to apply windowing, which is necessary to aggregate
over unbounded streams.

## Multiple Inputs

Your pipeline can consist of multiple sources, each starting its own
pipeline branch, and you are allowed to mix both kinds of stages in the
same pipeline. You can merge the branches with joining transforms such
as hash-join, co-group or merge.

As an example, you can merge two stages into one by using the `merge`
operator:

```java
Pipeline p = Pipeline.create();

BatchSource<String> leftSource = TestSources.items("the", "quick", "brown", "fox");
BatchSource<String> rightSource = TestSources.items("jumps", "over", "the", "lazy", "dog");

BatchStage<String> left = p.readFrom(leftSource);
BatchStage<String> right = p.readFrom(rightSource);

left.merge(right)
    .writeTo(Sinks.logger());
```

## Multiple outputs

Symmetrically, you can fork the output of a stage and send it to more
than one destination:

```java
Pipeline p = Pipeline.create();
BatchStage<String> src = p.drawFrom(TestSources.items("the", "quick", "brown", "fox"));
src.map(String::toUpperCase)
   .drainTo(Sinks.files("uppercase"));
src.map(String::toLowerCase)
   .drainTo(Sinks.files("lowercase"));
```

## Pipeline lifecycle

The pipeline itself is a reusable object which can be passed around and
submitted several times to the cluster. To execute a job, you need the
following steps:

1. Create an empty pipeline definition
1. Start with sources, add transforms and then finally write to a sink.
   A pipeline without any sinks is not valid.
1. Create or obtain a `JetInstance` (using either embedded instance,
   bootstrapped or a client)
1. Using `JetInstance.newJob(Pipeline)` submit it to the cluster
1. Wait for it complete (for batch jobs) using `Job.join()` or just let
   it run on the cluster indefinitely, for streaming jobs.

## Types of Transforms

Besides sources and sinks, Jet offers several transforms which can be used
to process data. We can divide these into roughly the following categories:

1. Stateless transforms: These transforms do not have any notion of
   _state_ meaning that all items must be processed independently of any
   previous items. Examples: `map`, `filter`, `flatMap`.
1. Stateful transforms: These transforms accumulate data and the output
   depends on previously encountered items. Examples: `aggregate`,
   `rollingAggregate`, `distinct`, `window`, `hashJoin`

This distinction is important because any stateful computation requires
the state to be saved for fault-tolerance and this has big implications
in terms of operational design.