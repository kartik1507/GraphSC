#!/usr/bin/python
import subprocess
import os.path
import time
for i in range(0, 16):
  subprocess.call("fuser " + str(35000 + i) + "/tcp", shell=True)
  subprocess.call("fuser " + str(50000 + i) + "/tcp", shell=True)
  subprocess.call("fuser " + str(55000 + i) + "/tcp", shell=True)
