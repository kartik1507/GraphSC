eval `ps awux | grep 'java .*parallel' | awk '{print "kill -9 " $2}'`
