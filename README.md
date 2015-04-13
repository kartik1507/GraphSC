GraphSC
======

GraphSC is a parallel secure computation framework that supports graph-parallel programming abstractions resembling GraphLab. GraphSC is suitable for both multi-core and cluster-based computing architectures. Link for the [paper](http://www.cs.umd.edu/~kartik/papers/3_graphsc.pdf).

## Installing GraphSC
git clone https://github.com/kartik1507/GraphSC.git

## Compiling and Running GraphSC - Basic Usage
cd GraphSC/bin/
./compile.sh
./runOne.py <experiment> <inputlength> <garblers>

e.g. ./runOne.py pr.PageRank 16 2

The above example will run the PageRank example using 2 garblers and 2 evaluators on the same machine. The configuration for running garblers and evaluators on a cluster can be found in machine_spec/*. The input files are stored in in/*.

## Developed by
Kartik Nayak (kartik@cs.umd.edu)
Xiao Shaun Wang (wangxiao@cs.umd.edu)
