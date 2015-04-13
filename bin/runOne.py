#!/usr/bin/python
import subprocess
import os.path
import time
import sys
inputLength = sys.argv[2]
garblers = sys.argv[3]
experiment = sys.argv[1]
subprocess.call("./clear_ports.sh", shell=True)
params = str(garblers) + " " + str(inputLength) + " " + experiment + " 00 REAL false"
subprocess.call(["./run_garblers.sh " + params], shell=True)
subprocess.call(["./run_evaluators.sh " + params], shell=True)
