find ../ -name "*.java" > source.txt;
#find ../../my_FlexSC/ -name "*.java" >> source.txt;
javac -cp .:../lib/*:../lib/FlexSC.jar -d . @source.txt;
rm source.txt;
